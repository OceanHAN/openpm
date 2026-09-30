package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 看板 Response VO")
@Data
public class KanbanRespVO {

    @Schema(description = "看板编号", example = "96101")
    private Long id;

    @Schema(description = "所属空间", example = "96001")
    private Long space;

    @Schema(description = "空间名称", example = "禅道研发空间")
    private String spaceName;

    @Schema(description = "看板名称", example = "禅道迁移看板")
    private String name;

    @Schema(description = "负责人", example = "admin")
    private String owner;

    @Schema(description = "团队")
    private String team;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "访问控制", example = "extend")
    private String acl;

    @Schema(description = "白名单")
    private String whitelist;

    @Schema(description = "是否启用归档功能", example = "1")
    private Integer archived;

    @Schema(description = "是否可执行", example = "0")
    private Integer performable;

    @Schema(description = "状态", example = "active")
    private String status;

    @Schema(description = "排序", example = "1")
    private Integer order;

    @Schema(description = "卡片显示数量", example = "0")
    private Integer displayCards;

    @Schema(description = "是否显示在制品数量", example = "1")
    private Integer showWIP;

    @Schema(description = "是否流式布局", example = "0")
    private Integer fluidBoard;

    @Schema(description = "列宽", example = "264")
    private Integer colWidth;

    @Schema(description = "列最小宽", example = "200")
    private Integer minColWidth;

    @Schema(description = "列最大宽", example = "384")
    private Integer maxColWidth;

    @Schema(description = "关联对象")
    private String object;

    @Schema(description = "对齐", example = "center")
    private String alignment;

    @Schema(description = "区域数量", example = "1")
    private Long regionCount;

    @Schema(description = "卡片数量", example = "2")
    private Long cardCount;

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

}
