package cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - 测试报告新增/修改 Request VO")
@Data
public class TestReportSaveReqVO {

    @Schema(description = "报告编号（修改时必填）", example = "95201")
    private Long id;

    @Schema(description = "所属产品", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属产品不能为空")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "90001")
    private Long execution;

    @Schema(description = "汇总的测试单，逗号列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "94101,94103")
    @NotBlank(message = "至少要选择一个要汇总的测试单")
    private String tasks;

    @Schema(description = "涉及的构建，逗号列表", example = "1,3")
    private String builds;

    @Schema(description = "报告标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "V1.0 测试报告")
    @NotBlank(message = "报告标题不能为空")
    private String title;

    @Schema(description = "统计开始日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-02-01")
    @NotNull(message = "统计开始日期不能为空")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate begin;

    @Schema(description = "统计结束日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-03-10")
    @NotNull(message = "统计结束日期不能为空")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate end;

    @Schema(description = "负责人", requiredMode = Schema.RequiredMode.REQUIRED, example = "admin")
    @NotBlank(message = "负责人不能为空")
    private String owner;

    @Schema(description = "结论 / 人工总结", example = "整体质量可控")
    private String report;

}
