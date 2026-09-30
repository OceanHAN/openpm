package cn.iocoder.yudao.module.zentao.service.bug;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugResolveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;

import java.util.List;

/**
 * 缺陷 Service 接口
 *
 * <h3>状态机</h3>
 * <pre>
 *   active(激活) ──resolve──> resolved(已解决) ──close──> closed(已关闭)
 *        ↑                          │                         │
 *        └──────────activate────────┴─────────────────────────┘
 * </pre>
 *
 * 禅道对「解决」有两个强制联动，本接口在实现层校验：
 * duplicate 必须给 duplicateBug（且目标存在）；fixed 必须给 resolvedBuild。
 */
public interface BugService {

    /**
     * 创建缺陷。初始状态 active
     */
    Long createBug(BugSaveReqVO createReqVO);

    /**
     * 修改缺陷。已关闭的缺陷不允许修改
     */
    void updateBug(BugSaveReqVO updateReqVO);

    /**
     * 解决缺陷：active → resolved
     */
    void resolveBug(BugResolveReqVO reqVO);

    /**
     * 关闭缺陷：只有 resolved 才能关闭
     */
    void closeBug(Long id);

    /**
     * 重新激活缺陷：resolved/closed → active，激活次数 +1
     */
    void activateBug(Long id, String comment);

    /**
     * 删除缺陷（逻辑删除）
     */
    void deleteBug(Long id);

    /**
     * 批量删除缺陷
     */
    void deleteBugList(List<Long> ids);

    /**
     * 获得缺陷
     */
    BugDO getBug(Long id);

    /**
     * 校验缺陷存在
     */
    BugDO validateBugExists(Long id);

    /**
     * 获得缺陷分页
     */
    PageResult<BugDO> getBugPage(BugPageReqVO reqVO);

    /**
     * 获得某需求下的全部缺陷
     */
    List<BugDO> getBugListByStory(Long story);

}
