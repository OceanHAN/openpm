package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 看板视图数据（一次把整块板取回来）。
 *
 * <p>结构对应禅道的「区域 → 分组 → 泳道 × 列 + 卡片」：
 * <pre>
 *   regions[].columns[]           该区域的所有列（纵向，含 WIP 与归档位）
 *   regions[].lanes[].cells[]     该泳道在每个列下的格子（卡片 + 是否超 WIP）
 * </pre>
 * 禅道把 WIP 超限做成**界面提示**（{@code cards/limit} 标红），后端不阻止移动，
 * 所以这里返回 {@code overWip} 供前端标红，而不是报错。
 */
@Schema(description = "管理后台 - 看板视图数据 Response VO")
@Data
public class KanbanDataRespVO {

    @Schema(description = "看板本体（含 showWIP / displayCards / colWidth 等展示参数）")
    private KanbanRespVO kanban;

    @Schema(description = "区域列表")
    private List<Region> regions = new ArrayList<>();

    @Schema(description = "看板区域")
    @Data
    public static class Region {

        @Schema(description = "区域编号", example = "96201")
        private Long id;

        @Schema(description = "区域名称", example = "默认区域")
        private String name;

        @Schema(description = "分组编号", example = "96251")
        private Long groupId;

        @Schema(description = "列（纵向）")
        private List<KanbanColumnRespVO> columns = new ArrayList<>();

        @Schema(description = "泳道（横向）")
        private List<Lane> lanes = new ArrayList<>();

    }

    @Schema(description = "看板泳道")
    @Data
    public static class Lane {

        @Schema(description = "泳道编号", example = "96301")
        private Long id;

        @Schema(description = "泳道名称", example = "默认泳道")
        private String name;

        @Schema(description = "泳道类型", example = "common")
        private String type;

        @Schema(description = "泳道颜色", example = "#7ec5ff")
        private String color;

        @Schema(description = "格子（每个列一个）")
        private List<Cell> cells = new ArrayList<>();

    }

    @Schema(description = "看板格子（泳道 × 列）")
    @Data
    public static class Cell {

        @Schema(description = "列编号", example = "96402")
        private Long columnId;

        @Schema(description = "列名称", example = "进行中")
        private String columnName;

        @Schema(description = "在制品上限，-1=不限", example = "3")
        private Integer limit;

        @Schema(description = "卡片数量", example = "1")
        private Integer cardCount;

        @Schema(description = "是否超出在制品上限（前端标红用，后端不阻止）", example = "false")
        private Boolean overWip;

        @Schema(description = "卡片（按格子里的顺序）")
        private List<KanbanCardRespVO> cards = new ArrayList<>();

    }

}
