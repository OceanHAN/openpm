package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 看板新增/修改 Request VO")
@Data
public class KanbanSaveReqVO {

    @Schema(description = "看板编号（修改时必填）", example = "96101")
    private Long id;

    @Schema(description = "所属空间", requiredMode = Schema.RequiredMode.REQUIRED, example = "96001")
    @NotNull(message = "所属空间不能为空")
    private Long space;

    @Schema(description = "看板名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "禅道迁移看板")
    @NotBlank(message = "看板名称不能为空")
    @Size(max = 255, message = "看板名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "负责人", example = "admin")
    private String owner;

    @Schema(description = "团队（逗号列表）", example = "admin,tester")
    private String team;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "访问控制：open / private / extend（继承空间权限）", example = "extend")
    private String acl;

    @Schema(description = "白名单（逗号列表）")
    private String whitelist;

    @Schema(description = "是否启用归档功能：1 启用 / 0 不启用", example = "1")
    private Integer archived;

    @Schema(description = "是否可执行", example = "0")
    private Integer performable;

    @Schema(description = "卡片显示数量，0=不限", example = "0")
    private Integer displayCards;

    @Schema(description = "是否显示在制品数量（卡片数/限额）", example = "1")
    private Integer showWIP;

    @Schema(description = "是否流式布局", example = "0")
    private Integer fluidBoard;

    @Schema(description = "列宽（流式关闭时）", example = "264")
    private Integer colWidth;

    @Schema(description = "列最小宽（流式开启时）", example = "200")
    private Integer minColWidth;

    @Schema(description = "列最大宽（流式开启时）", example = "384")
    private Integer maxColWidth;

    @Schema(description = "关联对象", example = "")
    private String object;

    @Schema(description = "对齐：center 居中 / left 居左", example = "center")
    private String alignment;

    @Schema(description = "排序", example = "1")
    private Integer order;

}
