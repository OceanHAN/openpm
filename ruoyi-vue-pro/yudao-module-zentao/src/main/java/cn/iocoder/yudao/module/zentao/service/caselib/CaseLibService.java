package cn.iocoder.yudao.module.zentao.service.caselib;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.caselib.vo.CaseLibPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.caselib.vo.CaseLibRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.caselib.vo.CaseLibSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CasePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testreport.TestSuiteDO;

import java.util.List;

/**
 * 用例库 Service 接口。
 *
 * <p>用例库不是独立表：它是 {@code zt_testsuite} 里 {@code (product=0, type='library')} 的行，
 * 库内用例是 {@code zt_case} 里 {@code (product=0, lib=<用例库编号>)} 的行，
 * 库的模块树是 {@code zt_module} 里 {@code (root=<用例库编号>, type='caselib')} 那棵树。
 * 详见 README 3.32。
 */
public interface CaseLibService {

    /**
     * 新建用例库。
     *
     * @return 用例库编号
     */
    Long createLib(CaseLibSaveReqVO reqVO);

    /**
     * 修改用例库（名称、描述、排序）
     */
    void updateLib(CaseLibSaveReqVO reqVO);

    /**
     * 删除用例库。与禅道一致用**逻辑删除**，但本实现额外加了「库内还有用例时拒绝删除」的保护
     * （禅道会直接软删，库里那批用例就变成孤儿数据）
     */
    void deleteLib(Long id);

    /**
     * 校验用例库存在且 type='library'
     */
    TestSuiteDO validateLibExists(Long id);

    CaseLibRespVO getLib(Long id);

    PageResult<CaseLibRespVO> getLibPage(CaseLibPageReqVO reqVO);

    /**
     * 全部用例库（下拉用），按 order desc, id desc
     */
    List<CaseLibRespVO> getLibList();

    // ==================== 库内用例 ====================

    /**
     * 库内用例分页。会把 reqVO.lib 强制设成 libId（避免调用方漏传/传错），
     * 并额外算出「源用例已更新」标记（fromCaseID 对应的产品用例版本已经大于导入时的版本）
     */
    PageResult<CaseRespVO> getLibCasePage(Long libId, CasePageReqVO reqVO);

    /**
     * 在用例库里建用例：product 置 0、lib 置 libId，其余规则（版本、步骤、评审）完全复用 testcase
     *
     * @return 用例编号
     */
    Long createLibCase(Long libId, CaseSaveReqVO reqVO);

    /**
     * 库内用例详情（带步骤），并补上「源用例已更新」标记
     */
    CaseRespVO getLibCase(Long caseId);

    /**
     * 可以把哪些产品用例导入这个库 —— 排除已经导入过的（禅道 getCanImportCases 按 fromCaseID 判重）
     */
    PageResult<CaseRespVO> getCanImportCasePage(Long libId, Long product, String title, PageParam pageParam);

    /**
     * 把产品用例导入用例库（禅道 testcase/importToLib）：
     * 复制用例与步骤到库里，记下来源编号与来源版本，并把来源模块同步到库的模块树下
     *
     * @return 新建的库内用例编号
     */
    List<Long> importToLib(Long libId, List<Long> caseIds);

}
