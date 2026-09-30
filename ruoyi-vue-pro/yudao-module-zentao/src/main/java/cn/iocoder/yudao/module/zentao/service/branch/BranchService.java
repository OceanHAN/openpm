package cn.iocoder.yudao.module.zentao.service.branch;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.branch.vo.BranchPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.branch.vo.BranchSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.branch.BranchDO;

import java.util.List;

/**
 * 分支/平台 Service 接口
 *
 * <h3>禅道语义（module/branch/model.php）</h3>
 * <ul>
 *   <li>{@code zt_branch} 是**产品维度**的分支表。产品类型决定称呼：
 *       normal 无分支 / branch「分支」/ platform「平台」</li>
 *   <li><b>id = 0 是虚拟主干</b>（{@code BRANCH_MAIN}），不落库。
 *       需求/缺陷/模块的 branch = 0 即挂在主干上</li>
 *   <li>主干不能关闭、不能删除，只能被设为默认</li>
 *   <li>删除要过 {@code checkBranchData()}：分支下有数据就拒绝</li>
 * </ul>
 */
public interface BranchService {

    /**
     * 新建分支。order 自动取 max+1，状态固定 active
     */
    Long createBranch(BranchSaveReqVO createReqVO);

    /**
     * 修改分支
     */
    void updateBranch(BranchSaveReqVO updateReqVO);

    /**
     * 关闭分支：active → closed，并取消默认标记
     */
    void closeBranch(Long id);

    /**
     * 激活分支：closed → active
     */
    void activateBranch(Long id);

    /**
     * 设为默认分支。branchId 传 0 表示把默认分支还原成主干（即清空默认标记）
     *
     * @param product  产品编号
     * @param branchId 分支编号，可为 0
     */
    void setDefaultBranch(Long product, Long branchId);

    /**
     * 删除分支。分支下有数据时拒绝
     */
    void deleteBranch(Long id);

    /**
     * 批量删除
     */
    void deleteBranchList(List<Long> ids);

    /**
     * 获得分支。id = 0 时返回虚拟主干（不查库）
     */
    BranchDO getBranch(Long id);

    /**
     * 校验分支存在（不含主干）
     */
    BranchDO validateBranchExists(Long id);

    /**
     * 获得分支分页。指定产品时会在第一页补一行虚拟主干
     */
    PageResult<BranchDO> getBranchPage(BranchPageReqVO reqVO);

    /**
     * 某个产品下的全部分支（不含主干），可按状态过滤
     */
    List<BranchDO> getBranchListByProduct(Long product, String status);

    /**
     * 某个产品下的分支数量（不含主干）
     */
    Long countBranchByProduct(Long product);

    /**
     * 产品是否启用了分支/平台（产品类型为 branch 或 platform）
     */
    boolean isBranchEnabled(Long product);

    /**
     * 分支/平台的业务文案：产品类型是 platform 时为「平台」，否则为「分支」
     */
    String branchNameOf(Long product);

}
