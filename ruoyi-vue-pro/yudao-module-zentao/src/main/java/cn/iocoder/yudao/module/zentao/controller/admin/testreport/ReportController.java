package cn.iocoder.yudao.module.zentao.controller.admin.testreport;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestReportPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestReportRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestReportSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestSuiteCaseLinkReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestSuitePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestSuiteRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo.TestSuiteSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testreport.TestSuiteDO;
import cn.iocoder.yudao.module.zentao.service.testreport.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 测试报告 + 用例集 Controller
 *
 * <p>两个模块合用一个页面：它们都**不产生新的执行数据**，而是对既有测试数据的组织 ——
 * 用例集把用例打包（编排的便利），测试报告把一段时间的结果汇总（结果的汇总）。
 */
@Tag(name = "管理后台 - 禅道测试报告与用例集")
@RestController
@RequestMapping("/zentao/testreport")
@Validated
public class ReportController {

    @Resource
    private ReportService reportService;

    // ==================== 测试报告 ====================

    @PostMapping("/create")
    @Operation(summary = "新建测试报告",
            description = "tasks 是逗号列表；会校验这些测试单与报告同产品。创建时把「涉及的需求/缺陷/用例」清单落库留档")
    @PreAuthorize("@ss.hasPermission('zentao:testreport:create')")
    public CommonResult<Long> createReport(@Valid @RequestBody TestReportSaveReqVO reqVO) {
        return success(reportService.createReport(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改测试报告", description = "条件（测试单/起止日期）变了会重算留档清单；只改结论不重算")
    @PreAuthorize("@ss.hasPermission('zentao:testreport:update')")
    public CommonResult<Boolean> updateReport(@Valid @RequestBody TestReportSaveReqVO reqVO) {
        reportService.updateReport(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除测试报告")
    @Parameter(name = "id", description = "报告编号", required = true, example = "95201")
    @PreAuthorize("@ss.hasPermission('zentao:testreport:delete')")
    public CommonResult<Boolean> deleteReport(@RequestParam("id") Long id) {
        reportService.deleteReport(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得测试报告详情",
            description = "用例数/通过/失败等数字是**读的时候现算**的；每条 run 只取区间内最后一次结果")
    @Parameter(name = "id", description = "报告编号", required = true, example = "95201")
    @PreAuthorize("@ss.hasPermission('zentao:testreport:query')")
    public CommonResult<TestReportRespVO> getReport(@RequestParam("id") Long id) {
        return success(reportService.getReport(id));
    }

    @GetMapping("/page")
    @Operation(summary = "测试报告分页查询")
    @PreAuthorize("@ss.hasPermission('zentao:testreport:query')")
    public CommonResult<PageResult<TestReportRespVO>> getReportPage(@Valid TestReportPageReqVO reqVO) {
        return success(reportService.getReportPage(reqVO));
    }

    @GetMapping("/preview")
    @Operation(summary = "预览汇总结果（不落库）", description = "建报告前先看一眼数字对不对")
    @Parameter(name = "product", description = "产品编号", required = true, example = "1")
    @Parameter(name = "tasks", description = "测试单编号，逗号列表", required = true, example = "94101,94103")
    @PreAuthorize("@ss.hasPermission('zentao:testreport:query')")
    public CommonResult<TestReportRespVO> previewReport(@RequestParam("product") Long product,
                                                        @RequestParam("tasks") String tasks,
                                                        @RequestParam(value = "begin", required = false) String begin,
                                                        @RequestParam(value = "end", required = false) String end) {
        return success(reportService.previewReport(product, tasks, begin, end));
    }

    // ==================== 用例集 ====================

    @PostMapping("/suite/create")
    @Operation(summary = "新建用例集", description = "同产品下名称唯一")
    @PreAuthorize("@ss.hasPermission('zentao:testsuite:create')")
    public CommonResult<Long> createSuite(@Valid @RequestBody TestSuiteSaveReqVO reqVO) {
        return success(reportService.createSuite(reqVO));
    }

    @PutMapping("/suite/update")
    @Operation(summary = "修改用例集")
    @PreAuthorize("@ss.hasPermission('zentao:testsuite:update')")
    public CommonResult<Boolean> updateSuite(@Valid @RequestBody TestSuiteSaveReqVO reqVO) {
        reportService.updateSuite(reqVO);
        return success(true);
    }

    @DeleteMapping("/suite/delete")
    @Operation(summary = "删除用例集", description = "集合里还有用例时拒绝删除（本实现的有意改进）")
    @Parameter(name = "id", description = "用例集编号", required = true, example = "95102")
    @PreAuthorize("@ss.hasPermission('zentao:testsuite:delete')")
    public CommonResult<Boolean> deleteSuite(@RequestParam("id") Long id) {
        reportService.deleteSuite(id);
        return success(true);
    }

    @GetMapping("/suite/get")
    @Operation(summary = "获得用例集")
    @Parameter(name = "id", description = "用例集编号", required = true, example = "95101")
    @PreAuthorize("@ss.hasPermission('zentao:testsuite:query')")
    public CommonResult<TestSuiteRespVO> getSuite(@RequestParam("id") Long id) {
        return success(reportService.getSuite(id));
    }

    @GetMapping("/suite/page")
    @Operation(summary = "用例集分页查询")
    @PreAuthorize("@ss.hasPermission('zentao:testsuite:query')")
    public CommonResult<PageResult<TestSuiteRespVO>> getSuitePage(@Valid TestSuitePageReqVO reqVO) {
        return success(reportService.getSuitePage(reqVO));
    }

    @GetMapping("/suite/list-by-product")
    @Operation(summary = "获得某产品的用例集（下拉用）")
    @Parameter(name = "product", description = "产品编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:testsuite:query')")
    public CommonResult<List<TestSuiteRespVO>> getSuiteListByProduct(@RequestParam("product") Long product) {
        List<TestSuiteRespVO> result = new ArrayList<>();
        for (TestSuiteDO suite : reportService.getSuiteListByProduct(product)) {
            result.add(BeanUtils.toBean(suite, TestSuiteRespVO.class));
        }
        return success(result);
    }

    @PostMapping("/suite/link-case")
    @Operation(summary = "把用例加进用例集",
            description = "已加过的只更新用例版本（幂等）；用例必须与集合同产品")
    @PreAuthorize("@ss.hasPermission('zentao:testsuite:update')")
    public CommonResult<Integer> linkSuiteCase(@Valid @RequestBody TestSuiteCaseLinkReqVO reqVO) {
        return success(reportService.linkSuiteCase(reqVO));
    }

    @DeleteMapping("/suite/unlink-case")
    @Operation(summary = "把用例移出用例集")
    @PreAuthorize("@ss.hasPermission('zentao:testsuite:update')")
    public CommonResult<Boolean> unlinkSuiteCase(@RequestParam("suiteId") Long suiteId,
                                                 @RequestParam("caseId") Long caseId) {
        reportService.unlinkSuiteCase(suiteId, caseId);
        return success(true);
    }

    @GetMapping("/suite/case-list")
    @Operation(summary = "用例集里的用例")
    @Parameter(name = "suiteId", description = "用例集编号", required = true, example = "95101")
    @PreAuthorize("@ss.hasPermission('zentao:testsuite:query')")
    public CommonResult<List<CaseRespVO>> getSuiteCaseList(@RequestParam("suiteId") Long suiteId) {
        List<CaseRespVO> result = new ArrayList<>();
        for (var caseDO : reportService.getSuiteCaseList(suiteId)) {
            CaseRespVO vo = BeanUtils.toBean(caseDO, CaseRespVO.class);
            result.add(vo);
        }
        return success(result);
    }

    @GetMapping("/suite/unlinked-case-list")
    @Operation(summary = "还能加进用例集的用例（同产品、且还没加过）")
    @Parameter(name = "suiteId", description = "用例集编号", required = true, example = "95101")
    @PreAuthorize("@ss.hasPermission('zentao:testsuite:query')")
    public CommonResult<List<CaseRespVO>> getSuiteUnlinkedCaseList(
            @RequestParam("suiteId") Long suiteId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "module", required = false) Long module) {
        List<CaseRespVO> result = new ArrayList<>();
        for (var caseDO : reportService.getSuiteUnlinkedCaseList(suiteId, title, module)) {
            result.add(BeanUtils.toBean(caseDO, CaseRespVO.class));
        }
        return success(result);
    }

    @GetMapping("/type-list")
    @Operation(summary = "用例集类型列表")
    @PreAuthorize("@ss.hasPermission('zentao:testsuite:query')")
    public CommonResult<List<Map<String, String>>> getSuiteTypeList() {
        return success(List.of(
                Map.of("value", "public", "label", "公共"),
                Map.of("value", "private", "label", "私有")));
    }

}
