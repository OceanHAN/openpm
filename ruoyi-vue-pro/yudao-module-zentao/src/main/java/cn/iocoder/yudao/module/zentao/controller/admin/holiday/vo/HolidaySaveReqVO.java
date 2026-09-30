package cn.iocoder.yudao.module.zentao.controller.admin.holiday.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - 节假日新增/修改 Request VO")
@Data
public class HolidaySaveReqVO {

    @Schema(description = "编号（修改时必填）", example = "1")
    private Long id;

    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "国庆节")
    @NotBlank(message = "名称不能为空")
    private String name;

    @Schema(description = "类型：holiday 假期 / working 补班", example = "holiday")
    private String type;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "开始日期（含）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-01")
    @NotNull(message = "开始日期不能为空")
    private LocalDate begin;

    @Schema(description = "结束日期（含）", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-10-07")
    @NotNull(message = "结束日期不能为空")
    private LocalDate end;

}
