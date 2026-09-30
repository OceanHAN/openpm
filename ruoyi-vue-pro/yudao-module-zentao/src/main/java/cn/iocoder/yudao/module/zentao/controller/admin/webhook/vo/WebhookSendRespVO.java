package cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - Webhook 发送结果 Response VO")
@Data
public class WebhookSendRespVO {

    @Schema(description = "对象类型", example = "story")
    private String objectType;

    @Schema(description = "对象编号", example = "1")
    private Long objectID;

    @Schema(description = "动作类型", example = "opened")
    private String actionType;

    @Schema(description = "实际使用的动作编号（zt_action.id；没找到就是 null）", example = "1")
    private Integer actionID;

    @Schema(description = "本次组装出来的 payload（禅道 buildData 的产物）")
    private String payload;

    @Schema(description = "数据本身就不满足投递条件（对象/动作/产品/执行过滤掉了）")
    private Boolean skipped;

    @Schema(description = "发送失败的条数。失败只落日志，不影响调用方（禅道 send() 返回 true）", example = "0")
    private Integer failedCount;

    @Schema(description = "命中的 webhook 编号列表")
    private List<Long> matchedWebhookIds;

    @Schema(description = "每个 webhook 的结果说明")
    private List<Item> items;

    @Schema(description = "给调用方的说明")
    private String message;

    @Schema(description = "单个 webhook 的发送明细")
    @Data
    public static class Item {

        @Schema(description = "webhook 编号", example = "92200")
        private Long webhookId;

        @Schema(description = "webhook 名称")
        private String name;

        @Schema(description = "请求地址")
        private String url;

        @Schema(description = "实际发出的 payload")
        private String payload;

        @Schema(description = "是否成功（HTTP 2xx/3xx 视为成功）")
        private Boolean success;

        @Schema(description = "结果说明：三方返回 body、HTTP 状态码或异常信息")
        private String result;

        @Schema(description = "本次发送日志在 zt_log 里的编号")
        private Long logId;

        @Schema(description = "是否走了异步队列（sendType=async）")
        private Boolean async;

    }

}
