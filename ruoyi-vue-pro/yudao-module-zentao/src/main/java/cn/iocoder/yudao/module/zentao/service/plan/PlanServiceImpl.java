package cn.iocoder.yudao.module.zentao.service.plan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.plan.vo.PlanPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.plan.vo.PlanSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.branch.BranchDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.plan.PlanDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.bug.BugMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.plan.PlanMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.story.StoryMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.plan.PlanClosedReasonEnum;
import cn.iocoder.yudao.module.zentao.enums.plan.PlanStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.product.ProductTypeEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import cn.iocoder.yudao.module.zentao.service.branch.BranchService;
import cn.iocoder.yudao.module.zentao.service.product.ProductService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 产品计划 Service 实现
 *
 * <p>逐条对齐禅道 {@code module/productplan/model.php}：create / update / updateStatus /
 * updateParentStatus / delete / linkStory / unlinkStory / linkBug / unlinkBug /
 * transferStoriesAndBugs / unlinkOldBranch。有意偏离的地方都在方法注释里标了「偏离」。
 */
@Slf4j
@Service
public class PlanServiceImpl implements PlanService {

    private static final String OBJECT_TYPE_PLAN = "productplan";
    private static final String OBJECT_TYPE_STORY = "story";
    private static final String OBJECT_TYPE_BUG = "bug";

    @Resource
    private PlanMapper planMapper;

    @Resource
    private StoryMapper storyMapper;

    @Resource
    private BugMapper bugMapper;

    @Resource
    private ProductService productService;

