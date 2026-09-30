package cn.iocoder.yudao.module.zentao.controller.admin.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 禅道产品精简 Response VO")
@Data
public class ProductSimpleRespVO {

    @Schema(description = "产品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "产品名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "禅道研发管理平台")
    private String name;

    @Schema(description = "产品代号", example = "ZENTAO")
    private String code;

    @Schema(description = "状态", example = "normal")
    private String status;

    @Schema(description = "类型：normal 普通 / branch 多分支 / platform 多平台。分支页面据此决定文案与能否建分支", example = "normal")
    private String type;

}
