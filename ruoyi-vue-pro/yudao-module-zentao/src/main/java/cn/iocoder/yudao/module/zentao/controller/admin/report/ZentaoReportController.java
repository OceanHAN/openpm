package cn.iocoder.yudao.module.zentao.controller.admin.report;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zentao.controller.admin.report.vo.AnnualDataRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.report.vo.ReportOptionRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.report.vo.ReportOutputRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.report.vo.ReminderRespVO;
import cn.iocoder.yudao.module.zentao.service.report.ZentaoReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 报表（禅道 {@code module/report}）。
 *
 * <p><b>类名带 {@code Zentao} 前缀是必须的</b>：禅道的「测试报告」模块在本项目里就叫
 * {@code testreport.ReportController} / {@code testreport.ReportServiceImpl}，
 * 组件扫描按短类名首字母小写生成 bean 名，同名会直接
 * {@code ConflictingBeanDefinitionException: bean name 'reportController'}（坑位 #25 再次上演）。
 *
 * <p>禅道这个模块只有 4 个 action：{@code index}（跳到年度数据）、{@code annualData}（年度数据页）、
 * {@code remind}（发每日提醒邮件）、以及给 API 用的年度数据 JSON。本实现拆成：
 * {@code options}（筛选项）、{@code annual-data}（年度数据）、{@code reminder-list}（提醒数据，
 * 不发邮件）、{@code output}（产出统计）、{@code project-status}（项目状态总览）。
 */
@Tag(name = "管理后台 - 禅道报表")
@RestController
@RequestMapping("/zentao/report")
public class ZentaoReportController {

    // 字段名必须带前缀：@Resource 是「先按字段名注入」的，
    // 叫 reportService 会命中「测试报告」模块同名的那个 bean，然后报
    // 「The bean 'reportService' could not be injected because it is a JDK dynamic proxy」（坑位 #25）
    @Resource
    private ZentaoReportService zentaoReportService;

    @GetMapping("/options")
    @Operation(summary = "筛选项", description = "可选年份（从第一条动作算起）、部门树、人员列表")
    @PreAuthorize("@ss.hasPermission('zentao:report:query')")
    public CommonResult<ReportOptionRespVO> getOptions() {
        return success(zentaoReportService.getOptions());
    }

    @GetMapping("/annual-data")
    @Operation(summary = "年度数据",
            description = "三种视角：不传参数=全公司、传 dept=部门（含子部门）、传 account=个人。"
                    + "返回基础指标 + 贡献（含历年雷达）+ 产品/执行统计 + 需求/任务/缺陷/用例的状态与月度趋势")
    @Parameter(name = "year", description = "年份，空则取今年（1~2 月自动取去年）", example = "2026")
    @Parameter(name = "dept", description = "部门编号（部门视角）", example = "103")
    @Parameter(name = "account", description = "账号（个人视角）", example = "admin")
    @PreAuthorize("@ss.hasPermission('zentao:report:query')")
    public CommonResult<AnnualDataRespVO> getAnnualData(
            @RequestParam(value = "year", required = false) String year,
            @RequestParam(value = "dept", required = false) Long dept,
            @RequestParam(value = "account", required = false) String account) {
        return success(zentaoReportService.getAnnualData(year, dept, account));
    }

    @GetMapping("/reminder-list")
    @Operation(summary = "每日提醒", description = "按人聚合「快到期的、没做完的」任务/缺陷/待办/测试单/看板卡片；"
            + "禅道在这里直接发邮件，本实现只产出数据")
    @PreAuthorize("@ss.hasPermission('zentao:report:query')")
    public CommonResult<List<ReminderRespVO>> getReminderList() {
        return success(zentaoReportService.getReminderList());
    }

    @GetMapping("/output")
    @Operation(summary = "产出统计", description = "各类对象本年各动作的条数（创建/编辑/关闭/完成/解决…）")
    @Parameter(name = "year", description = "年份，空则取今年", example = "2026")
    @Parameter(name = "account", description = "只看某个人", example = "admin")
    @PreAuthorize("@ss.hasPermission('zentao:report:query')")
    public CommonResult<List<ReportOutputRespVO>> getOutput(
            @RequestParam(value = "year", required = false) String year,
            @RequestParam(value = "account", required = false) String account) {
        return success(zentaoReportService.getOutput(year, account));
    }

    @GetMapping("/project-status")
    @Operation(summary = "项目状态总览", description = "按团队成员过滤的项目状态分布")
    @Parameter(name = "account", description = "只看某个人参与的项目", example = "admin")
    @PreAuthorize("@ss.hasPermission('zentao:report:query')")
    public CommonResult<Map<String, Integer>> getProjectStatusOverview(
            @RequestParam(value = "account", required = false) String account) {
        return success(zentaoReportService.getProjectStatusOverview(account));
    }

}
