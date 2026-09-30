package cn.iocoder.yudao.module.zentao.controller.admin.branch.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 分支创建/修改 Request VO")
@Data
public class BranchSaveReqVO {

    @Schema(description = "分支编号，新建时为空", example = "1")
    private Long id;

    @Schema(description = "所属产品", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "所属产品不能为空")
    private Long product;

    @Schema(description = "分支/平台名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "企业版")
    @NotBlank(message = "名称不能为空")
    @Size(max = 255, message = "名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "描述")
    @Size(max = 255, message = "描述长度不能超过 255 个字符")
    private String desc;

    @Schema(description = "是否设为默认分支", example = "false")
    private Boolean setDefault;

}
