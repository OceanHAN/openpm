package cn.iocoder.yudao.module.zentao.controller.admin.entry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "管理后台 - 应用接入校验 Request VO")
@Data
public class EntryVerifyReqVO {

    @Schema(description = "应用代号", requiredMode = Schema.RequiredMode.REQUIRED, example = "oa")
    @NotEmpty(message = "缺少 code 参数")
    private String code;

    @Schema(description = "签名", requiredMode = Schema.RequiredMode.REQUIRED, example = "d41d8cd98f00b204e9800998ecf8427e")
    @NotEmpty(message = "缺少 token 参数")
    private String token;

    @Schema(description = "时间戳（秒或毫秒）。带上它时走 md5(code+key+time) 校验，并且必须大于上次调用时间防重放",
            example = "1700000000")
    private String time;

    @Schema(description = "不带时间戳时的查询串（不含 token 参数，如 m=user&f=apilogin&account=admin）")
    private String query;

    @Schema(description = "免密登录时指定的账号（禅道 user.apilogin 的 account 参数）", example = "admin")
    private String account;

    @Schema(description = "模块名（对应禅道 GET 的 m 参数，仅用于日志/语义）", example = "user")
    private String module;

    @Schema(description = "方法名（对应禅道 GET 的 f 参数，仅用于日志/语义）", example = "apilogin")
    private String method;

    @Schema(description = "被调用的 URL（写入 zt_log.url）", example = "/index.php?m=user&f=apilogin")
    private String url;

    @Schema(description = "调用方 IP（不传则取请求真实 IP）", example = "127.0.0.1")
    private String clientIp;

}
