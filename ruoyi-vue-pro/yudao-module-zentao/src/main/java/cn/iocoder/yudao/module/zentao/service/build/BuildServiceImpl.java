package cn.iocoder.yudao.module.zentao.service.build;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.build.vo.BuildPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.build.vo.BuildSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.branch.BranchDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.build.BuildDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.build.BuildMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.bug.BugMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.story.StoryMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.bug.BugResolutionEnum;
import cn.iocoder.yudao.module.zentao.enums.bug.BugStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.product.ProductTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.project.ProjectStatusEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import cn.iocoder.yudao.module.zentao.service.release.ReleaseService;
import cn.iocoder.yudao.module.zentao.service.branch.BranchService;
import cn.iocoder.yudao.module.zentao.service.product.ProductService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 构建 Service 实现
 *
 * <p>逐条对齐禅道 {@code module/build/model.php}：create / update / delete /
 * linkStory / unlinkStory / linkBug / unlinkBug / updateLinkedBug / joinChildBuilds。
 */
@Slf4j
@Service
public class BuildServiceImpl implements BuildService {

    private static final String OBJECT_TYPE_BUILD = "build";
    private static final String OBJECT_TYPE_STORY = "story";
    private static final String OBJECT_TYPE_BUG = "bug";

    @Resource
    private BuildMapper buildMapper;

    @Resource
    private StoryMapper storyMapper;

    @Resource
    private BugMapper bugMapper;

    @Resource
    private ReleaseService releaseService;

