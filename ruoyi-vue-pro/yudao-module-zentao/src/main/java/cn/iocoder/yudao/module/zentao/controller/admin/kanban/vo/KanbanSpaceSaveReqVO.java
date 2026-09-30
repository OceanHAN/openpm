package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 看板空间新增/修改 Request VO")
@Data
public class KanbanSpaceSaveReqVO {

    @Schema(description = "空间编号（修改时必填）", example = "96001")
    private Long id;

    @Schema(description = "空间名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "禅道研发空间")
    @NotBlank(message = "空间名称不能为空")
    @Size(max = 255, message = "空间名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "空间类型：private 私人 / cooperation 协作 / public 公共", example = "cooperation")
    private String type;

    @Schema(description = "负责人", example = "admin")
    private String owner;

    @Schema(description = "团队成员，逗号列表", example = "admin,tester")
    private String team;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "访问控制：open 公开 / private 私有", example = "open")
    private String acl;

    @Schema(description = "白名单，逗号列表")
    private String whitelist;

    @Schema(description = "排序", example = "1")
    private Integer order;

}
