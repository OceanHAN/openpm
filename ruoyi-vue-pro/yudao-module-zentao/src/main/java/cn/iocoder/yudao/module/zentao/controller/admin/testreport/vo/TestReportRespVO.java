package cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - 测试报告 Response VO")
@Data
public class TestReportRespVO {

    @Schema(description = "报告编号", example = "95201")
    private Long id;

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "90001")
    private Long execution;

    @Schema(description = "汇总的测试单，逗号列表", example = "94101,94103")
    private String tasks;

    @Schema(description = "测试单名称", example = "V1.0 冒烟测试、V1.0 回归测试")
    private String taskNames;

    @Schema(description = "涉及的构建", example = "1,3")
    private String builds;

    @Schema(description = "标题", example = "V1.0 测试报告")
    private String title;

    @Schema(description = "统计开始日期")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate begin;

    @Schema(description = "统计结束日期")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate end;

    @Schema(description = "负责人", example = "admin")
    private String owner;

    @Schema(description = "结论 / 人工总结")
    private String report;

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

    // ==================== 以下为「读的时候现算」的汇总 ====================

    @Schema(description = "涉及的用例数（去重）", example = "5")
    private Integer caseCount;

    @Schema(description = "有执行记录的用例数", example = "5")
    private Integer runCaseCount;

    @Schema(description = "执行次数（范围内）", example = "6")
    private Integer resultCount;

    @Schema(description = "失败数（每条 run 取最后一次结果，非 pass 即算失败）", example = "1")
    private Integer failCount;

    @Schema(description = "通过数", example = "4")
    private Integer passCount;

    @Schema(description = "涉及的需求", example = "1,4")
    private String stories;

    @Schema(description = "涉及的缺陷", example = "1")
    private String bugs;

    @Schema(description = "涉及的用例", example = "93101,93102")
    private String cases;

    @Schema(description = "按用例汇总的执行明细")
    private List<TestReportCaseVO> caseSummaries;

}
