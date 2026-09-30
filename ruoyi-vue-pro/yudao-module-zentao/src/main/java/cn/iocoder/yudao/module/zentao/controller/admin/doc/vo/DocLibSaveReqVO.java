package cn.iocoder.yudao.module.zentao.controller.admin.doc.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 文档库新增/修改 Request VO")
@Data
public class DocLibSaveReqVO {

    @Schema(description = "文档库编号（修改时必填）", example = "91001")
    private Long id;

    @Schema(description = "库类型：product/project/execution/custom", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "product")
    @NotBlank(message = "文档库类型不能为空")
    private String type;

    @Schema(description = "所属产品（type=product 时必填）", example = "1")
    private Long product;

    @Schema(description = "所属项目（type=project 时必填）", example = "1")
    private Long project;

    @Schema(description = "所属执行（type=execution 时必填）", example = "90001")
    private Long execution;

    @Schema(description = "父空间（type=custom 时必填）", example = "91005")
    private Long parent;

    @Schema(description = "库名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "产品文档库")
    @NotBlank(message = "文档库名称不能为空")
    @Size(max = 255, message = "文档库名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "权限：open 公开 / private 私有", example = "open")
    private String acl;

    @Schema(description = "私有库可见角色，逗号列表", example = "1,2")
    private String groups;

    @Schema(description = "私有库可见用户，逗号列表", example = "admin,dev1")
    private String users;

    @Schema(description = "库描述", example = "产品线对外文档")
    @Size(max = 2000, message = "库描述长度不能超过 2000 个字符")
    private String desc;

    @Schema(description = "排序", example = "1")
    private Integer order;

}
