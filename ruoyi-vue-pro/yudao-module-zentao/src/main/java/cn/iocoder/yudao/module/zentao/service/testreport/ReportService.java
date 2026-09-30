package cn.iocoder.yudao.module.zentao.service.testreport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestReportPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestReportRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestReportSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestSuiteCaseLinkReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestSuitePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestSuiteRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestSuiteSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testcase.CaseDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testreport.TestSuiteDO;

import java.util.List;

/**
 * 用例集 + 测试报告 Service 接口
 *
 * <h3>两个模块的共同点</h3>
 * 它们都**不产生新的执行数据**，而是对既有数据的组织：
 * <ul>
 *   <li>用例集 {@code zt_testsuite}：把用例打包，排进测试单时一次选完</li>
 *   <li>测试报告 {@code zt_testreport}：把一段时间内若干测试单的执行结果汇总出来</li>
 * </ul>
 */
public interface ReportService {

    // ==================== 用例集 ====================

    Long createSuite(TestSuiteSaveReqVO reqVO);

    void updateSuite(TestSuiteSaveReqVO reqVO);

    void deleteSuite(Long id);

    TestSuiteDO validateSuiteExists(Long id);

    TestSuiteRespVO getSuite(Long id);

    PageResult<TestSuiteRespVO> getSuitePage(TestSuitePageReqVO reqVO);

    List<TestSuiteDO> getSuiteListByProduct(Long product);

    /**
     * 把用例加进集合。已加过的只更新用例版本（幂等）。
     *
     * @return 实际生效的用例数
     */
    int linkSuiteCase(TestSuiteCaseLinkReqVO reqVO);

    void unlinkSuiteCase(Long suiteId, Long caseId);

    /**
     * 集合里的用例（带「用例已变更」标记）
     */
    List<CaseDO> getSuiteCaseList(Long suiteId);

    /**
     * 还能加进这个集合的用例（同产品、且还没加过）
     */
    List<CaseDO> getSuiteUnlinkedCaseList(Long suiteId, String title, Long module);

    // ==================== 测试报告 ====================

    Long createReport(TestReportSaveReqVO reqVO);

    void updateReport(TestReportSaveReqVO reqVO);

    void deleteReport(Long id);

    TestReportRespVO getReport(Long id);

    PageResult<TestReportRespVO> getReportPage(TestReportPageReqVO reqVO);

    /**
     * 只算汇总不落库：建报告时前端可以先看一眼数字
     */
    TestReportRespVO previewReport(Long product, String tasks, String begin, String end);

}
