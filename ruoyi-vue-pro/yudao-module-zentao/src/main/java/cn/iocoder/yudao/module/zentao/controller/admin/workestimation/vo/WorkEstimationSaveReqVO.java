package cn.iocoder.yudao.module.zentao.controller.admin.workestimation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 工作量估算 Request VO")
@Data
public class WorkEstimationSaveReqVO {

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "项目编号不能为空")
    private Long project;

    @Schema(description = "规模（人天）", example = "100")
    @DecimalMin(value = "0", message = "规模不能为负数")
    private BigDecimal scale;

    @Schema(description = "生产率", example = "5")
    @DecimalMin(value = "0", message = "生产率不能为负数")
    private BigDecimal productivity;

    @Schema(description = "单位人工成本（元/人天）", example = "1500")
    @DecimalMin(value = "0", message = "单位人工成本不能为负数")
    private BigDecimal unitLaborCost;

    @Schema(description = "每天工时，默认 8", example = "8")
    @DecimalMin(value = "0", message = "每天工时不能为负数")
    private BigDecimal dayHour;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

    // 注意：duration 与 totalLaborCost 是**算出来的**，不接受入参

}
