package cn.iocoder.yudao.module.zentao.service.story;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryChangeReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryCloseReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryReviewStartReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryReviewSubmitReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StorySaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryTreeNodeRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryTypeRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryReviewDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StorySpecDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.story.StoryMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.story.StoryReviewMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.story.StorySpecMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.story.StoryCategoryEnum;
import cn.iocoder.yudao.module.zentao.enums.story.StoryReviewResultEnum;
import cn.iocoder.yudao.module.zentao.enums.story.StoryStageEnum;
import cn.iocoder.yudao.module.zentao.enums.story.StoryStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.story.StoryTypeEnum;
import jakarta.annotation.Resource;
import cn.iocoder.yudao.module.zentao.service.module.ModuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import cn.iocoder.yudao.module.zentao.service.action.ActionServiceImpl;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 需求 Service 实现
 *
 * 业务规则来源：禅道 {@code module/story/model.php} + {@code module/story/tao.php}。
 *
 * <h3>需求版本机制（本类最核心的部分）</h3>
 * 禅道把需求拆成"头部 + 追加式快照"两张表：
 * <pre>
 *   zt_story      头部：当前状态、当前版本号、用于列表展示与检索的标题
 *   zt_storyspec  快照：(story, version) 唯一，保存每一版的 title/spec/verify
 * </pre>
 * 对应两条写路径，语义完全不同，不能混用：
 * <pre>
 *   updateStory()  普通编辑 —— 原地 UPDATE 当前版本的快照，版本号不变
 *   changeStory()  正式变更 —— 版本号 +1，INSERT 一条新快照，旧版本永久保留
 * </pre>
 * 读取时用 {@code (story, version)} 取快照并叠加到头部对象上，
 * 与禅道 {@code getById($id, $version)}（version=0 表示当前版）语义一致。
 */
@Slf4j
@Service
public class StoryServiceImpl implements StoryService {

    /**
     * 关闭原因 - 重复。禅道 {@code closedReason=duplicate} 时要求必须指定重复需求。
     */
    private static final String CLOSED_REASON_DUPLICATE = "duplicate";

    /**
     * 需求类型默认值，对应禅道 {@code zt_story.type} 的默认值
     */
    private static final String DEFAULT_TYPE = "story";

    @Resource
    private ModuleService moduleService;

    @Resource
    private StoryMapper storyMapper;

    @Resource
    private StorySpecMapper storySpecMapper;

    @Resource
    private StoryReviewMapper storyReviewMapper;

    @Resource
    private AdminUserApi adminUserApi;

    @Resource
    private ActionService actionService;

    // ==================== 写：创建 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStory(StorySaveReqVO createReqVO) {
        StoryDO story = BeanUtils.toBean(createReqVO, StoryDO.class);
        // 禅道新建需求的初始值：草稿 / 未开始 / 版本 1
        story.setStatus(StoryStatusEnum.DRAFT.getStatus());
        story.setStage(StoryStageEnum.WAIT.getStage());
        story.setVersion(1);
        // 需求分层：type 决定这条需求是业务需求(epic)/用户需求(requirement)/研发需求(story)
        String storyType = StringUtils.hasText(createReqVO.getType()) ? createReqVO.getType() : DEFAULT_TYPE;
        validateStoryType(storyType);
        story.setType(storyType);
        story.setCategory(StringUtils.hasText(createReqVO.getCategory())
                ? createReqVO.getCategory() : StoryCategoryEnum.FEATURE.getCategory());
        // 创建人信息
        String operator = currentAccount();
        story.setOpenedBy(operator);
        story.setOpenedDate(LocalDateTime.now());
        if (StringUtils.hasText(createReqVO.getAssignedTo())) {
            story.setAssignedDate(LocalDateTime.now());
        }
        storyMapper.insert(story);

        // ★ 父子关系（需求分解）：
        //   有父 → root 继承父的 root、path = 父 path + 自己、grade = 父 grade + 1、
        //          parentVersion 冻结**分解时父需求的版本**（父后来变更，子要提示）
        //   没父 → 自己就是一棵树：root = 自己、path = ,自己,
        //   path 的格式与 zt_module 一致：逗号包起来且包含自己，前缀匹配就能取出整棵子树
        if (story.getParent() != null && story.getParent() > 0) {
            StoryDO parent = validateStoryExists(story.getParent());
            if (!Objects.equals(parent.getProduct(), story.getProduct())) {
                throw exception(STORY_PARENT_NOT_SAME_PRODUCT, story.getParent());
            }
            // 需求分层的父子类型规则：父的层级不能低于子
            // （业务需求下面挂用户需求/研发需求，用户需求下面挂研发需求，研发需求只能挂研发需求）
            validateParentType(story.getType(), parent);
            StoryDO treeUpdate = new StoryDO();
            treeUpdate.setId(story.getId());
            treeUpdate.setParent(parent.getId());
            treeUpdate.setParentVersion(parent.getVersion() == null ? 1 : parent.getVersion());
            treeUpdate.setRoot(parent.getRoot() == null || parent.getRoot() == 0
                    ? parent.getId() : parent.getRoot());
            treeUpdate.setPath(buildChildPath(parent, story.getId()));
            treeUpdate.setGrade((parent.getGrade() == null ? 1 : parent.getGrade()) + 1);
            storyMapper.updateById(treeUpdate);
        } else {
            StoryDO treeUpdate = new StoryDO();
            treeUpdate.setId(story.getId());
            treeUpdate.setParent(0L);
            treeUpdate.setParentVersion(0);
            treeUpdate.setRoot(story.getId());
            treeUpdate.setPath("," + story.getId() + ",");
            treeUpdate.setGrade(1);
            storyMapper.updateById(treeUpdate);
        }

