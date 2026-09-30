package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 看板泳道 Response VO")
@Data
public class KanbanLaneRespVO {

    @Schema(description = "泳道编号", example = "96301")
    private Long id;

    @Schema(description = "所属区域", example = "96201")
    private Long region;

    @Schema(description = "所属分组", example = "96251")
    private Long groupId;

    @Schema(description = "泳道名称", example = "默认泳道")
    private String name;

    @Schema(description = "泳道类型", example = "common")
    private String type;

    @Schema(description = "泳道类型名", example = "普通泳道")
    private String typeName;

    @Schema(description = "泳道颜色", example = "#7ec5ff")
    private String color;

    @Schema(description = "排序", example = "1")
    private Integer order;

}
