package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 看板空间 Response VO")
@Data
public class KanbanSpaceRespVO {

    @Schema(description = "空间编号", example = "96001")
    private Long id;

    @Schema(description = "空间名称", example = "禅道研发空间")
    private String name;

    @Schema(description = "空间类型", example = "cooperation")
    private String type;

    @Schema(description = "空间类型名", example = "协作空间")
    private String typeName;

    @Schema(description = "负责人", example = "admin")
    private String owner;

    @Schema(description = "团队成员")
    private String team;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "访问控制", example = "open")
    private String acl;

    @Schema(description = "白名单")
    private String whitelist;

    @Schema(description = "状态", example = "active")
    private String status;

    @Schema(description = "排序", example = "1")
    private Integer order;

    @Schema(description = "空间下的看板数量", example = "1")
    private Long kanbanCount;

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

}
