package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 看板泳道新增/修改 Request VO")
@Data
public class KanbanLaneSaveReqVO {

    @Schema(description = "泳道编号（修改时必填）", example = "96301")
    private Long id;

    @Schema(description = "所属区域", requiredMode = Schema.RequiredMode.REQUIRED, example = "96201")
    @NotNull(message = "所属区域不能为空")
    private Long region;

    @Schema(description = "泳道名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "默认泳道")
    @NotBlank(message = "泳道名称不能为空")
    @Size(max = 255, message = "泳道名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "泳道类型：common 普通 / story 需求 / bug 缺陷 / task 任务", example = "common")
    private String type;

    @Schema(description = "泳道颜色", example = "#7ec5ff")
    private String color;

    @Schema(description = "分组方式", example = "")
    private String groupby;

    @Schema(description = "扩展值", example = "")
    private String extra;

    @Schema(description = "排序", example = "1")
    private Integer order;

}
