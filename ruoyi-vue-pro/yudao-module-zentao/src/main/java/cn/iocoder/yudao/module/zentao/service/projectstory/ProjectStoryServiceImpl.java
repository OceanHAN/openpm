package cn.iocoder.yudao.module.zentao.service.projectstory;

import cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo.ProjectProductLinkReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo.ProjectProductRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo.ProjectStoryRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.branch.BranchDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.plan.PlanDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.projectstory.ProjectProductDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.projectstory.ProjectStoryDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.plan.PlanMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.projectstory.ProjectProductMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.projectstory.ProjectStoryMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.story.StoryMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.story.StoryStatusEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import cn.iocoder.yudao.module.zentao.service.branch.BranchService;
import cn.iocoder.yudao.module.zentao.service.product.ProductService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 项目/执行需求范围 Service 实现
 *
 * <p>逐条对齐禅道 {@code module/execution/model.php} 的 linkStory / unlinkStory，
 * 以及 {@code zt_projectproduct} 的关联逻辑。
 */
@Slf4j
@Service
public class ProjectStoryServiceImpl implements ProjectStoryService {

    private static final String OBJECT_TYPE_PROJECT = "project";
    private static final String OBJECT_TYPE_STORY = "story";

    /**
     * 不能纳入项目范围的需求状态（禅道 {@code $notAllowedStatus = 'draft,reviewing,closed'}）
     */
    private static final List<String> NOT_ALLOWED_STATUS = List.of(
            StoryStatusEnum.DRAFT.getStatus(),
            StoryStatusEnum.REVIEWING.getStatus(),
            StoryStatusEnum.CLOSED.getStatus());

    @Resource
    private ProjectStoryMapper projectStoryMapper;

    @Resource
    private ProjectProductMapper projectProductMapper;

    @Resource
    private StoryMapper storyMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private PlanMapper planMapper;

    @Resource
    private ProductService productService;

    @Resource
    private BranchService branchService;

    @Resource
    private ActionService actionService;

    // ==================== 项目关联产品 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long linkProduct(ProjectProductLinkReqVO reqVO) {
        validateProjectOrExecution(reqVO.getProject());
        ProductDO product = productService.validateProductExists(reqVO.getProduct());
        Long branch = reqVO.getBranch() == null ? 0L : reqVO.getBranch();

        ProjectProductDO exists = projectProductMapper.selectByProjectProduct(
                reqVO.getProject(), reqVO.getProduct(), branch);
        if (exists != null) {
            throw exception(PROJECT_PRODUCT_DUPLICATE, product.getName());
        }

        ProjectProductDO relation = new ProjectProductDO();
        relation.setProject(reqVO.getProject());
        relation.setProduct(reqVO.getProduct());
        relation.setBranch(branch);
        relation.setPlan(joinIds(reqVO.getPlans()));
        relation.setRoadmap("");
        projectProductMapper.insert(relation);

        actionService.recordAction(OBJECT_TYPE_PROJECT, reqVO.getProject(), ActionTypeEnum.EDITED,
                "关联产品：" + product.getName());
        return relation.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkProduct(Long project, Long product, Long branch) {
        validateProjectOrExecution(project);
        Long finalBranch = branch == null ? 0L : branch;
        ProjectProductDO relation = projectProductMapper.selectByProjectProduct(project, product, finalBranch);
        if (relation == null) {
            throw exception(PROJECT_PRODUCT_NOT_EXISTS, product);
        }
        // 产品下还有需求在项目范围内时不能解除关联（否则需求会失去产品依据）
        Long storyCount = projectStoryMapper.countByProjectAndProduct(project, product);
        if (storyCount != null && storyCount > 0) {
            throw exception(PROJECT_PRODUCT_HAS_STORIES, storyCount);
        }
        projectProductMapper.deleteById(relation.getId());
        actionService.recordAction(OBJECT_TYPE_PROJECT, project, ActionTypeEnum.EDITED,
                "解除产品关联：#" + product);
    }

    @Override
    public List<ProjectProductRespVO> getLinkedProducts(Long project) {
        List<ProjectProductRespVO> result = new ArrayList<>();
        for (ProjectProductDO relation : projectProductMapper.selectListByProject(project)) {
            ProjectProductRespVO vo = new ProjectProductRespVO();
            vo.setId(relation.getId());
            vo.setProject(relation.getProject());
            vo.setProduct(relation.getProduct());
            vo.setBranch(relation.getBranch());
            vo.setPlan(relation.getPlan());
            vo.setPlanNames(planNames(relation.getPlan()));
            vo.setStoryCount(projectStoryMapper.countByProjectAndProduct(project, relation.getProduct()));
            ProductDO product = productService.getProduct(relation.getProduct());
            if (product != null) {
                vo.setProductName(product.getName());
                vo.setProductType(product.getType());
            }
            vo.setBranchName(branchName(relation.getProduct(), relation.getBranch()));
            result.add(vo);
        }
        return result;
    }

