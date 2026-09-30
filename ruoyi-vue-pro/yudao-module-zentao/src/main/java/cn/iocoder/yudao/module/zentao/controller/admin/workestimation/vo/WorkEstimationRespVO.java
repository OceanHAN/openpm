package cn.iocoder.yudao.module.zentao.controller.admin.workestimation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 工作量估算 Response VO")
@Data
public class WorkEstimationRespVO {

    @Schema(description = "编号", example = "99201")
    private Long id;

    @Schema(description = "项目编号", example = "1")
    private Long project;

    @Schema(description = "规模（人天）", example = "100")
    private BigDecimal scale;

    @Schema(description = "生产率", example = "5")
    private BigDecimal productivity;

    @Schema(description = "工期（天，算出来的）", example = "20")
    private BigDecimal duration;

    @Schema(description = "单位人工成本（元/人天）", example = "1500")
    private BigDecimal unitLaborCost;

    @Schema(description = "每天工时", example = "8")
    private BigDecimal dayHour;

    @Schema(description = "总人工成本（算出来的）", example = "240000")
    private BigDecimal totalLaborCost;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

    @Schema(description = "指派时间")
    private LocalDateTime assignedDate;

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

    @Schema(description = "最后修改人", example = "admin")
    private String editedBy;

    @Schema(description = "最后修改时间")
    private LocalDateTime editedDate;

}
