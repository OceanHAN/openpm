package cn.iocoder.yudao.module.zentao.controller.admin.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 产品创建/修改 Request VO")
@Data
public class ProductSaveReqVO {

    @Schema(description = "产品编号，新建时为空", example = "1")
    private Long id;

    @Schema(description = "所属项目集", example = "0")
    private Long program;

    @Schema(description = "所属产品线", example = "0")
    private Long line;

    @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "禅道研发管理平台")
    @NotBlank(message = "产品名称不能为空")
    @Size(max = 110, message = "产品名称长度不能超过 110 个字符")
    private String name;

    @Schema(description = "产品代号", example = "ZENTAO")
    @Size(max = 45, message = "产品代号长度不能超过 45 个字符")
    private String code;

    @Schema(description = "类型：normal/branch/platform", example = "normal")
    private String type;

    @Schema(description = "访问控制：open/private", example = "open")
    private String acl;

    @Schema(description = "产品经理", example = "admin")
    private String PO;

    @Schema(description = "测试负责人", example = "admin")
    private String QD;

    @Schema(description = "研发负责人", example = "admin")
    private String RD;

    @Schema(description = "产品描述")
    private String desc;

    @Schema(description = "排序", example = "0")
    private Integer order;

}