        // 同步写入 version=1 的快照，否则按版本读取时拿不到内容
        insertStorySpec(story.getId(), 1, createReqVO.getTitle(), createReqVO.getSpec(),
                createReqVO.getVerify());

        // 记录操作日志
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_STORY, story.getId(),
                ActionTypeEnum.CREATED, null);
        // 子需求一建出来，父需求的 isParent / estimate / 状态就要跟着变
        refreshParent(story.getId());
        return story.getId();
    }

    // ==================== 父子需求（需求分解） ====================

    @Override
    public List<StoryDO> getChildList(Long parentId) {
        validateStoryExists(parentId);
        return storyMapper.selectListByParent(parentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int subdivide(Long parentId, List<Long> childIds) {
        StoryDO parent = validateStoryExists(parentId);
        if (childIds == null || childIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Long childId : childIds) {
            if (Objects.equals(childId, parent.getId())) {
                throw exception(STORY_PARENT_IS_CHILD);
            }
            StoryDO child = validateStoryExists(childId);
            if (!Objects.equals(child.getProduct(), parent.getProduct())) {
                throw exception(STORY_PARENT_NOT_SAME_PRODUCT, parentId);
            }
            // 需求分层：被挂的子需求类型也要符合层级规则，不能把业务需求挂到用户需求下面
            validateParentType(child.getType(), parent);
            // 已经是别人的子需求了就不再改挂（禅道的「分解」是给还没挂的子需求用）
            if (child.getParent() != null && child.getParent() > 0) {
                throw exception(STORY_ALREADY_HAS_PARENT, childId);
            }
            // 不能把父需求挂到自己的子孙下
            if (StringUtils.hasText(child.getPath()) && StringUtils.hasText(parent.getPath())
                    && parent.getPath().startsWith(child.getPath())) {
                throw exception(STORY_PARENT_IS_CHILD);
            }
            StoryDO update = new StoryDO();
            update.setId(child.getId());
            update.setParent(parent.getId());
            update.setParentVersion(parent.getVersion() == null ? 1 : parent.getVersion());
            update.setRoot(parent.getRoot() == null || parent.getRoot() == 0 ? parent.getId() : parent.getRoot());
            update.setPath(buildChildPath(parent, child.getId()));
            update.setGrade((parent.getGrade() == null ? 1 : parent.getGrade()) + 1);
            storyMapper.updateById(update);
            count++;
        }
        // 注意这里要传**父需求编号**，用 refreshParentByParentId 而不是 refreshParent(childId)
        refreshParentByParentId(parentId);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_STORY, parentId,
                ActionTypeEnum.EDITED, "分解出 " + count + " 条子需求");
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchCreateChild(Long parentId, List<String> titles) {
        StoryDO parent = validateStoryExists(parentId);
        List<Long> ids = new ArrayList<>();
        if (titles == null || titles.isEmpty()) {
            return ids;
        }
        for (String title : titles) {
            if (!StringUtils.hasText(title)) {
                continue;
            }
            // 只给标题，其余字段从父需求继承 —— 分解出来的子需求本来就是同一个需求的细分
            StorySaveReqVO reqVO = new StorySaveReqVO();
            reqVO.setProduct(parent.getProduct());
            reqVO.setModule(parent.getModule());
            reqVO.setBranch(parent.getBranch());
            reqVO.setPlan(parent.getPlan());
            reqVO.setTitle(title.trim());
            // 子需求的类型由父需求决定（禅道：业务需求分解出用户需求，用户需求分解出研发需求，
            // 研发需求分解出来的还是研发需求），不是简单沿用父类型
            reqVO.setType(StoryTypeEnum.childTypeOf(parent.getType()));
            reqVO.setCategory(parent.getCategory());
            reqVO.setPri(parent.getPri());
            reqVO.setParent(parentId);
            ids.add(createStory(reqVO));
        }
        return ids;
    }

    @Override
    public void fillParentInfo(List<StoryRespVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (StoryRespVO vo : list) {
            // 实体里是 Integer(0/1)，VO 里是 Boolean，这里转一下；顺带统计子需求数
            long childCount = vo.getId() == null ? 0 : storyMapper.countByParent(vo.getId());
            vo.setChildCount(childCount);
            if (vo.getParent() != null && vo.getParent() > 0) {
                StoryDO parent = storyMapper.selectById(vo.getParent());
                if (parent != null) {
                    vo.setParentTitle(parent.getTitle());
                    // 父需求升版且仍是激活态 → 子需求需要确认（与 projectstory 的「版本已变更」同思路）
                    vo.setParentChanged(parent.getVersion() != null && vo.getParentVersion() != null
                            && parent.getVersion() > vo.getParentVersion()
                            && StoryStatusEnum.ACTIVE.getStatus().equals(parent.getStatus()));
                }
            }
        }
    }

    /**
     * 刷新父需求 —— 禅道 updateParentStatus 的等价实现。
     *
     * <p>三件事，都是「子动父跟着动」：
     * <ol>
     *   <li>{@code isParent}：有子需求就是 1，没有就是 0（列表上要显示「父」标记）</li>
     *   <li>{@code estimate} = **所有子需求工作量之和** —— 父需求自己不填工时，
     *       它是汇总出来的（禅道 computeEstimate）</li>
     *   <li>状态级联：<b>子需求全部关闭 → 父需求自动关闭；父需求已关闭但还有子需求没关 → 父需求自动激活</b></li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshParent(Long childId) {
        StoryDO child = storyMapper.selectById(childId);
        if (child == null || child.getParent() == null || child.getParent() <= 0) {
            return;
        }
        refreshParentByParentId(child.getParent());
    }

    /**
     * 按**父需求编号**刷新父需求。
     *
     * <p>和 {@link #refreshParent(Long)} 的区别只在入参语义：那个收子需求编号、自己往上找父；
     * 这个直接收父编号 —— 分解（subdivide / batchCreateChild）手里只有父编号，
     * 早期把父编号传给了收子编号的方法，于是 `child.getParent() == 0` 直接返回，
     * 父需求的 isParent / 工时 / 状态全都没更新（这个 bug 实测踩到过）。
     */
    private void refreshParentByParentId(Long parentId) {
        StoryDO parent = storyMapper.selectById(parentId);
        if (parent == null) {
            return;
        }
        List<StoryDO> children = storyMapper.selectListByParent(parent.getId());
        StoryDO update = new StoryDO();
        update.setId(parent.getId());
        update.setIsParent(children.isEmpty() ? 0 : 1);
        if (!children.isEmpty()) {
            double estimate = 0;
            for (StoryDO item : children) {
                if (item.getEstimate() != null) {
                    estimate += item.getEstimate().doubleValue();
                }
            }
            update.setEstimate(java.math.BigDecimal.valueOf(estimate).setScale(2, java.math.RoundingMode.HALF_UP));
        }
        // 状态级联
        boolean allClosed = !children.isEmpty() && children.stream()
                .allMatch(item -> StoryStatusEnum.CLOSED.getStatus().equals(item.getStatus()));
        boolean anyNotClosed = children.stream()
                .anyMatch(item -> !StoryStatusEnum.CLOSED.getStatus().equals(item.getStatus()));
        String newStatus = parent.getStatus();
        if (allClosed) {
            newStatus = StoryStatusEnum.CLOSED.getStatus();
        } else if (StoryStatusEnum.CLOSED.getStatus().equals(parent.getStatus()) && anyNotClosed) {
            // 父需求已关闭但子需求又被激活 → 父需求回到激活态
            newStatus = StoryStatusEnum.ACTIVE.getStatus();
        }
        update.setStatus(newStatus);
        storyMapper.updateById(update);
    }

    /**
     * 拼子需求的 path：父 path 去掉末尾逗号 + 自己 + 逗号。
     * 父 path 是 ,4, → 子的 path 是 ,4,5,
     */
    private String buildChildPath(StoryDO parent, Long childId) {
        String parentPath = StringUtils.hasText(parent.getPath())
                ? parent.getPath() : "," + parent.getId() + ",";
        return parentPath + childId + ",";
    }

    // 说明：本实现只支持两层分解（父 + 直接子需求），与禅道的「分解」入口一致，
    // 所以改挂父需求时不需要迁移子孙的 path。

    // ==================== 写：普通编辑（不动版本号） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStory(StorySaveReqVO updateReqVO) {
        // 注意：这里必须用 getStoryByVersion 而不是 validateStoryExists。
        // spec/verify 是 @TableField(exist=false) 的瞬态字段，直接从主表读出来的对象
        // 这两个字段是 null，会导致操作日志里出现「旧值为空」的错误差异。
        StoryDO oldStory = getStoryByVersion(updateReqVO.getId(), null);
        // 禅道规则：已关闭的需求不允许直接修改，需先激活
        if (StoryStatusEnum.CLOSED.getStatus().equals(oldStory.getStatus())) {
            throw exception(STORY_CLOSED_CANNOT_UPDATE);
        }

        StoryDO updateObj = BeanUtils.toBean(updateReqVO, StoryDO.class);
        // 普通编辑不推进版本号（spec/verify 是非持久化字段，不会被 updateById 带上）
        updateObj.setVersion(oldStory.getVersion());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        // 指派对象发生变化时刷新指派时间
        if (StringUtils.hasText(updateReqVO.getAssignedTo())
                && !updateReqVO.getAssignedTo().equals(oldStory.getAssignedTo())) {
            updateObj.setAssignedDate(LocalDateTime.now());
        }
        storyMapper.updateById(updateObj);

        // 原地刷新当前版本的快照 —— 对应禅道 tao.php doUpdateSpec()
        updateStorySpecInPlace(oldStory.getId(), oldStory.getVersion(), updateReqVO.getTitle(),
                updateReqVO.getSpec(), updateReqVO.getVerify());

        // 记录操作日志 + 字段级差异（普通编辑不推进版本号，但差异照记）
        actionService.recordActionWithChanges(ActionServiceImpl.OBJECT_TYPE_STORY,
                oldStory.getId(), ActionTypeEnum.EDITED, null, oldStory, updateObj);
    }

    // ==================== 写：正式变更（版本号 +1，追加快照） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer changeStory(StoryChangeReqVO reqVO) {
        // 同 updateStory：需要带 spec/verify 的完整旧对象，否则日志差异缺旧值
        StoryDO oldStory = getStoryByVersion(reqVO.getId(), null);
        if (StoryStatusEnum.CLOSED.getStatus().equals(oldStory.getStatus())) {
            throw exception(STORY_CLOSED_CANNOT_UPDATE);
        }

        // 版本号 +1
        int newVersion = (oldStory.getVersion() == null ? 1 : oldStory.getVersion()) + 1;

        String operator = currentAccount();
        StoryDO updateObj = new StoryDO();
        updateObj.setId(oldStory.getId());
        updateObj.setVersion(newVersion);
        updateObj.setTitle(reqVO.getTitle());
        // spec/verify 是瞬态字段，写进 updateObj 只为让操作日志能算出新值；
        // 它们不会被 updateById 带进 SQL（@TableField(exist = false)），真正的持久化在下面的快照里
        updateObj.setSpec(reqVO.getSpec());
        updateObj.setVerify(reqVO.getVerify());
        updateObj.setLastEditedBy(operator);
        updateObj.setLastEditedDate(LocalDateTime.now());
        if (StringUtils.hasText(reqVO.getAssignedTo())
                && !reqVO.getAssignedTo().equals(oldStory.getAssignedTo())) {
            updateObj.setAssignedTo(reqVO.getAssignedTo());
            updateObj.setAssignedDate(LocalDateTime.now());
        }
        storyMapper.updateById(updateObj);

        // 追加新版本快照 —— 对应禅道 tao.php doCreateSpec()
        // 旧版本的行不动，历史因此得以完整保留
        insertStorySpec(oldStory.getId(), newVersion, reqVO.getTitle(), reqVO.getSpec(),
                reqVO.getVerify());

        // 记录操作日志。备注里带上变更说明，diff 里带上具体改了什么
        actionService.recordActionWithChanges(ActionServiceImpl.OBJECT_TYPE_STORY,
                oldStory.getId(), ActionTypeEnum.CHANGED, reqVO.getComment(), oldStory, updateObj);
        return newVersion;
    }

    // ==================== 写：关闭 / 激活 ====================

    @Override
    public void closeStory(StoryCloseReqVO reqVO) {
        StoryDO story = validateStoryExists(reqVO.getId());
        if (StoryStatusEnum.CLOSED.getStatus().equals(story.getStatus())) {
            throw exception(STORY_ALREADY_CLOSED);
        }

        // 禅道规则：关闭原因为 duplicate 时，必须指定重复需求，且该需求要真实存在
        if (CLOSED_REASON_DUPLICATE.equals(reqVO.getClosedReason())) {
            if (reqVO.getDuplicateStory() == null) {
                throw exception(STORY_DUPLICATE_STORY_REQUIRED);
            }
            if (reqVO.getDuplicateStory().equals(story.getId())) {
                throw exception(STORY_DUPLICATE_SELF);
            }
            if (storyMapper.selectById(reqVO.getDuplicateStory()) == null) {
                throw exception(STORY_DUPLICATE_STORY_NOT_EXISTS, reqVO.getDuplicateStory());
            }
        }

        StoryDO updateObj = new StoryDO();
        updateObj.setId(story.getId());
        updateObj.setStatus(StoryStatusEnum.CLOSED.getStatus());
        updateObj.setClosedBy(currentAccount());
        updateObj.setClosedDate(LocalDateTime.now());
        updateObj.setClosedReason(reqVO.getClosedReason());
        updateObj.setDuplicateStory(reqVO.getDuplicateStory() == null ? 0L : reqVO.getDuplicateStory());
        storyMapper.updateById(updateObj);

        actionService.recordActionWithChanges(ActionServiceImpl.OBJECT_TYPE_STORY,
                story.getId(), ActionTypeEnum.CLOSED, reqVO.getComment(), story, updateObj);
        // ★ 子需求关闭后回头看看父需求：全部子需求关闭 → 父需求自动关闭
        refreshParent(story.getId());
    }

    @Override
    public void activateStory(Long id) {
        StoryDO story = validateStoryExists(id);
        // 禅道规则：只有已关闭的需求才能激活
        if (!StoryStatusEnum.CLOSED.getStatus().equals(story.getStatus())) {
            throw exception(STORY_NOT_CLOSED_CANNOT_ACTIVATE);
        }

        StoryDO updateObj = new StoryDO();
        updateObj.setId(id);
        updateObj.setStatus(StoryStatusEnum.ACTIVE.getStatus());
        updateObj.setActivatedDate(LocalDateTime.now());
        updateObj.setClosedReason("");
        storyMapper.updateById(updateObj);

        actionService.recordActionWithChanges(ActionServiceImpl.OBJECT_TYPE_STORY,
                id, ActionTypeEnum.ACTIVATED, null, story, updateObj);
        // ★ 子需求被激活后，父需求要跟着回到激活态
        refreshParent(id);
    }

    // ==================== 写：删除 ====================

    @Override
    public void deleteStory(Long id) {
        validateStoryExists(id);
        // 【有意偏离禅道】禅道删父需求不管子需求（子需求会指向一个已删的父）。
        // 这里显式拒绝，避免「树断了但不报错」。
        Long childCount = storyMapper.countByParent(id);
        if (childCount != null && childCount > 0) {
            throw exception(STORY_HAS_CHILDREN, childCount);
        }
        storyMapper.deleteById(id);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_STORY, id,
                ActionTypeEnum.DELETED, null);
    }

    @Override
    public void deleteStoryList(List<Long> ids) {
        ids.forEach(this::validateStoryExists);
        for (Long id : ids) {
            Long childCount = storyMapper.countByParent(id);
            if (childCount != null && childCount > 0) {
                throw exception(STORY_HAS_CHILDREN, childCount);
            }
        }
        storyMapper.deleteByIds(ids);
        ids.forEach(id -> actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_STORY, id,
                ActionTypeEnum.DELETED, null));
    }

    // ==================== 读 ====================

    @Override
    public StoryDO getStory(Long id) {
        return getStoryByVersion(id, null);
    }

    @Override
    public StoryDO getStoryByVersion(Long id, Integer version) {
        StoryDO story = validateStoryExists(id);
        // version 为空或 0 表示"当前版本"，与禅道 getById 的 version=0 语义一致
        int targetVersion = (version == null || version == 0) ? story.getVersion() : version;
        StorySpecDO spec = storySpecMapper.selectByStoryAndVersion(id, targetVersion);
        if (spec != null) {
            // 禅道以快照里的 title 为准，主表的 title 只用于列表展示与检索
            story.setTitle(spec.getTitle());
            story.setSpec(spec.getSpec());
            story.setVerify(spec.getVerify());
        }
        story.setVersion(targetVersion);
        return story;
    }

    @Override
    public StoryDO validateStoryExists(Long id) {
        if (id == null) {
            return null;
        }
        StoryDO story = storyMapper.selectById(id);
        if (story == null) {
            throw exception(STORY_NOT_EXISTS);
        }
        return story;
    }

    @Override
    public List<StorySpecDO> getStorySpecList(Long storyId) {
        validateStoryExists(storyId);
        return storySpecMapper.selectListByStory(storyId);
    }

    @Override
    public PageResult<StoryDO> getStoryPage(StoryPageReqVO reqVO) {
        // 禅道行为：选中一个父模块时，连带查出它所有子模块下的数据。
        // 做法是把 module 展开成「自己 + 全部子孙」的 id 列表再 IN 查询。
        if (reqVO.getModule() != null) {
            java.util.List<Long> moduleIds = moduleService.getSelfAndDescendantIds(reqVO.getModule());
            if (moduleIds.isEmpty()) {
                // 模块不存在（或已删除）→ 结果必然为空。
                // 注意：这里**不能**直接把空列表交给 inIfPresent —— 空集合会被当成
                // 「没有这个条件」整条丢掉，查询反而退化成「查全部」，这是很隐蔽的错。
                return PageResult.empty();
            }
            reqVO.setModuleIds(moduleIds);
            reqVO.setModule(null);
        }
        return storyMapper.selectPage(reqVO);
    }

    @Override
    public List<StoryDO> getStoryListByProduct(Long product) {
        return storyMapper.selectListByProduct(product);
    }

    // ==================== 需求评审 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startReview(StoryReviewStartReqVO reqVO) {
        StoryDO story = validateStoryExists(reqVO.getId());
        if (StoryStatusEnum.CLOSED.getStatus().equals(story.getStatus())) {
            throw exception(STORY_CLOSED_CANNOT_UPDATE);
        }
        // 评审人去重并保持顺序
        Set<String> reviewers = new LinkedHashSet<>();
        reqVO.getReviewers().forEach(r -> {
            if (StringUtils.hasText(r)) {
                reviewers.add(r.trim());
            }
        });
        if (reviewers.isEmpty()) {
            throw exception(STORY_REVIEWER_EMPTY);
        }

        int version = story.getVersion() == null ? 1 : story.getVersion();

        // 剔除不再参与的评审人 —— 对应禅道 doUpdateReviewer() 里那段 delete
        // 这里值只来自接口参数，已在上面 trim，拼进 SQL 前再做一次单引号剥离
        String inList = reviewers.stream()
                .map(r -> "'" + r.replace("'", "") + "'")
                .collect(Collectors.joining(","));
        storyReviewMapper.physicalDeleteNotInReviewers(story.getId(), version, inList);

        // 为新增评审人建记录；已在列表里的保持原样（不覆盖已提交的结果）
        for (String reviewer : reviewers) {
            if (storyReviewMapper.selectByStoryVersionReviewer(story.getId(), version, reviewer) == null) {
                StoryReviewDO reviewDO = new StoryReviewDO();
                reviewDO.setStory(story.getId());
                reviewDO.setVersion(version);
                reviewDO.setReviewer(reviewer);
                reviewDO.setResult("");
                storyReviewMapper.insert(reviewDO);
            }
        }

        // 需求进入「评审中」，并清空上一轮的已评审记录
        StoryDO updateObj = new StoryDO();
        updateObj.setId(story.getId());
        updateObj.setStatus(StoryStatusEnum.REVIEWING.getStatus());
        updateObj.setReviewedBy("");
        storyMapper.updateById(updateObj);

        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_STORY, story.getId(),
                ActionTypeEnum.SUBMIT_REVIEW, reqVO.getComment());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submitReview(StoryReviewSubmitReqVO reqVO) {
        StoryDO story = validateStoryExists(reqVO.getId());
        // 只有评审中的需求才能表决
        if (!StoryStatusEnum.REVIEWING.getStatus().equals(story.getStatus())) {
            throw exception(STORY_REVIEW_NOT_IN_REVIEWING);
        }
        if (!StoryReviewResultEnum.isValid(reqVO.getResult())) {
            throw exception(STORY_REVIEW_RESULT_INVALID, reqVO.getResult());
        }

        int version = story.getVersion() == null ? 1 : story.getVersion();
        String operator = currentAccount();

        // 必须是本版本的评审人，且尚未表决
        StoryReviewDO myReview = storyReviewMapper.selectByStoryVersionReviewer(story.getId(), version, operator);
        if (myReview == null) {
            throw exception(STORY_NOT_REVIEWER);
        }
        if (StringUtils.hasText(myReview.getResult())) {
            throw exception(STORY_ALREADY_REVIEWED);
        }

        // 记录本人表决
        StoryReviewDO updateReview = new StoryReviewDO();
        updateReview.setId(myReview.getId());
        updateReview.setResult(reqVO.getResult());
        updateReview.setReviewDate(LocalDateTime.now());
        storyReviewMapper.updateById(updateReview);

        // 判断是否所有人都评完了
        List<StoryReviewDO> allReviews = storyReviewMapper.selectListByStoryAndVersion(story.getId(), version);
        Map<String, String> resultMap = new LinkedHashMap<>();
        boolean allDone = true;
        for (StoryReviewDO item : allReviews) {
            String res = item.getReviewer().equals(operator) ? reqVO.getResult() : item.getResult();
            resultMap.put(item.getReviewer(), res);
            if (!StringUtils.hasText(res)) {
                allDone = false;
            }
        }

        StoryReviewResultEnum resultEnum = StoryReviewResultEnum.of(reqVO.getResult());
        String resultName = resultEnum != null ? resultEnum.getName() : reqVO.getResult();
        String reviewComment = "评审结果：" + resultName
                + (StringUtils.hasText(reqVO.getComment()) ? "；意见：" + reqVO.getComment() : "");

        if (!allDone) {
            // 还有人没评，只更新「已评审人」列表，状态不变
            StoryDO updateObj = new StoryDO();
            updateObj.setId(story.getId());
            updateObj.setReviewedBy(appendAccount(story.getReviewedBy(), operator));
            updateObj.setReviewedDate(LocalDateTime.now());
            storyMapper.updateById(updateObj);
            actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_STORY, story.getId(),
                    ActionTypeEnum.REVIEWED, reviewComment);
            return null;
        }

        // 全部评完 —— 聚合结果并流转状态
        String finalResult = aggregateReviewResult(resultMap);
        applyReviewResult(story, version, finalResult);

        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_STORY, story.getId(),
                ActionTypeEnum.REVIEWED, reviewComment);
        StoryReviewResultEnum finalEnum = StoryReviewResultEnum.of(finalResult);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_STORY, story.getId(),
                ActionTypeEnum.REVIEW_RESULT,
                "全部评审完成，聚合结果为「" + (finalEnum != null ? finalEnum.getName() : finalResult) + "」");
        return finalResult;
    }

    @Override
    public List<StoryReviewDO> getStoryReviewList(Long storyId, Integer version) {
        StoryDO story = validateStoryExists(storyId);
        int targetVersion = (version == null || version == 0) ? story.getVersion() : version;
        return storyReviewMapper.selectListByStoryAndVersion(storyId, targetVersion);
    }

    /**
     * 聚合多个评审人的结果。对应禅道 {@code getReviewResult()}。
     *
     * 禅道的 reviewRules 配置默认是 {@code allpass}（全员通过才算通过），
     * 另有 {@code halfpass}（过半即通过）。这里实现 allpass，并保留 halfpass 的扩展位。
     * 未通过时按「多数派优先、否则取最强反对意见」判定。
     */
    private String aggregateReviewResult(Map<String, String> reviewerResults) {
        int total = reviewerResults.size();
        if (total == 0) {
            return null;
        }
        int passCount = 0, clarifyCount = 0, revertCount = 0, rejectCount = 0;
        for (String result : reviewerResults.values()) {
            if (StoryReviewResultEnum.PASS.getResult().equals(result)) {
                passCount++;
            } else if (StoryReviewResultEnum.CLARIFY.getResult().equals(result)) {
                clarifyCount++;
            } else if (StoryReviewResultEnum.REVERT.getResult().equals(result)) {
                revertCount++;
            } else if (StoryReviewResultEnum.REJECT.getResult().equals(result)) {
                rejectCount++;
            }
        }

        // allpass：全员通过才算通过
        if (passCount == total) {
            return StoryReviewResultEnum.PASS.getResult();
        }

        // 未通过：先看是否形成多数派（floor(n/2)+1）
        int majority = total / 2 + 1;
        if (clarifyCount >= majority) {
            return StoryReviewResultEnum.CLARIFY.getResult();
        }
        if (revertCount >= majority) {
            return StoryReviewResultEnum.REVERT.getResult();
        }
        if (rejectCount >= majority) {
            return StoryReviewResultEnum.REJECT.getResult();
        }
        // 没有多数派时，只要有任意一人反对，就取该反对意见（优先级 clarify > revert > reject）
        if (clarifyCount > 0) {
            return StoryReviewResultEnum.CLARIFY.getResult();
        }
        if (revertCount > 0) {
            return StoryReviewResultEnum.REVERT.getResult();
        }
        if (rejectCount > 0) {
            return StoryReviewResultEnum.REJECT.getResult();
        }
        return null;
    }

    /**
     * 按聚合结果流转需求状态。对应禅道 {@code setStatusByReviewResult()}。
     *
     * <ul>
     *   <li>pass    -> active</li>
     *   <li>clarify -> draft（若已变更过则 changing），清空 reviewedBy 等待重新评审</li>
     *   <li>revert  -> active，且 version-1，同时物理删除当前版本的快照与评审记录</li>
     *   <li>reject  -> closed，指派给 closed</li>
     * </ul>
     */
    private void applyReviewResult(StoryDO oldStory, int version, String result) {
        String operator = currentAccount();
        StoryDO updateObj = new StoryDO();
        updateObj.setId(oldStory.getId());
        updateObj.setReviewedBy(appendAccount(oldStory.getReviewedBy(), operator));
        updateObj.setReviewedDate(LocalDateTime.now());

        if (StoryReviewResultEnum.PASS.getResult().equals(result)) {
            updateObj.setStatus(StoryStatusEnum.ACTIVE.getStatus());

        } else if (StoryReviewResultEnum.CLARIFY.getResult().equals(result)) {
            // 有待明确：打回。变更过的需求回到 changing，新需求回到 draft
            boolean changed = version > 1;
            updateObj.setStatus(changed
                    ? StoryStatusEnum.CHANGING.getStatus() : StoryStatusEnum.DRAFT.getStatus());
            updateObj.setReviewedBy(""); // 需要重新发起评审

        } else if (StoryReviewResultEnum.REVERT.getResult().equals(result)) {
            // 撤销变更：版本回滚到上一版，并删掉本版的快照与评审记录
            if (version <= 1) {
                throw exception(STORY_REVIEW_NO_PREVIOUS_VERSION);
            }
            int prevVersion = version - 1;
            StorySpecDO prevSpec = storySpecMapper.selectByStoryAndVersion(oldStory.getId(), prevVersion);
            updateObj.setStatus(StoryStatusEnum.ACTIVE.getStatus());
            updateObj.setVersion(prevVersion);
            updateObj.setReviewedBy("");
            if (prevSpec != null) {
                updateObj.setTitle(prevSpec.getTitle());
            }
            storyMapper.updateById(updateObj);

            // 必须物理删除：表上有 (story, version) 唯一键，
            // 若走逻辑删除，之后再变更到同一版本号会撞唯一键。
            storySpecMapper.physicalDeleteByStoryAndVersion(oldStory.getId(), version);
            storyReviewMapper.physicalDeleteByStoryAndVersion(oldStory.getId(), version);
            return;

        } else if (StoryReviewResultEnum.REJECT.getResult().equals(result)) {
            // 拒绝：直接关闭需求
            updateObj.setStatus(StoryStatusEnum.CLOSED.getStatus());
            updateObj.setClosedBy(operator);
            updateObj.setClosedDate(LocalDateTime.now());
            updateObj.setClosedReason("willnotdo");
            updateObj.setAssignedTo("closed");
            updateObj.setAssignedDate(LocalDateTime.now());
        }

        storyMapper.updateById(updateObj);
    }

    /**
     * 取当前登录用户的「账号」。
     *
     * 为什么不直接用 {@code SecurityFrameworkUtils.getLoginUserNickname()}：
     * 禅道侧所有人员引用（openedBy / assignedTo / 评审人）存的都是账号，
     * 而 yudao 的 LoginUser 上下文里只有昵称（{@code LoginUser.INFO_KEY_NICKNAME}），
     * 没有账号；昵称还可能重名，不能当人员标识用。
     * 所以这里跨模块调用 system 的 AdminUserApi，把登录用户 ID 解析成账号。
     */
    private String currentAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : "";
    }

    /**
     * 把账号追加到逗号分隔的已评审人列表里（去重）
     */
    private String appendAccount(String reviewedBy, String account) {
        Set<String> accounts = new LinkedHashSet<>();
        if (StringUtils.hasText(reviewedBy)) {
            for (String item : reviewedBy.split(",")) {
                if (StringUtils.hasText(item)) {
                    accounts.add(item.trim());
                }
            }
        }
        accounts.add(account);
        return String.join(",", accounts);
    }

    // ==================== 内部：快照读写 ====================

    /**
     * 追加一条版本快照。对应禅道 tao.php doCreateSpec()
     */
    private void insertStorySpec(Long storyId, Integer version, String title, String spec, String verify) {
        StorySpecDO specDO = new StorySpecDO();
        specDO.setStory(storyId);
        specDO.setVersion(version);
        specDO.setTitle(title);
        specDO.setSpec(spec);
        specDO.setVerify(verify);
        storySpecMapper.insert(specDO);
    }

    /**
     * 原地刷新指定版本的快照。对应禅道 tao.php doUpdateSpec()
     *
     * 禅道在这里做了一个"内容没变就跳过"的优化，避免无意义的写库与
     * update_time 抖动。这里保持同样的行为。
     */
    private void updateStorySpecInPlace(Long storyId, Integer version, String title, String spec,
                                       String verify) {
        StorySpecDO oldSpec = storySpecMapper.selectByStoryAndVersion(storyId, version);
        if (oldSpec == null) {
            // 历史数据没有快照（例如从旧库迁移过来的），补一条，保证版本链完整
            insertStorySpec(storyId, version, title, spec, verify);
            return;
        }
        if (Objects.equals(oldSpec.getTitle(), title)
                && Objects.equals(oldSpec.getSpec(), spec)
                && Objects.equals(oldSpec.getVerify(), verify)) {
            return; // 内容未变，不必写库
        }
        StorySpecDO updateObj = new StorySpecDO();
        updateObj.setId(oldSpec.getId());
        updateObj.setTitle(title);
        updateObj.setSpec(spec);
        updateObj.setVerify(verify);
        storySpecMapper.updateById(updateObj);
    }

    // ==================== 需求分层（业务需求 / 用户需求 / 研发需求） ====================

    /**
     * 需求分层类型字典。
     *
     * <p>禅道 {@code module/story/lang/zh-cn.php}：
     * <pre>
     *   if($config->enableER) $lang->story->typeList['epic']        = $lang->ERCommon;  // 业务需求
     *   if($config->URAndSR)  $lang->story->typeList['requirement'] = $lang->URCommon;  // 用户需求
     *   $lang->story->typeList['story'] = $lang->SRCommon;                              // 研发需求
     * </pre>
     * 那两个开关只是界面裁剪（开源版默认关掉业务需求/用户需求），数据模型上三层一直都在，
     * 所以本实现不做开关：三层都能建、都能查。
     */
    @Override
    public List<StoryTypeRespVO> getTypeList() {
        List<StoryTypeRespVO> list = new ArrayList<>();
        for (StoryTypeEnum item : StoryTypeEnum.values()) {
            StoryTypeRespVO vo = new StoryTypeRespVO();
            vo.setType(item.getType());
            vo.setName(item.getName());
            vo.setLevel(item.getLevel());
            vo.setParentTypes(String.join(",", StoryTypeEnum.parentTypesOf(item.getType())));
            vo.setChildType(StoryTypeEnum.childTypeOf(item.getType()));
            list.add(vo);
        }
        return list;
    }

    /**
     * 某产品下各需求分层类型的数量。
     *
     * <p>用 {@code GROUP BY type} 一次查出来，没有数据的类型补 0 —— 前端页签要显示 "研发需求(0)"，
     * 不能因为缺 key 就渲染成 undefined。历史数据 {@code type} 为空的按研发需求计数（与
     * 禅道的默认值一致）。
     */
    @Override
    public Map<String, Long> getTypeSummary(Long product) {
        Map<String, Long> summary = new LinkedHashMap<>();
        for (StoryTypeEnum item : StoryTypeEnum.values()) {
            summary.put(item.getType(), 0L);
        }
        if (product == null) {
            return summary;
        }
        for (Map<String, Object> row : storyMapper.countGroupByType(product)) {
            Object type = row.get("type");
            Object cnt = row.get("cnt");
            // 列名大小写在不同驱动/别名下可能是 type/TYPE、cnt/CNT，这里都兼容一下
            if (type == null) {
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    if ("type".equalsIgnoreCase(entry.getKey())) {
                        type = entry.getValue();
                    } else if ("cnt".equalsIgnoreCase(entry.getKey())) {
                        cnt = entry.getValue();
                    }
                }
            }
            String key = type == null || !StringUtils.hasText(String.valueOf(type))
                    ? StoryTypeEnum.STORY.getType() : String.valueOf(type);
            if (!summary.containsKey(key)) {
                // 未知类型（例如 IPD 版的需求类型）也算进总数里，不丢掉
                summary.put(key, 0L);
            }
            summary.put(key, summary.get(key) + (cnt == null ? 0L : Long.parseLong(String.valueOf(cnt))));
        }
        return summary;
    }

    /**
     * 需求分层树：业务需求 → 用户需求 → 研发需求。
     *
     * <p>组装方式与禅道需求池的「分层视图」一致：按 {@code parent} 挂，
     * 顶层是 {@code parent = 0} 的需求。父需求的 estimate 是子需求之和（refreshParent 维护），
     * 这里直接带出来给前端展示。
     *
     * @param product 产品编号（必填）
     * @param rootId  只看某一棵子树，传空看整个产品的需求森林
     */
    @Override
    public List<StoryTreeNodeRespVO> getTypeTree(Long product, Long rootId) {
        List<StoryDO> all = storyMapper.selectListByProduct(product);
        Map<Long, StoryTreeNodeRespVO> nodeMap = new LinkedHashMap<>();
        for (StoryDO story : all) {
            nodeMap.put(story.getId(), toTreeNode(story));
        }
        // 按 parent 挂孩子；父不在结果集里（例如按子树查询时父被过滤掉了）的当顶层处理，
        // 否则这些需求会在树上"消失"
        List<StoryTreeNodeRespVO> roots = new ArrayList<>();
        for (StoryDO story : all) {
            StoryTreeNodeRespVO node = nodeMap.get(story.getId());
            Long parentId = story.getParent();
            StoryTreeNodeRespVO parent = parentId == null ? null : nodeMap.get(parentId);
            if (parent != null && !Objects.equals(parentId, story.getId())) {
                parent.getChildren().add(node);
                parent.setChildCount(parent.getChildren().size());
            } else {
                roots.add(node);
            }
        }
        if (rootId != null) {
            StoryTreeNodeRespVO root = nodeMap.get(rootId);
            return root == null ? new ArrayList<>() : List.of(root);
        }
        // 每层都按 (层级, 编号) 升序 —— 父需求排在自己的子需求上面，同级按编号，
        // 与禅道分层视图的顺序一致（query 是按 id 倒序取出来的，不排就会反着显示）
        sortChildren(roots);
        return roots;
    }

    /** 递归排序需求分层树的每一层 */
    private void sortChildren(List<StoryTreeNodeRespVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator
                .comparingInt((StoryTreeNodeRespVO node) -> StoryTypeEnum.levelOf(node.getType()))
                .thenComparingLong(node -> node.getId() == null ? 0L : node.getId()));
        for (StoryTreeNodeRespVO node : nodes) {
            sortChildren(node.getChildren());
        }
    }

    private StoryTreeNodeRespVO toTreeNode(StoryDO story) {
        StoryTreeNodeRespVO node = new StoryTreeNodeRespVO();
        node.setId(story.getId());
        node.setParent(story.getParent() == null ? 0L : story.getParent());
        node.setTitle(story.getTitle());
        node.setType(StringUtils.hasText(story.getType()) ? story.getType() : StoryTypeEnum.STORY.getType());
        node.setTypeName(StoryTypeEnum.nameOf(node.getType()));
        node.setGrade(story.getGrade());
        node.setStatus(story.getStatus());
        node.setStage(story.getStage());
        node.setPri(story.getPri());
        node.setEstimate(story.getEstimate());
        node.setChildCount(0);
        node.setChildren(new ArrayList<>());
        return node;
    }

    /**
     * 校验需求分层类型是否合法
     */
    private void validateStoryType(String type) {
        if (!StoryTypeEnum.isValid(type)) {
            throw exception(STORY_TYPE_INVALID, type);
        }
    }

    /**
     * 校验「子需求能否挂在这个父需求下」—— 父的层级不能低于子，规则见 {@link StoryTypeEnum}。
     *
     * <p>历史数据里 {@code zt_story.type} 可能为空（早期版本的默认值是 story），
     * 这里按研发需求处理。
     */
    private void validateParentType(String childType, StoryDO parent) {
        String parentType = StringUtils.hasText(parent.getType())
                ? parent.getType() : StoryTypeEnum.STORY.getType();
        if (!StoryTypeEnum.isParentTypeAllowed(childType, parentType)) {
            throw exception(STORY_PARENT_TYPE_NOT_ALLOWED,
                    StoryTypeEnum.nameOf(childType), StoryTypeEnum.nameOf(parentType));
        }
    }

}
