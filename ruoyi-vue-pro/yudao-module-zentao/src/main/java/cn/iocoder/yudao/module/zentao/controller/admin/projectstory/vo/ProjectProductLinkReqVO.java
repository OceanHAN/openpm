package cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 项目关联产品 Request VO
 *
 * <p>项目关联产品后，该产品的需求才会出现在「可关联需求」候选里。
 * {@code plans} 用于限制「只吃某些计划下的需求」（禅道 {@code zt_projectproduct.plan} 是逗号列表）。
 */
@Schema(description = "管理后台 - 项目关联产品 Request VO")
@Data
public class ProjectProductLinkReqVO {

    @Schema(description = "项目/执行编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "项目/执行编号不能为空")
    private Long project;

    @Schema(description = "产品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "产品编号不能为空")
    private Long product;

    @Schema(description = "分支/平台（单值，0 主干）", example = "0")
    private Long branch;

    @Schema(description = "只关联这些计划下的需求", example = "[1]")
    private List<Long> plans;

}
