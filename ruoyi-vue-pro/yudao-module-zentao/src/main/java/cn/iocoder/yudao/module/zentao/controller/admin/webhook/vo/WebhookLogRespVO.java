package cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - Webhook 发送日志 Response VO")
@Data
public class WebhookLogRespVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "对象类型，恒为 webhook", example = "webhook")
    private String objectType;

    @Schema(description = "对象编号（webhook 编号）", example = "92200")
    private Long objectID;

    @Schema(description = "动作编号（zt_action.id）", example = "1")
    private Integer action;

    @Schema(description = "发送时间")
    private java.time.LocalDateTime date;

    @Schema(description = "请求地址")
    private String url;

    @Schema(description = "内容类型", example = "application/json")
    private String contentType;

    @Schema(description = "实际发出的 payload")
    private String data;

    @Schema(description = "结果：三方返回 / HTTP 状态码 / 异常信息")
    private String result;

}
