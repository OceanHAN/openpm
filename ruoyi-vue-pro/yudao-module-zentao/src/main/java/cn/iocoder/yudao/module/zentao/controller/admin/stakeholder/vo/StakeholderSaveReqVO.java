package cn.iocoder.yudao.module.zentao.controller.admin.stakeholder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 干系人创建/修改 Request VO")
@Data
public class StakeholderSaveReqVO {

    @Schema(description = "干系人编号，修改时必填", example = "99101")
    private Long id;

    @Schema(description = "对象类型：program / project", requiredMode = Schema.RequiredMode.REQUIRED, example = "project")
    @NotBlank(message = "对象类型不能为空")
    private String objectType;

    @Schema(description = "对象编号（项目集/项目）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "对象编号不能为空")
    private Long objectID;

    @Schema(description = "账号（from=outside 时是外部人员名字）", requiredMode = Schema.RequiredMode.REQUIRED, example = "admin")
    @NotBlank(message = "干系人不能为空")
    private String user;

    @Schema(description = "来源：team / company / outside", requiredMode = Schema.RequiredMode.REQUIRED, example = "team")
    @NotBlank(message = "来源不能为空")
    private String from;

    @Schema(description = "是否关键干系人：0 否 / 1 是", example = "0")
    private Integer key;

}
