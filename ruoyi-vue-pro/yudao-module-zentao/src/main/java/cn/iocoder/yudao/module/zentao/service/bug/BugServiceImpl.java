package cn.iocoder.yudao.module.zentao.service.bug;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugResolveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.bug.BugMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.bug.BugResolutionEnum;
import cn.iocoder.yudao.module.zentao.enums.bug.BugStatusEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import cn.iocoder.yudao.module.zentao.service.release.ReleaseService;
import jakarta.annotation.Resource;
import cn.iocoder.yudao.module.zentao.service.module.ModuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 缺陷 Service 实现
 *
 * 业务规则来源：禅道 {@code module/bug/model.php} 的 resolve() / close() / activate()。
 */
@Slf4j
@Service
public class BugServiceImpl implements BugService {

    private static final String OBJECT_TYPE_BUG = "bug";

    @Resource
    private ModuleService moduleService;

    @Resource
    private BugMapper bugMapper;

    @Resource
    private ReleaseService releaseService;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ==================== 写：CRUD ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBug(BugSaveReqVO createReqVO) {
        BugDO bug = BeanUtils.toBean(createReqVO, BugDO.class);
        // 禅道新建缺陷的初始值：激活状态、未确认、激活次数 0
        bug.setStatus(BugStatusEnum.ACTIVE.getStatus());
        bug.setConfirmed(0);
        bug.setActivatedCount(0);
        String operator = currentAccount();
        bug.setOpenedBy(operator);
        bug.setOpenedDate(LocalDateTime.now());
        if (StringUtils.hasText(createReqVO.getAssignedTo())) {
            bug.setAssignedDate(LocalDateTime.now());
        }
        bugMapper.insert(bug);

        actionService.recordAction(OBJECT_TYPE_BUG, bug.getId(), ActionTypeEnum.CREATED, null);
        return bug.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBug(BugSaveReqVO updateReqVO) {
        BugDO oldBug = validateBugExists(updateReqVO.getId());
        if (BugStatusEnum.CLOSED.getStatus().equals(oldBug.getStatus())) {
            throw exception(BUG_CLOSED_CANNOT_UPDATE);
        }

        BugDO updateObj = BeanUtils.toBean(updateReqVO, BugDO.class);
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        if (StringUtils.hasText(updateReqVO.getAssignedTo())
                && !updateReqVO.getAssignedTo().equals(oldBug.getAssignedTo())) {
            updateObj.setAssignedDate(LocalDateTime.now());
        }
        bugMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_BUG, oldBug.getId(),
                ActionTypeEnum.EDITED, null, oldBug, updateObj);
    }

    // ==================== 写：状态流转 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveBug(BugResolveReqVO reqVO) {
        BugDO oldBug = validateBugExists(reqVO.getId());
        if (BugStatusEnum.RESOLVED.getStatus().equals(oldBug.getStatus())) {
            throw exception(BUG_ALREADY_RESOLVED);
        }
        if (BugStatusEnum.CLOSED.getStatus().equals(oldBug.getStatus())) {
            throw exception(BUG_CLOSED_CANNOT_UPDATE);
        }
        // 解决方案必须是合法枚举值
        if (!BugResolutionEnum.isValid(reqVO.getResolution())) {
            throw exception(BUG_RESOLUTION_INVALID, reqVO.getResolution());
        }

        // 禅道联动校验一：解决方案为「重复Bug」时必须指定目标，且目标要存在、不能是自己
        if (BugResolutionEnum.DUPLICATE.getResolution().equals(reqVO.getResolution())) {
            if (reqVO.getDuplicateBug() == null) {
                throw exception(BUG_DUPLICATE_BUG_REQUIRED);
            }
            if (reqVO.getDuplicateBug().equals(oldBug.getId())) {
                throw exception(BUG_DUPLICATE_SELF);
            }
            if (bugMapper.selectById(reqVO.getDuplicateBug()) == null) {
                throw exception(BUG_DUPLICATE_BUG_NOT_EXISTS, reqVO.getDuplicateBug());
            }
        }
        // 禅道联动校验二：解决方案为「已解决」时必须填写解决版本
        if (BugResolutionEnum.FIXED.getResolution().equals(reqVO.getResolution())
                && !StringUtils.hasText(reqVO.getResolvedBuild())) {
            throw exception(BUG_RESOLVED_BUILD_REQUIRED);
        }

