package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 看板列 Response VO")
@Data
public class KanbanColumnRespVO {

    @Schema(description = "列编号", example = "96402")
    private Long id;

    @Schema(description = "所属分组", example = "96251")
    private Long groupId;

    @Schema(description = "所属区域", example = "96201")
    private Long region;

    @Schema(description = "父列：0 顶层 / >0 子列 / -1 已拆分", example = "0")
    private Long parent;

    @Schema(description = "列名称", example = "进行中")
    private String name;

    @Schema(description = "列颜色", example = "#2b519c")
    private String color;

    @Schema(description = "在制品上限（WIP），-1=不限", example = "3")
    private Integer limit;

    @Schema(description = "排序", example = "2")
    private Integer order;

    @Schema(description = "是否归档", example = "false")
    private Boolean archived;

}
