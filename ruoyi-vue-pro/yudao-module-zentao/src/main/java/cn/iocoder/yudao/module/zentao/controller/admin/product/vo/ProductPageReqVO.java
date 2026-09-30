package cn.iocoder.yudao.module.zentao.controller.admin.product.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 产品分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductPageReqVO extends PageParam {

    @Schema(description = "产品名称，模糊匹配", example = "禅道")
    private String name;

    @Schema(description = "产品代号，模糊匹配", example = "ZENTAO")
    private String code;

    @Schema(description = "状态：normal/closed", example = "normal")
    private String status;

    @Schema(description = "类型：normal/branch/platform", example = "normal")
    private String type;

    @Schema(description = "所属项目集", example = "0")
    private Long program;

    @Schema(description = "产品经理", example = "admin")
    private String PO;

}