    // ==================== 关联 / 移除需求 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> linkStories(Long project, List<Long> storyIds) {
        ProjectDO projectRow = validateProjectOrExecution(project);
        List<ProjectProductDO> linkedProducts = projectProductMapper.selectListByProject(project);

        Set<Long> linked = new LinkedHashSet<>();
        int lastOrder = 0;
        for (ProjectStoryDO relation : projectStoryMapper.selectListByProject(project)) {
            linked.add(relation.getStory());
            lastOrder = Math.max(lastOrder, relation.getOrder() == null ? 0 : relation.getOrder());
        }

        List<Long> added = new ArrayList<>();
        for (Long storyId : storyIds) {
            if (storyId == null || linked.contains(storyId)) {
                continue;
            }
            StoryDO story = storyMapper.selectById(storyId);
            if (story == null) {
                continue;
            }
            // 状态不允许的需求直接跳过（禅道也是 skip，不报错）
            if (NOT_ALLOWED_STATUS.contains(story.getStatus())) {
                actionService.recordAction(OBJECT_TYPE_STORY, storyId, ActionTypeEnum.EDITED,
                        "尝试纳入项目范围被跳过（状态：" + StoryStatusEnum.nameOf(story.getStatus()) + "）");
                continue;
            }
            // 需求所属产品必须已经关联到项目
            if (linkedProducts.stream().noneMatch(p -> Objects.equals(p.getProduct(), story.getProduct()))) {
                throw exception(PROJECT_STORY_PRODUCT_NOT_LINKED);
            }

            ProjectStoryDO relation = new ProjectStoryDO();
            relation.setProject(project);
            relation.setProduct(story.getProduct());
            relation.setBranch(story.getBranch() == null ? 0L : story.getBranch());
            relation.setStory(storyId);
            // 记录「关联时」的版本，而不是之后会变的当前版本
            relation.setVersion(story.getVersion() == null ? 1 : story.getVersion());
            relation.setOrder(++lastOrder);
            projectStoryMapper.insert(relation);
            linked.add(storyId);
            added.add(storyId);

            // 禅道按执行类型区分动作：项目 → linked2project，执行 → linked2execution
            String actionName = "project".equals(projectRow.getType()) ? "关联到项目" : "关联到执行";
            actionService.recordAction(OBJECT_TYPE_STORY, storyId, ActionTypeEnum.EDITED,
                    actionName + " #" + project);
        }
        return added;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkStory(Long project, Long storyId) {
        ProjectDO projectRow = validateProjectOrExecution(project);
        ProjectStoryDO relation = projectStoryMapper.selectOne(
                new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<ProjectStoryDO>()
                        .eq(ProjectStoryDO::getProject, project)
                        .eq(ProjectStoryDO::getStory, storyId)
                        .last("LIMIT 1"));
        if (relation == null) {
            throw exception(PROJECT_STORY_RELATION_NOT_EXISTS);
        }

        // 项目上的需求：若子执行已经关联，则不允许从项目移除（禅道 notAllowedUnlinkStory）
        if ("project".equals(projectRow.getType())) {
            // 「子执行」是 project 列指向本项目、且 type 属于执行的记录（项目与执行共用一张表，
            // 不能用 selectListByParent：那个方法按 parent 找子项目）
            List<Long> childIds = projectMapper.selectExecutionListByProject(project).stream()
                    .map(ProjectDO::getId).toList();
            if (!childIds.isEmpty()) {
                Long count = projectStoryMapper.countByStoryAndProjects(storyId, childIds);
                if (count != null && count > 0) {
                    throw exception(PROJECT_STORY_HAS_CHILD_EXECUTION);
                }
            }
        }

        projectStoryMapper.deleteById(relation.getId());
        resequence(project);
        actionService.recordAction(OBJECT_TYPE_STORY, storyId, ActionTypeEnum.EDITED,
                "从项目/执行 #" + project + " 移出范围");
    }

    // ==================== 查询 ====================

