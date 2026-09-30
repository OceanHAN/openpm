package cn.iocoder.yudao.module.zentao.controller.admin.entry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 应用接入校验 Response VO")
@Data
public class EntryVerifyRespVO {

    @Schema(description = "应用编号", example = "1")
    private Long entryId;

    @Schema(description = "应用名称", example = "OA")
    private String name;

    @Schema(description = "应用代号", example = "oa")
    private String code;

    @Schema(description = "绑定的账号", example = "admin")
    private String account;

    @Schema(description = "是否免密登录", example = "0")
    private Integer freePasswd;

    @Schema(description = "命中的校验方式：time（带时间戳）/ query（查询串）", example = "time")
    private String tokenMode;

    @Schema(description = "本次调用后记录下的 calledTime", example = "1700000000")
    private Integer calledTime;

    @Schema(description = "账号对应的用户编号", example = "1")
    private Long userId;

    @Schema(description = "账号对应的用户姓名", example = "管理员")
    private String userNickname;

    @Schema(description = "调用日志编号", example = "1")
    private Long logId;

    @Schema(description = "说明（免密登录是重定向到首页，见 yudao 侧认证说明）")
    private String message;

}
