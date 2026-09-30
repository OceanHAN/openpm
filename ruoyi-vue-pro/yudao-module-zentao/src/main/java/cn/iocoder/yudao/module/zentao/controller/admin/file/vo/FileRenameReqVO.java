package cn.iocoder.yudao.module.zentao.controller.admin.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 附件重命名 Request VO")
@Data
public class FileRenameReqVO {

    @Schema(description = "附件编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "附件编号不能为空")
    private Long id;

    @Schema(description = "新的文件名", requiredMode = Schema.RequiredMode.REQUIRED, example = "需求说明书-v2.docx")
    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过 255 个字符")
    private String title;

}
