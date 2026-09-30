package cn.iocoder.yudao.module.zentao.service.release;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.release.vo.ReleasePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.release.vo.ReleaseSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.branch.BranchDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.build.BuildDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.release.ReleaseDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.release.ReleaseRelatedDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.build.BuildMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.bug.BugMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.release.ReleaseMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.release.ReleaseRelatedMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.story.StoryMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.product.ProductTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.release.ReleaseStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.story.StoryStageEnum;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 发布 Service 实现
 *
 * <p>逐条对齐禅道 {@code module/release/model.php}：create / update / delete /
 * linkStory / linkBug / changeStatus / processReleaseForCreate / setStoriesStage /
 * updateRelated。有意偏离的地方在方法注释里标了「偏离」。
 */
@Slf4j
@Service
public class ReleaseServiceImpl implements ReleaseService {

    private static final String OBJECT_TYPE_RELEASE = "release";
    private static final String OBJECT_TYPE_STORY = "story";
    private static final String OBJECT_TYPE_BUG = "bug";

    /** Bug 关联类型：本次解决 */
    private static final String BUG_TYPE_BUG = "bug";
    /** Bug 关联类型：遗留 */
    private static final String BUG_TYPE_LEFT = "leftBug";

    @Resource
    private ReleaseMapper releaseMapper;

    @Resource
    private ReleaseRelatedMapper releaseRelatedMapper;

