package cn.iocoder.yudao.module.zentao.service.report;

import cn.iocoder.yudao.module.zentao.controller.admin.report.vo.AnnualDataRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.report.vo.ReportOptionRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.report.vo.ReportOutputRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.report.vo.ReminderRespVO;

import java.util.List;
import java.util.Map;

/**
 * 报表服务（禅道 {@code module/report}）。
 *
 * <p><b>接口名带 {@code Zentao} 前缀</b>的原因与 Controller 相同：
 * 「测试报告」模块里已经有 {@code testreport.ReportService} / {@code ReportServiceImpl} 了。
 *
 * <p>全是**只读聚合**：不建表、不写库，数据都来自已经迁移的那些模块
 * （{@code zt_action} / {@code zt_story} / {@code zt_task} / {@code zt_bug} / {@code zt_case} /
 * {@code zt_todo} / {@code zt_effort} / {@code zt_product} / {@code zt_project} …）。
 */
public interface ZentaoReportService {

    /** 筛选项：可选年份 / 部门 / 人员 */
    ReportOptionRespVO getOptions();

    /**
     * 年度数据
     *
     * @param year    年份，空则取「今年或去年」（禅道 minMonth 规则）
     * @param deptId  部门编号（部门视角）
     * @param account 账号（个人视角）
     */
    AnnualDataRespVO getAnnualData(String year, Long deptId, String account);

    /** 每日提醒：按人聚合「快到期的、没做完的」任务/缺陷/待办/测试单/看板卡片 */
    List<ReminderRespVO> getReminderList();

    /** 产出统计：各类对象本年各动作的条数 */
    List<ReportOutputRespVO> getOutput(String year, String account);

    /** 项目状态总览（按团队成员过滤）：status -> 项目数 */
    Map<String, Integer> getProjectStatusOverview(String account);

}
