package cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "管理后台 - Webhook 发送 Request VO")
@Data
public class WebhookSendReqVO {

    @Schema(description = "对象类型，必须在禅道 objectTypes 白名单里", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "story")
    @NotEmpty(message = "对象类型不能为空")
    private String objectType;

    @Schema(description = "对象编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long objectID;

    @Schema(description = "动作类型（禅道的 actionType，如 opened/edited/closed）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "opened")
    @NotEmpty(message = "动作类型不能为空")
    private String actionType;

    @Schema(description = "动作编号（zt_action.id）。禅道 send() 必传；这里可省 —— 省略时按"
            + "「对象类型 + 对象编号 + 动作类型」取最新一条动作", example = "1")
    private Integer actionID;

    @Schema(description = "操作者账号。留空时用当前登录用户（禅道 send 的 $actor 参数）", example = "admin")
    private String actor;

    @Schema(description = "只发给指定的 webhook（留空 = 遍历全部，禅道原样）", example = "92200")
    private Long webhookId;

}