    @Resource
    private BuildMapper buildMapper;

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
    public Long createRelease(ReleaseSaveReqVO createReqVO) {
        String status = normalizeStatus(createReqVO.getStatus());
        ProductDO product = productService.validateProductExists(createReqVO.getProduct());
        validateNameUnique(createReqVO.getName(), null);
        validateDateByStatus(status, createReqVO.getDate(), createReqVO.getReleasedDate());

        List<BuildDO> builds = loadBuilds(createReqVO.getBuilds());
        String branch = builds.isEmpty()
                ? buildBranch(product, createReqVO.getBranches())
                : unionBranches(builds);

        // 从构建同步需求/Bug（禅道 processReleaseForCreate）
        Set<String> stories = new LinkedHashSet<>();
        Set<String> bugs = new LinkedHashSet<>();
        List<Long> projectIds = new ArrayList<>();
        if (!builds.isEmpty() && !Boolean.FALSE.equals(createReqVO.getSyncFromBuilds())) {
            for (BuildDO build : withChildBuilds(builds)) {
                stories.addAll(splitIds(build.getStories()));
                bugs.addAll(splitIds(build.getBugs()));
                if (build.getProject() != null && build.getProject() > 0) {
                    projectIds.add(build.getProject());
                }
            }
        }
        // 手工指定的「涉及项目」也要并进来（禅道 release 表单里有这一项：
        // 一个发布可以涉及多个项目，zt_release.project 就是这些项目的逗号列表）
        if (createReqVO.getProjects() != null) {
            createReqVO.getProjects().forEach(id -> projectIds.add(id));
        }
        // 手工指定的也要并进来
        if (createReqVO.getStories() != null) {
            createReqVO.getStories().forEach(id -> stories.add(String.valueOf(id)));
        }
        if (createReqVO.getBugs() != null) {
            createReqVO.getBugs().forEach(id -> bugs.add(String.valueOf(id)));
        }

        ReleaseDO release = new ReleaseDO();
        release.setProduct(createReqVO.getProduct());
        release.setName(createReqVO.getName());
        release.setBranch(wrap(branch));
        release.setBuild(wrap(joinIds(createReqVO.getBuilds())));
        release.setProject(wrap(joinIds(new ArrayList<>(new LinkedHashSet<>(projectIds)))));
        release.setStories(joinSet(stories));
        release.setBugs(joinSet(bugs));
        release.setLeftBugs(joinIds(createReqVO.getLeftBugs()));
        release.setReleases(joinIds(createReqVO.getReleases()));
        release.setMarker(Boolean.TRUE.equals(createReqVO.getMarker()) ? 1 : 0);
        release.setDate(createReqVO.getDate());
        release.setReleasedDate(createReqVO.getReleasedDate());
        release.setStatus(status);
        release.setSubStatus("");
        release.setNotify("");
        release.setMailto("");
        release.setDesc(createReqVO.getDesc());
        release.setShadow(0L);
        release.setSystem(0L);
        release.setCreatedBy(currentAccount());
        release.setCreatedDate(LocalDateTime.now());
        releaseMapper.insert(release);

        // 自动创建影子构建并回填 shadow（禅道 create() 里的 shadowBuild）
        Long shadowId = createShadowBuild(release);
        ReleaseDO shadowUpdate = new ReleaseDO();
        shadowUpdate.setId(release.getId());
        shadowUpdate.setShadow(shadowId);
        releaseMapper.updateById(shadowUpdate);

        syncRelated(release);
        if (ReleaseStatusEnum.NORMAL.getStatus().equals(status)) {
            // 已发布的发布，把需求阶段推进到「已发布」
            updateStoriesStage(release.getStories(), StoryStageEnum.RELEASED.getStage());
        }

        actionService.recordAction(OBJECT_TYPE_RELEASE, release.getId(), ActionTypeEnum.CREATED,
                "创建发布：" + release.getName() + "（自动生成影子构建 #" + shadowId + "）");
        return release.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRelease(ReleaseSaveReqVO updateReqVO) {
        ReleaseDO oldRelease = validateReleaseExists(updateReqVO.getId());
        String status = normalizeStatus(updateReqVO.getStatus());
        validateNameUnique(updateReqVO.getName(), oldRelease.getId());
        validateDateByStatus(status, updateReqVO.getDate(), updateReqVO.getReleasedDate());

        List<BuildDO> builds = loadBuilds(updateReqVO.getBuilds());
        // 有构建时分支由构建推导；没有构建时允许手工维护分支（禅道表单也是这么用的）
        String branch = !builds.isEmpty()
                ? unionBranches(builds)
                : (updateReqVO.getBranches() == null || updateReqVO.getBranches().isEmpty()
                        ? oldRelease.getBranch()
                        : buildBranch(productService.getProduct(oldRelease.getProduct()), updateReqVO.getBranches()));
        List<Long> projectIds = new ArrayList<>();
        for (BuildDO build : builds) {
            if (build.getProject() != null && build.getProject() > 0) {
                projectIds.add(build.getProject());
            }
        }

        Set<String> stories = new LinkedHashSet<>(splitIds(oldRelease.getStories()));
        Set<String> bugs = new LinkedHashSet<>(splitIds(oldRelease.getBugs()));
        // 传了 stories/bugs 就以传入的为准（重新计算清单），没传则沿用旧值
        if (updateReqVO.getStories() != null) {
            Set<String> newStories = new LinkedHashSet<>();
            updateReqVO.getStories().forEach(id -> newStories.add(String.valueOf(id)));
            stories = newStories;
        }
        if (updateReqVO.getBugs() != null) {
            Set<String> newBugs = new LinkedHashSet<>();
            updateReqVO.getBugs().forEach(id -> newBugs.add(String.valueOf(id)));
            bugs = newBugs;
        }

        ReleaseDO updateObj = new ReleaseDO();
        updateObj.setId(oldRelease.getId());
        updateObj.setName(updateReqVO.getName());
        updateObj.setStatus(status);
        updateObj.setBranch(wrap(branch));
        updateObj.setBuild(wrap(joinIds(updateReqVO.getBuilds())));
        if (!projectIds.isEmpty()) {
            updateObj.setProject(wrap(joinIds(new ArrayList<>(new LinkedHashSet<>(projectIds)))));
        }
        updateObj.setStories(joinSet(stories));
        updateObj.setBugs(joinSet(bugs));
        updateObj.setLeftBugs(joinIds(updateReqVO.getLeftBugs()));
        updateObj.setReleases(joinIds(updateReqVO.getReleases()));
        updateObj.setMarker(Boolean.TRUE.equals(updateReqVO.getMarker()) ? 1 : 0);
        updateObj.setDate(updateReqVO.getDate());
        updateObj.setDesc(updateReqVO.getDesc());
        releaseMapper.updateById(updateObj);
        // 状态与实际发布日期单独用原生 UPDATE：未开始状态要把 releasedDate 置空，
        // 而 updateById 会跳过 null 字段（禅道 update() 里 releasedDate = null）
        releaseMapper.updateStatusAndReleasedDate(oldRelease.getId(), status,
                ReleaseStatusEnum.WAIT.getStatus().equals(status) ? null : updateReqVO.getReleasedDate());

        // 影子构建同步：名称/构建/日期变了就跟着改（禅道 edit() 里的 shadowBuild）
        syncShadowBuild(oldRelease, updateReqVO, status);

        syncRelated(releaseMapper.selectById(oldRelease.getId()));
        if (!ReleaseStatusEnum.NORMAL.getStatus().equals(oldRelease.getStatus())
                && ReleaseStatusEnum.NORMAL.getStatus().equals(status)) {
            updateStoriesStage(updateObj.getStories(), StoryStageEnum.RELEASED.getStage());
        }

        ReleaseDO newRelease = releaseMapper.selectById(oldRelease.getId());
        actionService.recordActionWithChanges(OBJECT_TYPE_RELEASE, oldRelease.getId(),
                ActionTypeEnum.EDITED, null, oldRelease, newRelease);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRelease(Long id) {
        ReleaseDO release = validateReleaseExists(id);
        Long includedCount = releaseMapper.countAsChildRelease(id);
        if (includedCount != null && includedCount > 0) {
            throw exception(RELEASE_IS_INCLUDED, includedCount);
        }

        // 偏离禅道：禅道只删发布、把影子构建留成孤儿；这里把影子构建一起删掉，
        // 避免构建列表里出现一个没有任何引用、名字和发布一样的空构建。
        if (release.getShadow() != null && release.getShadow() > 0) {
            buildMapper.deleteById(release.getShadow());
        }
        releaseRelatedMapper.deleteByRelease(id);
        releaseMapper.deleteById(id);
        actionService.recordAction(OBJECT_TYPE_RELEASE, id, ActionTypeEnum.DELETED,
                "删除发布：" + release.getName());
    }

    // ==================== 写：状态流转 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishRelease(Long id, LocalDateTime releasedDate) {
        validateReleaseExists(id);
        changeStatus(id, ReleaseStatusEnum.NORMAL.getStatus(), releasedDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status, LocalDateTime releasedDate) {
        ReleaseDO release = validateReleaseExists(id);
        if (!ReleaseStatusEnum.isValid(status)) {
            throw exception(RELEASE_STATUS_ILLEGAL, status);
        }
        LocalDateTime finalDate = releasedDate != null ? releasedDate : LocalDateTime.now();
        if (finalDate.isAfter(LocalDateTime.now())) {
            throw exception(RELEASE_DATE_IN_FUTURE);
        }
        if (ReleaseStatusEnum.WAIT.getStatus().equals(status)) {
            finalDate = null;
        }

        releaseMapper.updateStatusAndReleasedDate(id, status, finalDate);

        if (ReleaseStatusEnum.NORMAL.getStatus().equals(status)) {
            updateStoriesStage(release.getStories(), StoryStageEnum.RELEASED.getStage());
        }
        actionService.recordAction(OBJECT_TYPE_RELEASE, id, ActionTypeEnum.EDITED,
                "发布状态变更为：" + ReleaseStatusEnum.nameOf(status));
    }

    // ==================== 读 ====================

    @Override
    public ReleaseDO getRelease(Long id) {
        return validateReleaseExists(id);
    }

    @Override
    public ReleaseDO validateReleaseExists(Long id) {
        ReleaseDO release = id == null ? null : releaseMapper.selectById(id);
        if (release == null) {
            throw exception(RELEASE_NOT_EXISTS, id);
        }
        return release;
    }

    @Override
    public PageResult<ReleaseDO> getReleasePage(ReleasePageReqVO reqVO) {
        return releaseMapper.selectPage(reqVO);
    }

    @Override
    public List<ReleaseDO> getReleaseListByProduct(Long product, Long branch) {
        return releaseMapper.selectListByProduct(product, branch);
    }

    @Override
    public List<StoryDO> getReleaseStories(Long releaseId) {
        ReleaseDO release = validateReleaseExists(releaseId);
        return loadByIds(splitIds(release.getStories()), storyMapper::selectBatchIds, StoryDO::getId, StoryDO.class);
    }

    @Override
    public List<StoryDO> getUnlinkedStories(Long releaseId) {
        ReleaseDO release = validateReleaseExists(releaseId);
        Set<String> linked = new LinkedHashSet<>(splitIds(release.getStories()));
        return storyMapper.selectListByProduct(release.getProduct()).stream()
                .filter(story -> !linked.contains(String.valueOf(story.getId())))
                .filter(story -> !"closed".equals(story.getStatus()))
                .toList();
    }

    @Override
    public List<BugDO> getReleaseBugs(Long releaseId, String type) {
        ReleaseDO release = validateReleaseExists(releaseId);
        List<String> ids = splitIds(BUG_TYPE_LEFT.equals(type) ? release.getLeftBugs() : release.getBugs());
        return loadByIds(ids, bugMapper::selectBatchIds, BugDO::getId, BugDO.class);
    }

    @Override
    public List<BugDO> getUnlinkedBugs(Long releaseId, String type) {
        ReleaseDO release = validateReleaseExists(releaseId);
        Set<String> linked = new LinkedHashSet<>(splitIds(
                BUG_TYPE_LEFT.equals(type) ? release.getLeftBugs() : release.getBugs()));
        return bugMapper.selectListByProduct(release.getProduct()).stream()
                .filter(bug -> !linked.contains(String.valueOf(bug.getId())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long appendBugByResolvedBuild(Long product, Long bugId, String resolvedBuild) {
        if (bugId == null || !StringUtils.hasText(resolvedBuild)) {
            return null;
        }
        // 禅道把「解决版本」存成构建编号的字符串；不是纯数字就说明用户填的是版本名，直接跳过
        Long buildId;
        try {
            buildId = Long.valueOf(resolvedBuild.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        BuildDO build = buildMapper.selectById(buildId);
        if (build == null) {
            return null;
        }
        // 1) 构建的 Bug 清单并进这个 Bug（禅道先更新 zt_build.bugs）
        Set<String> buildBugs = new LinkedHashSet<>(splitIds(build.getBugs()));
        if (buildBugs.add(String.valueOf(bugId))) {
            BuildDO buildUpdate = new BuildDO();
            buildUpdate.setId(build.getId());
            buildUpdate.setBugs(joinSet(buildBugs));
            buildMapper.updateById(buildUpdate);
        }
        // 2) 找包含这个构建的发布 → 并进发布的 Bug 清单（复用 linkBugs：它会处理去重、
        //    releaserelated 关系行与操作日志）
        Long releaseProduct = product != null && product > 0 ? product : build.getProduct();
        ReleaseDO release = releaseMapper.selectByBuild(releaseProduct, buildId);
        if (release == null) {
            return null;
        }
        linkBugs(release.getId(), BUG_TYPE_BUG, List.of(bugId));
        return release.getId();
    }

    @Override
    public boolean isIncludedByOther(Long releaseId) {
        Long count = releaseMapper.countAsChildRelease(releaseId);
        return count != null && count > 0;
    }

    @Override
    public List<Long> getRelatedIds(Long releaseId, String objectType) {
        return releaseRelatedMapper.selectListByRelease(releaseId, objectType).stream()
                .map(ReleaseRelatedDO::getObjectID).toList();
    }

    // ==================== 写：关联需求 / Bug ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkStories(Long releaseId, List<Long> storyIds) {
        ReleaseDO release = validateReleaseExists(releaseId);
        Set<String> current = new LinkedHashSet<>(splitIds(release.getStories()));
        List<Long> added = new ArrayList<>();
        for (Long storyId : storyIds) {
            if (storyId == null || current.contains(String.valueOf(storyId))) {
                continue;
            }
            current.add(String.valueOf(storyId));
            added.add(storyId);
            actionService.recordAction(OBJECT_TYPE_STORY, storyId, ActionTypeEnum.EDITED,
                    "关联到发布 #" + releaseId);
        }
        if (added.isEmpty()) {
            return;
        }
        updateField(releaseId, "stories", joinSet(current));
        replaceRelated(releaseId, "story", toLongList(new ArrayList<>(current)));
        if (ReleaseStatusEnum.NORMAL.getStatus().equals(release.getStatus())) {
            updateStoriesStage(joinSet(current), StoryStageEnum.RELEASED.getStage());
        }
        actionService.recordAction(OBJECT_TYPE_RELEASE, releaseId, ActionTypeEnum.EDITED,
                "关联需求：" + joinIds(added));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkStory(Long releaseId, Long storyId) {
        ReleaseDO release = validateReleaseExists(releaseId);
        Set<String> current = new LinkedHashSet<>(splitIds(release.getStories()));
        if (!current.remove(String.valueOf(storyId))) {
            return;
        }
        updateField(releaseId, "stories", joinSet(current));
        replaceRelated(releaseId, "story", toLongList(new ArrayList<>(current)));
        actionService.recordAction(OBJECT_TYPE_STORY, storyId, ActionTypeEnum.EDITED,
                "从发布 #" + releaseId + " 移除");
        actionService.recordAction(OBJECT_TYPE_RELEASE, releaseId, ActionTypeEnum.EDITED,
                "移除需求 #" + storyId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void linkBugs(Long releaseId, String type, List<Long> bugIds) {
        ReleaseDO release = validateReleaseExists(releaseId);
        boolean left = BUG_TYPE_LEFT.equals(type);
        String field = left ? "leftBugs" : "bugs";
        Set<String> current = new LinkedHashSet<>(splitIds(left ? release.getLeftBugs() : release.getBugs()));
        List<Long> added = new ArrayList<>();
        for (Long bugId : bugIds) {
            if (bugId == null || current.contains(String.valueOf(bugId))) {
                continue;
            }
            current.add(String.valueOf(bugId));
            added.add(bugId);
            actionService.recordAction(OBJECT_TYPE_BUG, bugId, ActionTypeEnum.EDITED,
                    "关联到发布 #" + releaseId + (left ? "（遗留）" : ""));
        }
        if (added.isEmpty()) {
            return;
        }
        updateField(releaseId, field, joinSet(current));
        replaceRelated(releaseId, left ? BUG_TYPE_LEFT : BUG_TYPE_BUG, toLongList(new ArrayList<>(current)));
        actionService.recordAction(OBJECT_TYPE_RELEASE, releaseId, ActionTypeEnum.EDITED,
                (left ? "关联遗留 Bug：" : "关联 Bug：") + joinIds(added));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkBug(Long releaseId, String type, Long bugId) {
        ReleaseDO release = validateReleaseExists(releaseId);
        boolean left = BUG_TYPE_LEFT.equals(type);
        String field = left ? "leftBugs" : "bugs";
        Set<String> current = new LinkedHashSet<>(splitIds(left ? release.getLeftBugs() : release.getBugs()));
        if (!current.remove(String.valueOf(bugId))) {
            return;
        }
        updateField(releaseId, field, joinSet(current));
        replaceRelated(releaseId, left ? BUG_TYPE_LEFT : BUG_TYPE_BUG, toLongList(new ArrayList<>(current)));
        actionService.recordAction(OBJECT_TYPE_BUG, bugId, ActionTypeEnum.EDITED,
                "从发布 #" + releaseId + " 移除");
        actionService.recordAction(OBJECT_TYPE_RELEASE, releaseId, ActionTypeEnum.EDITED,
                "移除 Bug #" + bugId);
    }

    // ==================== 内部：校验 ====================

    private String normalizeStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status : ReleaseStatusEnum.WAIT.getStatus();
        if (!ReleaseStatusEnum.isValid(normalized)) {
            throw exception(RELEASE_STATUS_ILLEGAL, status);
        }
        return normalized;
    }

    /**
     * 发布名全局唯一。
     *
     * <p>禅道的校验是 {@code check('name','unique', "system = X AND deleted = 0")}，
     * 而 system 默认 0 —— 没启用「系统」概念时，所有发布共用一个 system=0，
     * 等价于**跨产品全局唯一**。这里保持一致。
     */
    private void validateNameUnique(String name, Long excludeId) {
        if (releaseMapper.selectByName(name, excludeId) != null) {
            throw exception(RELEASE_NAME_DUPLICATE, name);
        }
    }

    /**
     * 必填字段随状态变化（禅道 create() 里动态改 requiredFields）：
     * wait 不要求 releasedDate；normal 不要求 date。
     */
    private void validateDateByStatus(String status, LocalDate date, LocalDateTime releasedDate) {
        if (ReleaseStatusEnum.NORMAL.getStatus().equals(status)) {
            if (releasedDate == null) {
                throw exception(RELEASE_RELEASED_DATE_REQUIRED);
            }
        } else if (date == null) {
            throw exception(RELEASE_DATE_REQUIRED);
        }
        if (releasedDate != null && releasedDate.isAfter(LocalDateTime.now())) {
            throw exception(RELEASE_DATE_IN_FUTURE);
        }
    }

    private List<BuildDO> loadBuilds(List<Long> buildIds) {
        if (buildIds == null || buildIds.isEmpty()) {
            return List.of();
        }
        List<BuildDO> builds = buildMapper.selectListByIds(new ArrayList<>(new LinkedHashSet<>(buildIds)));
        if (builds.size() != new LinkedHashSet<>(buildIds).size()) {
            for (Long id : buildIds) {
                if (builds.stream().noneMatch(b -> b.getId().equals(id))) {
                    throw exception(RELEASE_BUILD_NOT_EXISTS, id);
                }
            }
        }
        return builds;
    }

    /**
     * 把集成构建的子构建也一起算进来（禅道 processReleaseForCreate 里的 linkedBuilds）
     */
    private List<BuildDO> withChildBuilds(List<BuildDO> builds) {
        Set<Long> childIds = new LinkedHashSet<>();
        for (BuildDO build : builds) {
            splitIds(build.getBuilds()).forEach(id -> childIds.add(Long.valueOf(id)));
        }
        List<BuildDO> result = new ArrayList<>(builds);
        if (!childIds.isEmpty()) {
            result.addAll(buildMapper.selectListByIds(new ArrayList<>(childIds)));
        }
        return result;
    }

    private String unionBranches(List<BuildDO> builds) {
        Set<String> branches = new LinkedHashSet<>();
        for (BuildDO build : withChildBuilds(builds)) {
            branches.addAll(splitIds(build.getBranch()));
        }
        return branches.contains("0") ? "0" : String.join(",", branches);
    }

    private String buildBranch(ProductDO product, List<Long> branches) {
        String branchName = ProductTypeEnum.branchNameOf(product.getType());
        if (!ProductTypeEnum.supportsBranch(product.getType())) {
            boolean hasReal = branches != null && branches.stream().anyMatch(id -> id != null && id != 0L);
            if (hasReal) {
                throw exception(RELEASE_BRANCH_REQUIRED,
                        branchName + "（普通产品没有分支，不要传真实分支号）");
            }
            return "0";
        }
        if (branches == null || branches.isEmpty()) {
            throw exception(RELEASE_BRANCH_REQUIRED, branchName);
        }
        Set<Long> normalized = new LinkedHashSet<>(branches);
        if (normalized.contains(0L)) {
            return "0";
        }
        Set<Long> valid = new LinkedHashSet<>();
        for (BranchDO branch : branchService.getBranchListByProduct(product.getId(), null)) {
            if (branch.getId() != null) {
                valid.add(branch.getId());
            }
        }
        for (Long branchId : normalized) {
            if (!valid.contains(branchId)) {
                throw exception(RELEASE_BRANCH_REQUIRED, branchName + "（" + branchId + " 不属于该产品）");
            }
        }
        return normalized.stream().sorted().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("0");
    }

    // ==================== 内部：影子构建与关系 ====================

    /**
     * 创建影子构建（禅道 create() 里的 shadowBuild）。
     *
     * <p>注意：这里**直接用 Mapper 插入**，不走 {@code BuildService.createBuild} ——
     * 因为影子构建的名字与发布名相同，而禅道的构建名唯一性只在 build::create 里校验，
     * 影子构建是绕开那层校验的（所以确实可能出现「构建与发布同名」）。
     */
    private Long createShadowBuild(ReleaseDO release) {
        BuildDO build = new BuildDO();
        build.setProduct(release.getProduct());
        // 发布里的 branch/builds 是「前后带逗号」的格式（',0,'），
        // 构建表统一用不带逗号的写法（'0' / '1,2'），这里做一次规范化，
        // 否则构建列表里会冒出 branch=',0,' 这种与其它构建不一致的数据。
        build.setBranch(unwrap(release.getBranch()));
        build.setProject(0L);
        build.setBuilds(unwrap(release.getBuild()));
        build.setExecution(0L);
        build.setName(release.getName());
        build.setDate(release.getDate());
        build.setBuilder(release.getCreatedBy());
        build.setSystem(0L);
        build.setArtifactRepoID(0L);
        build.setStories("");
        build.setBugs("");
        build.setCreatedBy(release.getCreatedBy());
        build.setCreatedDate(LocalDateTime.now());
        buildMapper.insert(build);
        if (build.getId() == null) {
            throw exception(RELEASE_SHADOW_FAILED);
        }
        return build.getId();
    }

    /**
     * 影子构建同步：名称/包含构建/日期变化时更新（禅道 edit()）。
     * 只有这些字段变了才写库，避免无谓更新。
     */
    private void syncShadowBuild(ReleaseDO oldRelease, ReleaseSaveReqVO updateReqVO, String status) {
        if (oldRelease.getShadow() == null || oldRelease.getShadow() <= 0) {
            return;
        }
        BuildDO shadow = buildMapper.selectById(oldRelease.getShadow());
        if (shadow == null) {
            return;
        }
        BuildDO updateObj = new BuildDO();
        updateObj.setId(shadow.getId());
        boolean changed = false;
        if (!Objects.equals(oldRelease.getName(), updateReqVO.getName())) {
            updateObj.setName(updateReqVO.getName());
            changed = true;
        }
        String newBuild = wrap(joinIds(updateReqVO.getBuilds()));
        if (!Objects.equals(shadow.getBuilds(), newBuild)) {
            updateObj.setBuilds(newBuild);
            changed = true;
        }
        if (!Objects.equals(oldRelease.getDate(), updateReqVO.getDate())) {
            updateObj.setDate(updateReqVO.getDate());
            changed = true;
        }
        String unwrappedBranch = unwrap(oldRelease.getBranch());
        if (!Objects.equals(shadow.getBranch(), unwrappedBranch)) {
            updateObj.setBranch(unwrappedBranch);
            changed = true;
        }
        if (changed) {
            buildMapper.updateById(updateObj);
        }
    }

    /**
     * 重建发布的关系表数据（禅道 processRelated / updateRelated：先删后插）
     */
    private void syncRelated(ReleaseDO release) {
        if (release == null) {
            return;
        }
        replaceRelated(release.getId(), "project", toLongList(splitIds(release.getProject())));
        replaceRelated(release.getId(), "build", toLongList(splitIds(release.getBuild())));
        replaceRelated(release.getId(), "branch", toLongList(splitIds(release.getBranch())));
        replaceRelated(release.getId(), "release", toLongList(splitIds(release.getReleases())));
        replaceRelated(release.getId(), "story", toLongList(splitIds(release.getStories())));
        replaceRelated(release.getId(), BUG_TYPE_BUG, toLongList(splitIds(release.getBugs())));
        replaceRelated(release.getId(), BUG_TYPE_LEFT, toLongList(splitIds(release.getLeftBugs())));
    }

    private void replaceRelated(Long releaseId, String objectType, List<Long> objectIds) {
        releaseRelatedMapper.deleteByRelease(releaseId, objectType);
        for (Long objectId : objectIds) {
            ReleaseRelatedDO related = new ReleaseRelatedDO();
            related.setRelease(releaseId);
            related.setObjectType(objectType);
            related.setObjectID(objectId);
            releaseRelatedMapper.insert(related);
        }
    }

    /**
     * 把关联需求的阶段推进到「已发布」（对应禅道 setStoriesStage）。
     *
     * <p>禅道的 {@code story->setStage()} 是按需求下任务的状态**算**出来的阶段，
     * 这里做了简化：发布成功后直接把阶段置为 released，并注明是文档里的有意简化。
     */
    private void updateStoriesStage(String storyIds, String stage) {
        for (String id : splitIds(storyIds)) {
            StoryDO updateObj = new StoryDO();
            updateObj.setId(Long.valueOf(id));
            updateObj.setStage(stage);
            storyMapper.updateById(updateObj);
        }
    }

    // ==================== 内部：工具 ====================

    private void updateField(Long releaseId, String field, String value) {
        ReleaseDO updateObj = new ReleaseDO();
        updateObj.setId(releaseId);
        if ("stories".equals(field)) {
            updateObj.setStories(value);
        } else if ("bugs".equals(field)) {
            updateObj.setBugs(value);
        } else if ("leftBugs".equals(field)) {
            updateObj.setLeftBugs(value);
        }
        releaseMapper.updateById(updateObj);
    }

    /**
     * 前后补逗号：禅道对 release 的 project/build/branch 会用 {@code ',' . trim(x) . ','} 存。
     * 空值保持空串（不是 ','）。
     */
    private String wrap(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return "," + value.replaceAll("^,+|,+$", "") + ",";
    }

    /**
     * 去掉前后逗号：{@code ',1,2,' -> '1,2'}；空串保持空串
     */
    private String unwrap(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("^,+|,+$", "") : "";
    }

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

    private List<Long> toLongList(List<String> values) {
        List<Long> result = new ArrayList<>();
        for (String value : values) {
            try {
                result.add(Long.valueOf(value));
            } catch (NumberFormatException ignored) {
                // 脏数据直接跳过，不要让整个发布打不开
            }
        }
        return result;
    }

    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return ids.stream().filter(Objects::nonNull).distinct()
                .map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }

    private String joinSet(Set<String> ids) {
        return ids.isEmpty() ? "" : String.join(",", ids);
    }

    /**
     * 按给定顺序批量取对象（保持逗号列表里的先后顺序）
     */
    private <T> List<T> loadByIds(List<String> idStrings,
                                  java.util.function.Function<List<Long>, List<T>> loader,
                                  java.util.function.Function<T, Long> idGetter,
                                  Class<T> clazz) {
        List<Long> ids = toLongList(idStrings);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, T> map = new java.util.HashMap<>();
        for (T item : loader.apply(ids)) {
            map.put(idGetter.apply(item), item);
        }
        List<T> result = new ArrayList<>();
        for (Long id : ids) {
            if (map.containsKey(id)) {
                result.add(map.get(id));
            }
        }
        return result;
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