    @Resource
    private BranchService branchService;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ==================== 写：创建 / 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPlan(PlanSaveReqVO createReqVO) {
        ProductDO product = productService.validateProductExists(createReqVO.getProduct());
        String branch = buildBranch(product, createReqVO.getBranches());
        LocalDate[] range = resolveDateRange(createReqVO);

        PlanDO parent = null;
        if (createReqVO.getParent() != null && createReqVO.getParent() > 0) {
            parent = validatePlanExists(createReqVO.getParent());
            if (!parent.getProduct().equals(createReqVO.getProduct())) {
                throw exception(PLAN_PARENT_INVALID, "父计划不属于同一个产品");
            }
            checkChildWithinParent(range, parent);
        }

        PlanDO plan = new PlanDO();
        plan.setProduct(createReqVO.getProduct());
        plan.setBranch(branch);
        plan.setParent(parent == null ? PlanDO.PARENT_NONE : parent.getId());
        plan.setTitle(createReqVO.getTitle());
        plan.setDesc(createReqVO.getDesc());
        plan.setBegin(range[0]);
        plan.setEnd(range[1]);
        plan.setStatus(PlanStatusEnum.WAIT.getStatus());
        plan.setCreatedBy(currentAccount());
        plan.setCreatedDate(LocalDateTime.now());
        planMapper.insert(plan);

        if (parent != null) {
            // 建了子计划：给父计划打上「有子计划」标记，并把父计划上属于该分支的需求/Bug 转到子计划。
            // 禅道的条件是多一层判断：只有当父计划本身是「顶层计划」（parent == 0）时才转移数据，
            // 已经有父计划的中间层计划不参与转移。
            planMapper.updateParent(parent.getId(), PlanDO.PARENT_HAS_CHILDREN);
            if (PlanDO.PARENT_NONE.equals(parent.getParent())) {
                transferStoriesAndBugs(parent, plan);
            }
        }

        actionService.recordAction(OBJECT_TYPE_PLAN, plan.getId(), ActionTypeEnum.CREATED,
                "创建计划：" + plan.getTitle());
        return plan.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlan(PlanSaveReqVO updateReqVO) {
        PlanDO oldPlan = validatePlanExists(updateReqVO.getId());
        if (PlanStatusEnum.CLOSED.getStatus().equals(oldPlan.getStatus())) {
            // 偏离：禅道没有这条限制，但关闭的计划再改分支/日期会让关联数据失去依据
            throw exception(PLAN_STATUS_ILLEGAL, PlanStatusEnum.CLOSED.getName());
        }
        ProductDO product = productService.validateProductExists(oldPlan.getProduct());
        String branch = buildBranch(product, updateReqVO.getBranches());
        LocalDate[] range = resolveDateRange(updateReqVO);

        // 父计划日期范围要罩住子计划（对应禅道 update() 里对 parent == -1 的处理）
        checkParentCoversChildren(oldPlan, range);
        // 子计划不能超出父计划范围
        if (oldPlan.getParent() != null && oldPlan.getParent() > 0) {
            checkChildWithinParent(range, validatePlanExists(oldPlan.getParent()));
        }

        PlanDO updateObj = new PlanDO();
        updateObj.setId(oldPlan.getId());
        updateObj.setTitle(updateReqVO.getTitle());
        updateObj.setDesc(updateReqVO.getDesc());
        updateObj.setBegin(range[0]);
        updateObj.setEnd(range[1]);
        updateObj.setBranch(branch);
        planMapper.updateById(updateObj);

        // 分支范围缩小后，超出范围的需求/Bug 要自动解除关联（禅道 unlinkOldBranch）
        if (!branch.equals(oldPlan.getBranch())) {
            unlinkOutOfBranch(oldPlan, branch);
        }

        PlanDO newPlan = planMapper.selectById(oldPlan.getId());
        actionService.recordActionWithChanges(OBJECT_TYPE_PLAN, oldPlan.getId(),
                ActionTypeEnum.EDITED, null, oldPlan, newPlan);
    }

    // ==================== 写：状态流转 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startPlan(Long id) {
        PlanDO plan = validatePlanExists(id);
        if (!PlanStatusEnum.WAIT.getStatus().equals(plan.getStatus())) {
            throw exception(PLAN_STATUS_ILLEGAL, PlanStatusEnum.nameOf(plan.getStatus()));
        }
        // 开始：清掉完成/关闭信息（对齐 buildPlanByStatus）
        planMapper.updateStatusFields(id, PlanStatusEnum.DOING.getStatus(), null, null, "");
        afterStatusChanged(plan, "开始计划");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishPlan(Long id) {
        PlanDO plan = validatePlanExists(id);
        if (PlanStatusEnum.DONE.getStatus().equals(plan.getStatus())
                || PlanStatusEnum.CLOSED.getStatus().equals(plan.getStatus())) {
            throw exception(PLAN_STATUS_ILLEGAL, PlanStatusEnum.nameOf(plan.getStatus()));
        }
        planMapper.updateStatusFields(id, PlanStatusEnum.DONE.getStatus(), LocalDateTime.now(), null, "");
        afterStatusChanged(plan, "完成计划");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closePlan(Long id, String reason) {
        PlanDO plan = validatePlanExists(id);
        if (PlanStatusEnum.CLOSED.getStatus().equals(plan.getStatus())) {
            throw exception(PLAN_ALREADY_CLOSED);
        }
        String closedReason = StringUtils.hasText(reason) ? reason : PlanClosedReasonEnum.DONE.getReason();
        if (!PlanClosedReasonEnum.isValid(closedReason)) {
            throw exception(PLAN_STATUS_ILLEGAL, "关闭原因：" + closedReason);
        }
        // 关闭原因选「已完成」时顺带写 finishedDate（禅道 buildPlanByStatus 的行为）
        LocalDateTime finishedDate = PlanClosedReasonEnum.DONE.getReason().equals(closedReason)
                ? LocalDateTime.now() : null;
        planMapper.updateStatusFields(id, PlanStatusEnum.CLOSED.getStatus(), finishedDate,
                LocalDateTime.now(), closedReason);
        afterStatusChanged(plan, "关闭计划（" + PlanClosedReasonEnum.of(closedReason).getName() + "）");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activatePlan(Long id) {
        PlanDO plan = validatePlanExists(id);
        if (!PlanStatusEnum.CLOSED.getStatus().equals(plan.getStatus())) {
            throw exception(PLAN_STATUS_ILLEGAL, PlanStatusEnum.nameOf(plan.getStatus()));
        }
        // 注意：禅道激活后是「进行中」而不是「未开始」（control.php activate → updateStatus('doing')）
        planMapper.updateStatusFields(id, PlanStatusEnum.DOING.getStatus(), null, null, "");
        afterStatusChanged(plan, "激活计划");
    }

    // ==================== 写：删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlan(Long id) {
        PlanDO plan = validatePlanExists(id);
        List<PlanDO> children = planMapper.selectChildren(id);
        if (!children.isEmpty()) {
            throw exception(PLAN_HAS_CHILDREN, children.size());
        }

        // 偏离禅道：禅道的删除不会清理 zt_story.plan，会留下指向已删除计划的脏引用。
        // 这里把需求从该计划上摘掉，保证「计划列表里的需求数」和「需求上的计划」始终一致。
        for (StoryDO story : storyMapper.selectListByPlan(id)) {
            storyMapper.updateById(buildStoryPlanUpdate(story.getId(), removePlan(story.getPlan(), id)));
        }
        List<BugDO> bugs = bugMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BugDO>()
                .eq(BugDO::getPlan, id));
        for (BugDO bug : bugs) {
            BugDO updateObj = new BugDO();
            updateObj.setId(bug.getId());
            updateObj.setPlan(0L);
            bugMapper.updateById(updateObj);
        }

        planMapper.deleteById(id);
        // 删掉子计划后，父计划的「有子计划」标记要跟着变
        if (plan.getParent() != null && plan.getParent() > 0) {
            fixParentMarker(plan.getParent());
        }
        actionService.recordAction(OBJECT_TYPE_PLAN, id, ActionTypeEnum.DELETED,
                "删除计划：" + plan.getTitle());
    }

    // ==================== 读 ====================

    @Override
    public PlanDO getPlan(Long id) {
        return validatePlanExists(id);
    }

    @Override
    public PlanDO validatePlanExists(Long id) {
        PlanDO plan = id == null ? null : planMapper.selectById(id);
        if (plan == null) {
            throw exception(PLAN_NOT_EXISTS, id);
        }
        return plan;
    }

    @Override
    public PageResult<PlanDO> getPlanPage(PlanPageReqVO reqVO) {
        return planMapper.selectPage(reqVO);
    }

    @Override
    public List<PlanDO> getPlanListByProduct(Long product, Long branch) {
        return planMapper.selectListByProduct(product, branch);
    }

    @Override
    public List<StoryDO> getPlanStories(Long planId) {
        validatePlanExists(planId);
        return storyMapper.selectListByPlan(planId);
    }

    @Override
    public List<StoryDO> getUnlinkedStories(Long planId) {
        PlanDO plan = validatePlanExists(planId);
        return storyMapper.selectUnlinkedByProduct(plan.getProduct(), plan.getBranch());
    }

    @Override
    public Long countStoriesByPlan(Long planId) {
        return planMapper.countStoriesByPlan(planId);
    }

    @Override
    public Long countBugsByPlan(Long planId) {
        return planMapper.countBugsByPlan(planId);
    }

    @Override
    public Long getPlanChildrenCount(Long planId) {
        return planId == null ? 0L : planMapper.countChildren(planId);
    }

    @Override
    public Map<Long, Long> countStoriesByPlans(List<Long> planIds) {
        Map<Long, Long> result = new java.util.HashMap<>();
        if (planIds == null || planIds.isEmpty()) {
            return result;
        }
        // 一条 SQL 捞出这些计划下的全部需求（只取 id 与 plan 两列），
        // 再在内存里按逗号列表累加 —— 因为 plan 是多值列，SQL 层做不了 GROUP BY。
        for (StoryDO story : planMapper.selectStoriesOfPlans(planIds)) {
            for (Long planId : planIds) {
                if (containsPlan(story.getPlan(), planId)) {
                    result.merge(planId, 1L, Long::sum);
                }
            }
        }
        return result;
    }

    // ==================== 写：关联需求 / Bug ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkStories(Long planId, List<Long> storyIds) {
        PlanDO plan = validatePlanExists(planId);
        List<Long> linked = new ArrayList<>();
        for (Long storyId : storyIds) {
            StoryDO story = storyMapper.selectById(storyId);
            if (story == null) {
                continue;
            }
            if (containsPlan(story.getPlan(), planId)) {
                continue;
            }
            // 禅道规则：type='story' 的研发需求独占一个计划（换计划=移走）；
            // 其它类型（需求/史诗）可以同时挂在多个计划上，所以是逗号累加。
            String oldPlan = story.getPlan();
            String newPlan = "story".equals(story.getType())
                    ? String.valueOf(planId)
                    : appendPlan(oldPlan, planId);
            storyMapper.updateById(buildStoryPlanUpdate(storyId, newPlan));
            linked.add(storyId);

            actionService.recordAction(OBJECT_TYPE_STORY, storyId, ActionTypeEnum.EDITED,
                    "关联到计划 #" + planId);
            if ("story".equals(story.getType()) && StringUtils.hasText(oldPlan)
                    && !String.valueOf(planId).equals(oldPlan.trim())) {
                actionService.recordAction(OBJECT_TYPE_PLAN, Long.valueOf(oldPlan.trim()),
                        ActionTypeEnum.EDITED, "需求 #" + storyId + " 已移到计划：" + plan.getTitle());
            }
        }
        if (!linked.isEmpty()) {
            actionService.recordAction(OBJECT_TYPE_PLAN, planId, ActionTypeEnum.EDITED,
                    "关联需求：" + joinIds(linked));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkStory(Long planId, Long storyId) {
        validatePlanExists(planId);
        StoryDO story = storyMapper.selectById(storyId);
        if (story == null || !containsPlan(story.getPlan(), planId)) {
            return;
        }
        storyMapper.updateById(buildStoryPlanUpdate(storyId, removePlan(story.getPlan(), planId)));
        actionService.recordAction(OBJECT_TYPE_STORY, storyId, ActionTypeEnum.EDITED,
                "从计划 #" + planId + " 移除");
        actionService.recordAction(OBJECT_TYPE_PLAN, planId, ActionTypeEnum.EDITED,
                "移除需求 #" + storyId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkBugs(Long planId, List<Long> bugIds) {
        PlanDO plan = validatePlanExists(planId);
        List<Long> linked = new ArrayList<>();
        for (Long bugId : bugIds) {
            BugDO bug = bugMapper.selectById(bugId);
            // 缺陷侧的 plan 是单值（zt_bug.plan 是 int，不是逗号列表）
            if (bug == null || planId.equals(bug.getPlan())) {
                continue;
            }
            BugDO updateObj = new BugDO();
            updateObj.setId(bugId);
            updateObj.setPlan(planId);
            bugMapper.updateById(updateObj);
            linked.add(bugId);
            actionService.recordAction(OBJECT_TYPE_BUG, bugId, ActionTypeEnum.EDITED,
                    "关联到计划 #" + planId);
        }
        if (!linked.isEmpty()) {
            actionService.recordAction(OBJECT_TYPE_PLAN, planId, ActionTypeEnum.EDITED,
                    "关联 Bug：" + joinIds(linked));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkBug(Long planId, Long bugId) {
        BugDO bug = bugMapper.selectById(bugId);
        if (bug == null || !planId.equals(bug.getPlan())) {
            return;
        }
        BugDO updateObj = new BugDO();
        updateObj.setId(bugId);
        updateObj.setPlan(0L);
        bugMapper.updateById(updateObj);
        actionService.recordAction(OBJECT_TYPE_BUG, bugId, ActionTypeEnum.EDITED,
                "从计划 #" + planId + " 移除");
        actionService.recordAction(OBJECT_TYPE_PLAN, planId, ActionTypeEnum.EDITED,
                "移除 Bug #" + bugId);
    }

    // ==================== 内部：校验 ====================

    /**
     * 处理「待定」与日期校验，返回 [begin, end]。
     *
     * <p>禅道用日期哨兵 {@code 2030-01-01} 表示待定：{@code $config->productplan->future}。
     */
    private LocalDate[] resolveDateRange(PlanSaveReqVO reqVO) {
        if (Boolean.TRUE.equals(reqVO.getFuture())) {
            return new LocalDate[]{PlanDO.FUTURE_DATE, PlanDO.FUTURE_DATE};
        }
        if (reqVO.getBegin() == null || reqVO.getEnd() == null) {
            throw exception(PLAN_DATE_REQUIRED);
        }
        if (reqVO.getEnd().isBefore(reqVO.getBegin())) {
            throw exception(PLAN_END_BEFORE_BEGIN);
        }
        return new LocalDate[]{reqVO.getBegin(), reqVO.getEnd()};
    }

    /**
     * 组装 branch 逗号串。
     *
     * <p>禅道：产品类型不是 normal 时**必须**选分支，否则报「分支不能为空」。
     * 主干用 0 表示；多选时去重排序后拼成 {@code '1,2'}。
     */
    private String buildBranch(ProductDO product, List<Long> branches) {
        String branchName = ProductTypeEnum.branchNameOf(product.getType());
        if (!ProductTypeEnum.supportsBranch(product.getType())) {
            // 普通产品没有分支概念：只接受「主干」，传了真实分支说明调用方搞错了产品，
            // 直接报错比悄悄改成主干更好排查
            boolean hasRealBranch = branches != null
                    && branches.stream().anyMatch(id -> id != null && id != 0L);
            if (hasRealBranch) {
                throw exception(PLAN_BRANCH_NOT_IN_PRODUCT,
                        branches.stream().filter(id -> id != null && id != 0L).findFirst().orElse(0L));
            }
            return "0";
        }
        if (branches == null || branches.isEmpty()) {
            throw exception(PLAN_BRANCH_REQUIRED, branchName);
        }
        Set<Long> normalized = new LinkedHashSet<>(branches);
        if (normalized.contains(0L)) {
            return "0";
        }
        // 分支必须属于该产品（禅道由前端下拉保证，这里补一道服务端校验）
        Set<Long> valid = new LinkedHashSet<>();
        for (BranchDO branch : branchService.getBranchListByProduct(product.getId(), null)) {
            if (branch.getId() != null) {
                valid.add(branch.getId());
            }
        }
        for (Long branchId : normalized) {
            if (!valid.contains(branchId)) {
                throw exception(PLAN_BRANCH_NOT_IN_PRODUCT, branchId);
            }
        }
        return normalized.stream().sorted(Comparator.naturalOrder())
                .map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("0");
    }

    /**
     * 子计划必须落在父计划的日期范围内（禅道 create/update 里的 begin/end 校验）
     */
    private void checkChildWithinParent(LocalDate[] range, PlanDO parent) {
        if (parent == null) {
            return;
        }
        if (parent.getBegin() != null && !PlanDO.FUTURE_DATE.equals(parent.getBegin())
                && range[0].isBefore(parent.getBegin())) {
            throw exception(PLAN_CHILD_OUT_OF_PARENT, "开始日期", range[0], "开始日期", parent.getBegin());
        }
        if (parent.getEnd() != null && !PlanDO.FUTURE_DATE.equals(parent.getEnd())
                && range[1].isAfter(parent.getEnd())) {
            throw exception(PLAN_CHILD_OUT_OF_PARENT, "结束日期", range[1], "结束日期", parent.getEnd());
        }
    }

    /**
     * 父计划的日期必须覆盖所有子计划（禅道 update() 里对 parent == -1 的处理）
     */
    private void checkParentCoversChildren(PlanDO oldPlan, LocalDate[] range) {
        List<PlanDO> children = planMapper.selectChildren(oldPlan.getId());
        if (children.isEmpty()) {
            return;
        }
        for (PlanDO child : children) {
            if (child.getBegin() != null && !PlanDO.FUTURE_DATE.equals(child.getBegin())
                    && range[0].isAfter(child.getBegin())) {
                throw exception(PLAN_PARENT_NOT_COVER_CHILD, "开始日期", range[0], "早开始日期", child.getBegin());
            }
            if (child.getEnd() != null && !PlanDO.FUTURE_DATE.equals(child.getEnd())
                    && range[1].isBefore(child.getEnd())) {
                throw exception(PLAN_PARENT_NOT_COVER_CHILD, "结束日期", range[1], "晚结束日期", child.getEnd());
            }
        }
    }

    // ==================== 内部：父子计划联动 ====================

    /**
     * 建子计划时把父计划上的需求/Bug 转过来（禅道 {@code transferStoriesAndBugs}）。
     *
     * <p>规则：父计划上的数据，只有**分支落在子计划覆盖范围内**的才转到子计划；
     * 其它分支的直接从父计划上摘掉（因为父计划已经把这块拆给别的子计划了）。
     */
    private void transferStoriesAndBugs(PlanDO parent, PlanDO child) {
        for (StoryDO story : storyMapper.selectListByPlan(parent.getId())) {
            boolean inBranch = story.getBranch() == null || story.getBranch() == 0
                    || containsPlan(child.getBranch(), story.getBranch());
            String newPlan = inBranch
                    ? replacePlan(story.getPlan(), parent.getId(), child.getId())
                    : removePlan(story.getPlan(), parent.getId());
            storyMapper.updateById(buildStoryPlanUpdate(story.getId(), newPlan));
        }
        List<BugDO> bugs = bugMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BugDO>()
                .eq(BugDO::getPlan, parent.getId()));
        for (BugDO bug : bugs) {
            boolean inBranch = bug.getBranch() == null || bug.getBranch() == 0
                    || containsPlan(child.getBranch(), bug.getBranch());
            BugDO updateObj = new BugDO();
            updateObj.setId(bug.getId());
            updateObj.setPlan(inBranch ? child.getId() : 0L);
            bugMapper.updateById(updateObj);
        }
    }

    /**
     * 计划分支范围变化后，把不再覆盖的需求/Bug 摘掉（禅道 {@code unlinkOldBranch}）
     */
    private void unlinkOutOfBranch(PlanDO plan, String newBranch) {
        for (StoryDO story : storyMapper.selectListByPlan(plan.getId())) {
            if (story.getBranch() != null && story.getBranch() != 0
                    && !containsPlan(newBranch, story.getBranch())) {
                storyMapper.updateById(buildStoryPlanUpdate(story.getId(),
                        removePlan(story.getPlan(), plan.getId())));
                actionService.recordAction(OBJECT_TYPE_STORY, story.getId(), ActionTypeEnum.EDITED,
                        "计划分支调整，自动移出计划 #" + plan.getId());
            }
        }
        List<BugDO> bugs = bugMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BugDO>()
                .eq(BugDO::getPlan, plan.getId()));
        for (BugDO bug : bugs) {
            if (bug.getBranch() != null && bug.getBranch() != 0 && !containsPlan(newBranch, bug.getBranch())) {
                BugDO updateObj = new BugDO();
                updateObj.setId(bug.getId());
                updateObj.setPlan(0L);
                bugMapper.updateById(updateObj);
                actionService.recordAction(OBJECT_TYPE_BUG, bug.getId(), ActionTypeEnum.EDITED,
                        "计划分支调整，自动移出计划 #" + plan.getId());
            }
        }
    }

    /**
     * 子计划状态变化后回写父计划状态（禅道 {@code updateParentStatus} 的等价实现）
     */
    private void refreshParentStatus(Long parentId) {
        List<PlanDO> children = planMapper.selectChildren(parentId);
        if (children.isEmpty()) {
            planMapper.updateParent(parentId, PlanDO.PARENT_NONE);
            planMapper.updateStatusFields(parentId, PlanStatusEnum.WAIT.getStatus(), null, null, "");
            return;
        }
        PlanDO parent = planMapper.selectById(parentId);
        boolean allClosed = children.stream()
                .allMatch(c -> PlanStatusEnum.CLOSED.getStatus().equals(c.getStatus()));
        boolean anyRunning = children.stream().anyMatch(c ->
                PlanStatusEnum.WAIT.getStatus().equals(c.getStatus())
                        || PlanStatusEnum.DOING.getStatus().equals(c.getStatus()));

        if (allClosed) {
            if (!PlanStatusEnum.CLOSED.getStatus().equals(parent.getStatus())) {
                planMapper.updateStatusFields(parentId, PlanStatusEnum.CLOSED.getStatus(),
                        parent.getFinishedDate(), LocalDateTime.now(), PlanClosedReasonEnum.DONE.getReason());
            }
            return;
        }
        if (!anyRunning) {
            // 子计划全部完成 → 父计划完成
            if (!PlanStatusEnum.DONE.getStatus().equals(parent.getStatus())) {
                planMapper.updateStatusFields(parentId, PlanStatusEnum.DONE.getStatus(),
                        LocalDateTime.now(), null, "");
            }
            return;
        }
        if (!PlanStatusEnum.DOING.getStatus().equals(parent.getStatus())) {
            planMapper.updateStatusFields(parentId, PlanStatusEnum.DOING.getStatus(), null, null, "");
        }
    }

    /**
     * 删除子计划后重算父计划的「有子计划」标记
     */
    private void fixParentMarker(Long parentId) {
        Long count = planMapper.countChildren(parentId);
        planMapper.updateParent(parentId, count > 0 ? PlanDO.PARENT_HAS_CHILDREN : PlanDO.PARENT_NONE);
    }

    /**
     * 状态流转后的统一收尾：记动作 + 回写父计划状态
     */
    private void afterStatusChanged(PlanDO plan, String comment) {
        PlanDO newPlan = planMapper.selectById(plan.getId());
        actionService.recordActionWithChanges(OBJECT_TYPE_PLAN, plan.getId(),
                ActionTypeEnum.EDITED, comment, plan, newPlan);
        if (plan.getParent() != null && plan.getParent() > 0) {
            refreshParentStatus(plan.getParent());
        }
    }

    // ==================== 内部：plan 逗号列表工具 ====================

    private boolean containsPlan(String planList, Long planId) {
        if (!StringUtils.hasText(planList) || planId == null) {
            return false;
        }
        for (String item : planList.split(",")) {
            if (String.valueOf(planId).equals(item.trim())) {
                return true;
            }
        }
        return false;
    }

    private String appendPlan(String planList, Long planId) {
        if (containsPlan(planList, planId)) {
            return planList;
        }
        return StringUtils.hasText(planList) ? planList.trim() + "," + planId : String.valueOf(planId);
    }

    private String removePlan(String planList, Long planId) {
        if (!StringUtils.hasText(planList)) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (String item : planList.split(",")) {
            String trimmed = item.trim();
            if (StringUtils.hasText(trimmed) && !String.valueOf(planId).equals(trimmed)) {
                kept.add(trimmed);
            }
        }
        return String.join(",", kept);
    }

    private String replacePlan(String planList, Long oldPlanId, Long newPlanId) {
        return appendPlan(removePlan(planList, oldPlanId), newPlanId);
    }

    private StoryDO buildStoryPlanUpdate(Long storyId, String plan) {
        StoryDO updateObj = new StoryDO();
        updateObj.setId(storyId);
        updateObj.setPlan(plan);
        return updateObj;
    }

    private String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }

    private String currentAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : "";
    }

}
