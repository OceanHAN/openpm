package cn.iocoder.yudao.module.zentao.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "管理后台 - 产品计划创建/修改 Request VO")
@Data
public class PlanSaveReqVO {

    @Schema(description = "计划编号，新建时为空", example = "1")
    private Long id;

    @Schema(description = "所属产品", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属产品不能为空")
    private Long product;

    @Schema(description = "分支/平台。多分支产品必填，可多选（禅道里存成逗号列表）", example = "[1,2]")
    private List<Long> branches;

    @Schema(description = "父计划编号。不传为独立计划", example = "0")
    private Long parent;

    @Schema(description = "计划名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "V1.0 迭代计划")
    @NotBlank(message = "计划名称不能为空")
    @Size(max = 90, message = "计划名称长度不能超过 90 个字符")
    private String title;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "开始日期。传 null 且 future=true 表示待定", example = "2026-01-05")
    private LocalDate begin;

    @Schema(description = "结束日期。传 null 且 future=true 表示待定", example = "2026-02-28")
    private LocalDate end;

    @Schema(description = "是否待定计划。为 true 时 begin/end 会被写成禅道的待定哨兵日期 2030-01-01",
            example = "false")
    private Boolean future;

}
