package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 看板区域 Response VO")
@Data
public class KanbanRegionRespVO {

    @Schema(description = "区域编号", example = "96201")
    private Long id;

    @Schema(description = "所属看板", example = "96101")
    private Long kanban;

    @Schema(description = "所属空间", example = "96001")
    private Long space;

    @Schema(description = "区域名称", example = "默认区域")
    private String name;

    @Schema(description = "排序", example = "1")
    private Integer order;

    @Schema(description = "分组编号（泳道与列都挂在这个分组下）", example = "96251")
    private Long groupId;

    @Schema(description = "泳道数量", example = "2")
    private Long laneCount;

    @Schema(description = "列数量", example = "4")
    private Long columnCount;

}