        BugDO updateObj = new BugDO();
        updateObj.setId(oldBug.getId());
        updateObj.setStatus(BugStatusEnum.RESOLVED.getStatus());
        updateObj.setResolution(reqVO.getResolution());
        updateObj.setResolvedBuild(reqVO.getResolvedBuild());
        updateObj.setDuplicateBug(reqVO.getDuplicateBug() == null ? 0L : reqVO.getDuplicateBug());
        updateObj.setResolvedBy(currentAccount());
        updateObj.setResolvedDate(LocalDateTime.now());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        bugMapper.updateById(updateObj);

        // 禅道 module/bug/model.php:2075：解决缺陷时按「解决版本」把 Bug 自动并进
        // 构建与发布的清单（"质量链"的自动回写）—— 构建 → 发布 → 遗留 Bug 这条链
        // 之前只有手工关联能动，这里补上自动那一半
        Long linkedRelease = releaseService.appendBugByResolvedBuild(
                oldBug.getProduct(), oldBug.getId(), reqVO.getResolvedBuild());

        BugResolutionEnum resolutionEnum = BugResolutionEnum.of(reqVO.getResolution());
        String comment = "解决方案：" + (resolutionEnum != null ? resolutionEnum.getName() : reqVO.getResolution())
                + (StringUtils.hasText(reqVO.getComment()) ? "；说明：" + reqVO.getComment() : "")
                + (linkedRelease != null ? "；已自动并入发布 #" + linkedRelease + " 的 Bug 清单" : "");
        actionService.recordActionWithChanges(OBJECT_TYPE_BUG, oldBug.getId(),
                ActionTypeEnum.CHANGED, comment, oldBug, updateObj);
    }

    @Override
    public void closeBug(Long id) {
        BugDO bug = validateBugExists(id);
        // 禅道规则：只有已解决的缺陷才能关闭
        if (!BugStatusEnum.RESOLVED.getStatus().equals(bug.getStatus())) {
            throw exception(BUG_NOT_RESOLVED_CANNOT_CLOSE);
        }

        BugDO updateObj = new BugDO();
        updateObj.setId(id);
        updateObj.setStatus(BugStatusEnum.CLOSED.getStatus());
        updateObj.setClosedBy(currentAccount());
        updateObj.setClosedDate(LocalDateTime.now());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        bugMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_BUG, id,
                ActionTypeEnum.CLOSED, null, bug, updateObj);
    }

    @Override
    public void activateBug(Long id, String comment) {
        BugDO bug = validateBugExists(id);
        if (BugStatusEnum.ACTIVE.getStatus().equals(bug.getStatus())) {
            // 这里不能用 BUG_ALREADY_RESOLVED，否则会提示「已经是解决状态」，与实际语义不符
            throw exception(BUG_ALREADY_ACTIVE);
        }

        BugDO updateObj = new BugDO();
        updateObj.setId(id);
        updateObj.setStatus(BugStatusEnum.ACTIVE.getStatus());
        // 激活次数累加，禅道用它统计「同一个问题反复出现」的频率
        updateObj.setActivatedCount((bug.getActivatedCount() == null ? 0 : bug.getActivatedCount()) + 1);
        updateObj.setActivatedDate(LocalDateTime.now());
        // 激活时清掉上一次的解决信息，避免状态与字段不一致
        updateObj.setResolution("");
        updateObj.setResolvedBy("");
        updateObj.setClosedBy("");
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        bugMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_BUG, id,
                ActionTypeEnum.ACTIVATED, comment, bug, updateObj);
    }

    // ==================== 写：删除 ====================

    @Override
    public void deleteBug(Long id) {
        validateBugExists(id);
        bugMapper.deleteById(id);
        actionService.recordAction(OBJECT_TYPE_BUG, id, ActionTypeEnum.DELETED, null);
    }

    @Override
    public void deleteBugList(List<Long> ids) {
        ids.forEach(this::validateBugExists);
        bugMapper.deleteByIds(ids);
        ids.forEach(id -> actionService.recordAction(OBJECT_TYPE_BUG, id,
                ActionTypeEnum.DELETED, null));
    }

    // ==================== 读 ====================

    @Override
    public BugDO getBug(Long id) {
        return bugMapper.selectById(id);
    }

    @Override
    public BugDO validateBugExists(Long id) {
        if (id == null) {
            return null;
        }
        BugDO bug = bugMapper.selectById(id);
        if (bug == null) {
            throw exception(BUG_NOT_EXISTS);
        }
        return bug;
    }

    @Override
    public PageResult<BugDO> getBugPage(BugPageReqVO reqVO) {
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
        return bugMapper.selectPage(reqVO);
    }

    @Override
    public List<BugDO> getBugListByStory(Long story) {
        return bugMapper.selectListByStory(story);
    }

    // ==================== 内部 ====================

    private String currentAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : "";
    }

}
