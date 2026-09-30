package cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 用例集新增/修改 Request VO")
@Data
public class TestSuiteSaveReqVO {

    @Schema(description = "用例集编号（修改时必填）", example = "95101")
    private Long id;

    @Schema(description = "所属产品", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属产品不能为空")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "冒烟用例集")
    @NotBlank(message = "用例集名称不能为空")
    private String name;

    @Schema(description = "描述", example = "每次发版必跑的几条")
    private String desc;

    @Schema(description = "类型：public 公共 / private 私有", example = "public")
    private String type;

    @Schema(description = "排序", example = "1")
    private Integer order;

}