    @Resource
    private ProductService productService;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private BranchService branchService;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ==================== 写：创建 / 修改 / 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBuild(BuildSaveReqVO createReqVO) {
        boolean integrated = Boolean.TRUE.equals(createReqVO.getIntegrated());
        List<BuildDO> children = validateIntegratedChildren(integrated, createReqVO.getBuilds());

        // 集成构建：execution 固定 0，branch 取子构建分支的并集，也不需要校验产品分支
        Long execution = integrated ? 0L : validateExecution(createReqVO.getExecution());
        String branch = integrated
                ? unionBranches(children)
                : buildBranch(createReqVO.getProduct(), createReqVO.getBranches());
        Long product = integrated
                ? (children.isEmpty() ? createReqVO.getProduct() : children.get(0).getProduct())
                : validateProduct(createReqVO.getProduct());

        if (buildMapper.selectByName(product, branch, createReqVO.getName(), null) != null) {
            throw exception(BUILD_NAME_DUPLICATE, createReqVO.getName());
        }

        BuildDO build = new BuildDO();
        build.setProject(createReqVO.getProject() != null ? createReqVO.getProject() : projectOfExecution(execution));
        build.setProduct(product);
        build.setBranch(branch);
        build.setExecution(execution);
        build.setBuilds(joinIds(createReqVO.getBuilds()));
        build.setName(createReqVO.getName());
        build.setDate(createReqVO.getDate());
        build.setBuilder(createReqVO.getBuilder());
        build.setScmPath(createReqVO.getScmPath());
        build.setFilePath(createReqVO.getFilePath());
        build.setDesc(createReqVO.getDesc());
        build.setSystem(0L);
        build.setArtifactRepoID(0L);
        build.setStories("");
        build.setBugs("");
        build.setCreatedBy(currentAccount());
        build.setCreatedDate(LocalDateTime.now());
        buildMapper.insert(build);

        actionService.recordAction(OBJECT_TYPE_BUILD, build.getId(), ActionTypeEnum.CREATED,
                "创建" + (integrated ? "集成构建：" : "构建：") + build.getName());
        return build.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBuild(BuildSaveReqVO updateReqVO) {
        BuildDO oldBuild = validateBuildExists(updateReqVO.getId());
        boolean integrated = Boolean.TRUE.equals(updateReqVO.getIntegrated());
        List<BuildDO> children = validateIntegratedChildren(integrated, updateReqVO.getBuilds());

        // 被集成构建/发布引用的构建，不能改「产品」「执行」「子构建」（禅道 edit 页的 notice）
        if (isChildBuild(oldBuild.getId())) {
            if (updateReqVO.getProduct() != null && !updateReqVO.getProduct().equals(oldBuild.getProduct())) {
                throw exception(BUILD_CHILD_CANNOT_CHANGE, "所属产品");
            }
            if (updateReqVO.getExecution() != null && !updateReqVO.getExecution().equals(oldBuild.getExecution())) {
                throw exception(BUILD_CHILD_CANNOT_CHANGE, "所属执行");
            }
        }

        Long execution = integrated ? 0L : validateExecution(updateReqVO.getExecution());
        String branch = integrated
                ? unionBranches(children)
                : buildBranch(oldBuild.getProduct(), updateReqVO.getBranches());

        if (buildMapper.selectByName(oldBuild.getProduct(), branch, updateReqVO.getName(), oldBuild.getId()) != null) {
            throw exception(BUILD_NAME_DUPLICATE, updateReqVO.getName());
        }

        BuildDO updateObj = new BuildDO();
        updateObj.setId(oldBuild.getId());
        updateObj.setName(updateReqVO.getName());
        updateObj.setBranch(branch);
        updateObj.setExecution(execution);
        updateObj.setBuilds(joinIds(updateReqVO.getBuilds()));
        updateObj.setDate(updateReqVO.getDate());
        updateObj.setBuilder(updateReqVO.getBuilder());
        updateObj.setScmPath(updateReqVO.getScmPath());
        updateObj.setFilePath(updateReqVO.getFilePath());
        updateObj.setDesc(updateReqVO.getDesc());
        if (updateReqVO.getProject() != null) {
            updateObj.setProject(updateReqVO.getProject());
        }
        buildMapper.updateById(updateObj);

        BuildDO newBuild = buildMapper.selectById(oldBuild.getId());
        actionService.recordActionWithChanges(OBJECT_TYPE_BUILD, oldBuild.getId(),
                ActionTypeEnum.EDITED, null, oldBuild, newBuild);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBuild(Long id) {
        BuildDO build = validateBuildExists(id);
        buildMapper.deleteById(id);
        actionService.recordAction(OBJECT_TYPE_BUILD, id, ActionTypeEnum.DELETED,
                "删除构建：" + build.getName());
    }

    // ==================== 读 ====================

    @Override
    public BuildDO getBuild(Long id) {
        BuildDO build = validateBuildExists(id);
        // 集成构建：把子构建的 stories/bugs 并进来（禅道 joinChildBuilds）
        if (isIntegrated(build)) {
            Set<String> stories = new LinkedHashSet<>(splitIds(build.getStories()));
            Set<String> bugs = new LinkedHashSet<>(splitIds(build.getBugs()));
            for (BuildDO child : childBuilds(build)) {
                stories.addAll(splitIds(child.getStories()));
                bugs.addAll(splitIds(child.getBugs()));
            }
            build.setStories(String.join(",", stories));
            build.setBugs(String.join(",", bugs));
        }
        return build;
    }

    @Override
    public BuildDO validateBuildExists(Long id) {
        BuildDO build = id == null ? null : buildMapper.selectById(id);
        if (build == null) {
            throw exception(BUILD_NOT_EXISTS, id);
        }
        return build;
    }

    @Override
    public PageResult<BuildDO> getBuildPage(BuildPageReqVO reqVO) {
        return buildMapper.selectPage(reqVO);
    }

    @Override
    public List<BuildDO> getBuildListByProduct(Long product, Long branch) {
        return buildMapper.selectListByProduct(product, branch);
    }

    @Override
    public List<BuildDO> getBuildListByExecution(Long execution) {
        return buildMapper.selectListByExecution(execution);
    }

    @Override
    public List<StoryDO> getBuildStories(Long buildId) {
        BuildDO build = getBuild(buildId);
        List<Long> ids = splitIds(build.getStories()).stream().map(Long::valueOf).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        // 保持构建里记录的先后顺序，方便前端直接展示
        Map<Long, StoryDO> map = new java.util.HashMap<>();
        for (StoryDO story : storyMapper.selectBatchIds(ids)) {
            map.put(story.getId(), story);
        }
        List<StoryDO> result = new ArrayList<>();
        for (Long id : ids) {
            if (map.containsKey(id)) {
                result.add(map.get(id));
            }
        }
        return result;
    }

    @Override
    public List<StoryDO> getUnlinkedStories(Long buildId) {
        BuildDO build = validateBuildExists(buildId);
        Set<String> linked = new LinkedHashSet<>(splitIds(build.getStories()));
        return storyMapper.selectListByProduct(build.getProduct()).stream()
                .filter(story -> !linked.contains(String.valueOf(story.getId())))
                // 已关闭的需求不再参与构建（禅道列表也默认过滤）
                .filter(story -> !"closed".equals(story.getStatus()))
                .toList();
    }

    @Override
    public List<BugDO> getBuildBugs(Long buildId) {
        BuildDO build = getBuild(buildId);
        List<Long> ids = splitIds(build.getBugs()).stream().map(Long::valueOf).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, BugDO> map = new java.util.HashMap<>();
        for (BugDO bug : bugMapper.selectBatchIds(ids)) {
            map.put(bug.getId(), bug);
        }
        List<BugDO> result = new ArrayList<>();
        for (Long id : ids) {
            if (map.containsKey(id)) {
                result.add(map.get(id));
            }
        }
        return result;
    }

    @Override
    public List<BugDO> getUnlinkedBugs(Long buildId) {
        BuildDO build = validateBuildExists(buildId);
        Set<String> linked = new LinkedHashSet<>(splitIds(build.getBugs()));
        return bugMapper.selectListByProduct(build.getProduct()).stream()
                .filter(bug -> !linked.contains(String.valueOf(bug.getId())))
                .toList();
    }

    @Override
    public boolean isChildBuild(Long buildId) {
        // 发布侧的引用检查（zt_release.build）等 release 模块接入后在这里补
        return buildMapper.countAsChildBuild(buildId) > 0;
    }

    // ==================== 写：关联需求 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkStories(Long buildId, List<Long> storyIds) {
        BuildDO build = validateBuildExists(buildId);
        Set<String> current = new LinkedHashSet<>(splitIds(build.getStories()));
        List<Long> added = new ArrayList<>();
        for (Long storyId : storyIds) {
            if (storyId == null || current.contains(String.valueOf(storyId))) {
                continue;
            }
            current.add(String.valueOf(storyId));
            added.add(storyId);
            actionService.recordAction(OBJECT_TYPE_STORY, storyId, ActionTypeEnum.EDITED,
                    "关联到构建 #" + buildId);
        }
        if (added.isEmpty()) {
            return;
        }
        updateStories(buildId, String.join(",", current));
        actionService.recordAction(OBJECT_TYPE_BUILD, buildId, ActionTypeEnum.EDITED,
                "关联需求：" + joinIds(added));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkStory(Long buildId, Long storyId) {
        BuildDO build = validateBuildExists(buildId);
        Set<String> current = new LinkedHashSet<>(splitIds(build.getStories()));
        if (!current.remove(String.valueOf(storyId))) {
            return;
        }
        updateStories(buildId, String.join(",", current));
        actionService.recordAction(OBJECT_TYPE_STORY, storyId, ActionTypeEnum.EDITED,
                "从构建 #" + buildId + " 移除");
        actionService.recordAction(OBJECT_TYPE_BUILD, buildId, ActionTypeEnum.EDITED,
                "移除需求 #" + storyId);
    }

    // ==================== 写：关联 Bug（带自动解决） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkBugs(Long buildId, List<Long> bugIds, Map<Long, String> resolvedBy) {
        BuildDO build = validateBuildExists(buildId);
        Set<String> current = new LinkedHashSet<>(splitIds(build.getBugs()));
        List<Long> added = new ArrayList<>();
        for (Long bugId : bugIds) {
            if (bugId == null || current.contains(String.valueOf(bugId))) {
                continue;
            }
            BugDO bug = bugMapper.selectById(bugId);
            if (bug == null) {
                continue;
            }
            current.add(String.valueOf(bugId));
            added.add(bugId);
            // 关键副作用：关联到构建的未解决 Bug 会被直接置为「已解决」，
            // resolvedBuild 指向本构建（禅道 updateLinkedBug）
            resolveBugIfNeeded(bug, build, resolvedBy == null ? null : resolvedBy.get(bugId));
        }
        if (added.isEmpty()) {
            return;
        }
        updateBugs(buildId, String.join(",", current));
        // 同一个「质量链回写」：关联到构建的 Bug 既然算「在这个构建里解决的」，
        // 那包含这个构建的发布也应该拿到这些 Bug（与缺陷模块 resolve 走同一条路）
        for (Long bugId : added) {
            releaseService.appendBugByResolvedBuild(build.getProduct(), bugId, String.valueOf(buildId));
        }
        actionService.recordAction(OBJECT_TYPE_BUILD, buildId, ActionTypeEnum.EDITED,
                "关联 Bug：" + joinIds(added));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkBug(Long buildId, Long bugId) {
        BuildDO build = validateBuildExists(buildId);
        Set<String> current = new LinkedHashSet<>(splitIds(build.getBugs()));
        if (!current.remove(String.valueOf(bugId))) {
            return;
        }
        updateBugs(buildId, String.join(",", current));
        // 注意：禅道解除关联时不会回退 Bug 的解决状态，这里保持一致
        actionService.recordAction(OBJECT_TYPE_BUG, bugId, ActionTypeEnum.EDITED,
                "从构建 #" + buildId + " 移除");
        actionService.recordAction(OBJECT_TYPE_BUILD, buildId, ActionTypeEnum.EDITED,
                "移除 Bug #" + bugId);
    }

    /**
     * 未解决/未关闭的 Bug → resolved + fixed + resolvedBuild 指向本构建，并指派回创建人
     */
    private void resolveBugIfNeeded(BugDO bug, BuildDO build, String resolver) {
        if (BugStatusEnum.RESOLVED.getStatus().equals(bug.getStatus())
                || BugStatusEnum.CLOSED.getStatus().equals(bug.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String account = StringUtils.hasText(resolver) ? resolver : currentAccount();
        BugDO updateObj = new BugDO();
        updateObj.setId(bug.getId());
        updateObj.setStatus(BugStatusEnum.RESOLVED.getStatus());
        updateObj.setResolution(BugResolutionEnum.FIXED.getResolution());
        updateObj.setResolvedBuild(String.valueOf(build.getId()));
        updateObj.setResolvedBy(account);
        updateObj.setResolvedDate(now);
        updateObj.setConfirmed(1);
        updateObj.setAssignedTo(bug.getOpenedBy());
        updateObj.setAssignedDate(now);
        updateObj.setLastEditedBy(account);
        updateObj.setLastEditedDate(now);
        bugMapper.updateById(updateObj);

        actionService.recordAction(OBJECT_TYPE_BUG, bug.getId(), ActionTypeEnum.EDITED,
                "关联构建 #" + build.getId() + " 时自动解决（fixed）");
    }

    // ==================== 内部 ====================

    private boolean isIntegrated(BuildDO build) {
        return build.getExecution() != null && build.getExecution() == 0
                && StringUtils.hasText(build.getBuilds());
    }

    private List<BuildDO> childBuilds(BuildDO build) {
        List<Long> ids = splitIds(build.getBuilds()).stream().map(Long::valueOf).toList();
        return ids.isEmpty() ? List.of() : buildMapper.selectListByIds(ids);
    }

    /**
     * 校验集成构建的子构建：必须存在、不能是自己、且子构建本身不能再是集成构建
     */
    private List<BuildDO> validateIntegratedChildren(boolean integrated, List<Long> buildIds) {
        if (!integrated) {
            return List.of();
        }
        if (buildIds == null || buildIds.isEmpty()) {
            throw exception(BUILD_INTEGRATED_NEEDS_CHILDREN);
        }
        List<BuildDO> children = buildMapper.selectListByIds(buildIds);
        if (children.size() != new LinkedHashSet<>(buildIds).size()) {
            for (Long id : buildIds) {
                if (children.stream().noneMatch(c -> c.getId().equals(id))) {
                    throw exception(BUILD_CHILD_NOT_EXISTS, id);
                }
            }
        }
        for (BuildDO child : children) {
            if (isIntegrated(child)) {
                throw exception(BUILD_CHILD_NOT_EXISTS, child.getId() + "（集成构建不能再被集成）");
            }
        }
        return children;
    }

    /**
     * 集成构建的分支 = 子构建分支的并集（禅道 create() 里的 relationBranch）
     */
    private String unionBranches(List<BuildDO> children) {
        Set<String> branches = new LinkedHashSet<>();
        for (BuildDO child : children) {
            branches.addAll(splitIds(child.getBranch()));
        }
        if (branches.contains("0")) {
            return "0";
        }
        return String.join(",", branches);
    }

    /**
     * 非集成构建：校验产品与分支。
     * 禅道规则：多分支/多平台产品必须选分支；项目未关联产品时产品可以不填。
     */
    private Long validateProduct(Long product) {
        if (product == null) {
            throw exception(BUILD_PRODUCT_REQUIRED);
        }
        productService.validateProductExists(product);
        return product;
    }

    private String buildBranch(Long product, List<Long> branches) {
        ProductDO productDO = productService.validateProductExists(product);
        String branchName = ProductTypeEnum.branchNameOf(productDO.getType());
        if (!ProductTypeEnum.supportsBranch(productDO.getType())) {
            boolean hasReal = branches != null && branches.stream().anyMatch(id -> id != null && id != 0L);
            if (hasReal) {
                throw exception(BUILD_BRANCH_REQUIRED, branchName);
            }
            return "0";
        }
        if (branches == null || branches.isEmpty()) {
            throw exception(BUILD_BRANCH_REQUIRED, branchName);
        }
        Set<Long> normalized = new LinkedHashSet<>(branches);
        if (normalized.contains(0L)) {
            return "0";
        }
        Set<Long> valid = new LinkedHashSet<>();
        for (BranchDO branch : branchService.getBranchListByProduct(product, null)) {
            if (branch.getId() != null) {
                valid.add(branch.getId());
            }
        }
        for (Long branchId : normalized) {
            if (!valid.contains(branchId)) {
                throw exception(BUILD_BRANCH_REQUIRED, branchName + "（" + branchId + " 不属于该产品）");
            }
        }
        return normalized.stream().sorted().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("0");
    }

    /**
     * 执行必须存在，且真的是一条「执行」（type = sprint/stage/kanban，不能传项目 id）。
     *
     * <p>这里直接用 ProjectMapper 查而不是走 ProjectService：后者的 getProject 现在会
     * 主动拒绝「传执行 id」的调用（项目/执行共用一张表的互斥校验），方向正好相反。
     */
    private Long validateExecution(Long execution) {
        if (execution == null || execution <= 0) {
            throw exception(BUILD_EXECUTION_REQUIRED);
        }
        ProjectDO row = projectMapper.selectById(execution);
        if (row == null || !cn.iocoder.yudao.module.zentao.enums.execution.ExecutionTypeEnum.isExecution(row.getType())) {
            throw exception(BUILD_EXECUTION_NOT_EXISTS, execution);
        }
        return execution;
    }

    private Long projectOfExecution(Long execution) {
        if (execution == null || execution <= 0) {
            return 0L;
        }
        ProjectDO row = projectMapper.selectById(execution);
        return row != null && row.getProject() != null ? row.getProject() : 0L;
    }

    private void updateStories(Long buildId, String stories) {
        BuildDO updateObj = new BuildDO();
        updateObj.setId(buildId);
        updateObj.setStories(stories);
        buildMapper.updateById(updateObj);
    }

    private void updateBugs(Long buildId, String bugs) {
        BuildDO updateObj = new BuildDO();
        updateObj.setId(buildId);
        updateObj.setBugs(bugs);
        buildMapper.updateById(updateObj);
    }

    /**
     * 把逗号列表拆成有序去重的字符串集合。空串/只有逗号时返回空列表。
     */
    private List<String> splitIds(String value) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(value)) {
            return result;
        }
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (StringUtils.hasText(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return ids.stream().filter(java.util.Objects::nonNull).distinct()
                .map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
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
