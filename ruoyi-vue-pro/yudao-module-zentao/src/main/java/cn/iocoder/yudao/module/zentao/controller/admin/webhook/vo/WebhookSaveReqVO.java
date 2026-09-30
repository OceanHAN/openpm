package cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Schema(description = "管理后台 - Webhook 创建/修改 Request VO")
@Data
public class WebhookSaveReqVO {

    @Schema(description = "编号（修改时必填）", example = "92200")
    private Long id;

    /**
     * 禅道 {@code form::data($config->webhook->form->create)} 的必填项只有 name
     * （{@code config->webhook->create->requiredFields = 'name'}），url 只在**非空**时才校验格式。
     * 这里把 name 做成硬性 {@code @NotEmpty}，url 的格式与非空交给 Service 判（错误码
     * {@code WEBHOOK_URL_REQUIRED} 是预先约定好的），便于返回禅道同款中文提示。
     */
    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "本地联调接收端")
    @NotEmpty(message = "名称不能为空")
    private String name;

    @Schema(description = "类型：default/dinggroup/wechatgroup/feishugroup", example = "default")
    private String type;

    @Schema(description = "请求地址", example = "http://127.0.0.1:48080/admin-api/zentao/webhook/mock-receive")
    private String url;

    @Schema(description = "站点域名（留空则用当前系统地址拼查看链接）")
    private String domain;

    @Schema(description = "加签密钥（钉钉群/飞书群）")
    private String secret;

    @Schema(description = "内容类型", example = "application/json")
    private String contentType;

    @Schema(description = "发送方式：sync/async", example = "sync")
    private String sendType;

    @Schema(description = "只对这些产品发（逗号列表；空 = 不限）")
    private String products;

    @Schema(description = "只对这个执行发（逗号列表；空 = 不限）")
    private String executions;

    @Schema(description = "payload 字段列表（逗号；text 会被强制补上）", example = "id,objectType,action,text")
    private String params;

    @Schema(description = "对象类型+动作过滤，JSON 形如 {\"story\":[\"opened\"]}；空 = 白名单全量")
    private String actions;

    @Schema(description = "备注")
    private String desc;

    /**
     * 是否强制要求 products 非空。
     *
     * <p>禅道的 CREATE 表单里 products 是**可空**的（{@code formdata.php:19} required=false，
     * 空 = 所有产品的动作都触发）。这个字段是为「必须选择产品」
     * （{@code WEBHOOK_PRODUCT_REQUIRED}）预留的显式开关：调用方（例如某个只关心
     * 单产品的接入场景）可以打开它，默认关闭 —— 关闭时与禅道行为完全一致。
     * 它只参与校验，**不落库**。
     */
    @Schema(description = "是否强制要求 products 非空（默认 false，与禅道可空行为一致）", example = "false")
    private Boolean requireProduct;

}
