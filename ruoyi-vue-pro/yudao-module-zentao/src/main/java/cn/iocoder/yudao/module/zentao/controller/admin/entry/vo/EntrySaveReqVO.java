package cn.iocoder.yudao.module.zentao.controller.admin.entry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "管理后台 - 应用接入创建/修改 Request VO")
@Data
public class EntrySaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "应用名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "OA")
    @NotEmpty(message = "应用名称不能为空")
    private String name;

    /**
     * 代号规则来自禅道 {@code $lang->entry->note->code}：必须为字母或数字的组合，
     * DAO 层的 {@code check('code', 'code')} 就是这条正则。
     */
    @Schema(description = "应用代号（字母或数字的组合）", requiredMode = Schema.RequiredMode.REQUIRED, example = "oa")
    @NotEmpty(message = "应用代号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "应用代号必须为字母或数字的组合")
    private String code;

    @Schema(description = "绑定账号（免密登录时可不填）", example = "admin")
    private String account;

    @Schema(description = "密钥（留空则自动生成 32 位）", example = "c0f0…")
    private String key;

    @Schema(description = "是否免密登录：1 开启", example = "0")
    private Integer freePasswd;

    @Schema(description = "允许的来源 IP，多个用逗号隔开，支持 192.168.1.* 与 CIDR", example = "*")
    private String ip;

    @Schema(description = "描述")
    private String desc;

}
