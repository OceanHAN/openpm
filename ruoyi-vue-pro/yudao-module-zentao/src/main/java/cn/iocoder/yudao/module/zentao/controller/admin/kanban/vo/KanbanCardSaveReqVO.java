package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 看板卡片新增/修改 Request VO")
@Data
public class KanbanCardSaveReqVO {

    @Schema(description = "卡片编号（修改时必填）", example = "96501")
    private Long id;

    @Schema(description = "所属看板（新建时必填）", example = "96101")
    private Long kanban;

    @Schema(description = "所属泳道（新建时必填）", example = "96301")
    private Long lane;

    @Schema(description = "所属列（新建时必填）", example = "96402")
    @NotNull(message = "所属列不能为空")
    private Long column;

    @Schema(description = "卡片标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "打通登录链路")
    @NotBlank(message = "卡片标题不能为空")
    @Size(max = 255, message = "卡片标题长度不能超过 255 个字符")
    private String name;

    @Schema(description = "优先级", example = "2")
    private Integer pri;

    @Schema(description = "指派给（逗号列表）", example = "dev1")
    private String assignedTo;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "预计开始", example = "2026-03-01")
    private LocalDate begin;

    @Schema(description = "截止日期", example = "2026-03-10")
    private LocalDate end;

    @Schema(description = "预计工时", example = "8")
    private BigDecimal estimate;

    @Schema(description = "卡片颜色：空 / #937c5a / #fc5959 / #ff9f46", example = "#fff")
    private String color;

}