    @Override
    public List<ProjectStoryRespVO> getProjectStories(Long project) {
        List<ProjectStoryDO> relations = projectStoryMapper.selectListByProject(project);
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Long> storyIds = relations.stream().map(ProjectStoryDO::getStory).toList();
        Map<Long, StoryDO> storyMap = new HashMap<>();
        for (StoryDO story : storyMapper.selectBatchIds(storyIds)) {
            storyMap.put(story.getId(), story);
        }
        // 一次性算出每条需求还被哪些项目/执行关联
        Map<Long, List<Long>> relatedMap = new HashMap<>();
        for (ProjectStoryDO other : projectStoryMapper.selectListByStories(storyIds)) {
            relatedMap.computeIfAbsent(other.getStory(), k -> new ArrayList<>()).add(other.getProject());
        }
        Map<Long, String> productNames = new HashMap<>();
        for (ProjectStoryDO relation : relations) {
            productNames.computeIfAbsent(relation.getProduct(), id -> {
                ProductDO product = productService.getProduct(id);
                return product != null ? product.getName() : null;
            });
        }

        List<ProjectStoryRespVO> result = new ArrayList<>();
        for (ProjectStoryDO relation : relations) {
            StoryDO story = storyMap.get(relation.getStory());
            ProjectStoryRespVO vo = new ProjectStoryRespVO();
            vo.setId(relation.getId());
            vo.setProject(relation.getProject());
            vo.setStory(relation.getStory());
            vo.setProduct(relation.getProduct());
            vo.setBranch(relation.getBranch());
            vo.setLinkVersion(relation.getVersion());
            vo.setOrder(relation.getOrder());
            vo.setProductName(productNames.get(relation.getProduct()));
            vo.setRelatedProjects(relatedMap.getOrDefault(relation.getStory(), List.of()));
            if (story != null) {
                vo.setTitle(story.getTitle());
                vo.setStatus(story.getStatus());
                vo.setStage(story.getStage());
                vo.setPri(story.getPri());
                vo.setEstimate(story.getEstimate());
                vo.setAssignedTo(story.getAssignedTo());
                vo.setOpenedDate(story.getOpenedDate());
                vo.setCurrentVersion(story.getVersion());
                // 需求后续发生正式变更（version+1）时，项目看到的仍是当初规划的版本
                vo.setVersionChanged(story.getVersion() != null && relation.getVersion() != null
                        && story.getVersion() > relation.getVersion());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<StoryDO> getUnlinkedStories(Long project) {
        validateProjectOrExecution(project);
        List<ProjectProductDO> linkedProducts = projectProductMapper.selectListByProject(project);
        if (linkedProducts.isEmpty()) {
            return List.of();
        }
        Set<Long> linked = new LinkedHashSet<>();
        projectStoryMapper.selectListByProject(project).forEach(r -> linked.add(r.getStory()));

        List<StoryDO> result = new ArrayList<>();
        for (ProjectProductDO relation : linkedProducts) {
            for (StoryDO story : storyMapper.selectListByProduct(relation.getProduct())) {
                if (linked.contains(story.getId()) || NOT_ALLOWED_STATUS.contains(story.getStatus())) {
                    continue;
                }
                // 关系上指定了计划时，只吃这些计划下的需求（story.plan 是逗号列表）
                if (StringUtils.hasText(relation.getPlan()) && !intersectsPlan(story.getPlan(), relation.getPlan())) {
                    continue;
                }
                // 关系上指定了分支时，只吃该分支（或主干）的需求
                if (relation.getBranch() != null && relation.getBranch() != 0
                        && story.getBranch() != null && story.getBranch() != 0
                        && !relation.getBranch().equals(story.getBranch())) {
                    continue;
                }
                result.add(story);
            }
        }
        return result;
    }

    @Override
    public List<Long> getRelatedProjects(Long storyId) {
        return projectStoryMapper.selectListByStory(storyId).stream()
                .map(ProjectStoryDO::getProject).distinct().toList();
    }

    @Override
    public Long countStories(Long project) {
        return projectStoryMapper.selectCount(
                new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<ProjectStoryDO>()
                        .eq(ProjectStoryDO::getProject, project));
    }

    // ==================== 内部 ====================

    /**
     * 移除后把剩余关系重新编号 1..n（禅道 unlinkStory 里也会做这件事）
     */
    private void resequence(Long project) {
        int order = 1;
        for (ProjectStoryDO relation : projectStoryMapper.selectListByProject(project)) {
            if (relation.getOrder() == null || relation.getOrder() != order) {
                ProjectStoryDO updateObj = new ProjectStoryDO();
                updateObj.setId(relation.getId());
                updateObj.setOrder(order);
                projectStoryMapper.updateById(updateObj);
            }
            order++;
        }
    }

    /**
     * 校验目标存在，且确实是「项目」或「执行」（两类都允许挂需求）
     */
    private ProjectDO validateProjectOrExecution(Long id) {
        ProjectDO row = id == null ? null : projectMapper.selectById(id);
        if (row == null) {
            throw exception(PROJECT_STORY_TARGET_NOT_EXISTS, id);
        }
        return row;
    }

    private boolean intersectsPlan(String storyPlans, String relationPlans) {
        if (!StringUtils.hasText(storyPlans)) {
            return false;
        }
        Set<String> storySet = new LinkedHashSet<>(List.of(storyPlans.split(",")));
        for (String plan : relationPlans.split(",")) {
            if (storySet.contains(plan.trim())) {
                return true;
            }
        }
        return false;
    }

    private List<String> planNames(String planIds) {
        if (!StringUtils.hasText(planIds)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String id : planIds.split(",")) {
            if (!StringUtils.hasText(id)) {
                continue;
            }
            PlanDO plan = planMapper.selectById(Long.valueOf(id.trim()));
            names.add(plan != null ? plan.getTitle() : "#" + id);
        }
        return names;
    }

    private String branchName(Long product, Long branch) {
        if (branch == null || branch == 0) {
            return "主干";
        }
        for (BranchDO item : branchService.getBranchListByProduct(product, null)) {
            if (branch.equals(item.getId())) {
                return item.getName();
            }
        }
        return "#" + branch;
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return ids.stream().filter(Objects::nonNull).distinct()
                .map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }

}
