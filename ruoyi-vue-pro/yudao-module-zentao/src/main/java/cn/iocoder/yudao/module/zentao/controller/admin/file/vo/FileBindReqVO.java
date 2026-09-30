package cn.iocoder.yudao.module.zentao.controller.admin.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 按 gid 绑定附件到对象
 *
 * <p>对应禅道的 {@code file->updateObjectID($uid, $objectID, $objectType)}：
 * 新建对象时先上传附件（带一个临时 gid），对象保存成功后再调用本接口把附件挂上去。
 */
@Schema(description = "管理后台 - 附件按 gid 绑定 Request VO")
@Data
public class FileBindReqVO {

    @Schema(description = "临时分组 id", requiredMode = Schema.RequiredMode.REQUIRED, example = "5f3a...")
    @NotBlank(message = "gid 不能为空")
    private String gid;

    @Schema(description = "所属对象类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "story")
    @NotBlank(message = "对象类型不能为空")
    private String objectType;

    @Schema(description = "所属对象编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "对象编号不能为空")
    private Long objectID;

}
