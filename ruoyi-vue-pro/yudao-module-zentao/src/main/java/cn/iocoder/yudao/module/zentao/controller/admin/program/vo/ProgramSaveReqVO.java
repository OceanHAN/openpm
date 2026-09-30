package cn.iocoder.yudao.module.zentao.controller.admin.program.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 项目集创建/修改 Request VO")
@Data
public class ProgramSaveReqVO {

    @Schema(description = "项目集编号，新建时为空", example = "9001")
    private Long id;

    @Schema(description = "上级项目集，0 表示顶级项目集", example = "0")
    private Long parent;

    @Schema(description = "项目集名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "禅道迁移项目集")
    @NotBlank(message = "项目集名称不能为空")
    private String name;

    @Schema(description = "项目集代号", example = "ZENTAO-PGM")
    private String code;

    @Schema(description = "负责人（禅道 PM）", example = "admin")
    private String PM;

    @Schema(description = "预算", example = "1000000.00")
    private BigDecimal budget;

    @Schema(description = "预算币种", example = "CNY")
    private String budgetUnit;

    @Schema(description = "计划开始", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-01-01")
    @NotNull(message = "计划开始日期不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate begin;

    @Schema(description = "计划结束", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-12-31")
    @NotNull(message = "计划结束日期不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate end;

    @Schema(description = "优先级 1~4", example = "1")
    private Integer pri;

    @Schema(description = "访问控制：open/private", example = "open")
    private String acl;

    @Schema(description = "项目集描述")
    private String desc;

}
