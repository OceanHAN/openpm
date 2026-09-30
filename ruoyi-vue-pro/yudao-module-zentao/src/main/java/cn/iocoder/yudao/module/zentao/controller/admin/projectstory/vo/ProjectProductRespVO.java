package cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 项目关联产品 Response VO")
@Data
public class ProjectProductRespVO {

    @Schema(description = "关系编号", example = "1")
    private Long id;

    @Schema(description = "项目/执行编号", example = "1")
    private Long project;

    @Schema(description = "产品编号", example = "1")
    private Long product;

    @Schema(description = "产品名称", example = "禅道研发管理平台")
    private String productName;

    @Schema(description = "产品类型：normal/branch/platform", example = "normal")
    private String productType;

    @Schema(description = "分支/平台", example = "0")
    private Long branch;

    @Schema(description = "分支/平台名称", example = "主干")
    private String branchName;

    @Schema(description = "关联的计划，逗号列表", example = "1")
    private String plan;

    @Schema(description = "计划名称列表", example = "[\"V1.0 迭代计划\"]")
    private java.util.List<String> planNames;

    @Schema(description = "该产品已纳入项目范围的需求数", example = "3")
    private Long storyCount;

}
