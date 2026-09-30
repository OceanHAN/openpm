package cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - Webhook Response VO")
@Data
public class WebhookRespVO {

    @Schema(description = "编号", example = "92200")
    private Long id;

    @Schema(description = "类型", example = "default")
    private String type;

    @Schema(description = "名称", example = "本地联调接收端")
    private String name;

    @Schema(description = "请求地址")
    private String url;

    @Schema(description = "站点域名（用于拼查看链接）")
    private String domain;

    @Schema(description = "加签密钥（群机器人用）")
    private String secret;

    @Schema(description = "内容类型", example = "application/json")
    private String contentType;

    @Schema(description = "发送方式：sync/async", example = "sync")
    private String sendType;

    @Schema(description = "只对这些产品发（逗号列表）", example = "1,2")
    private String products;

    @Schema(description = "只对这个执行发（逗号列表）", example = "1")
    private String executions;

    @Schema(description = "payload 字段列表（必然含 text）", example = "id,objectType,action,text")
    private String params;

    @Schema(description = "对象类型+动作过滤（JSON；空 = 用白名单全量）")
    private String actions;

    @Schema(description = "备注")
    private String desc;

    @Schema(description = "禅道侧创建人账号")
    private String createdBy;

    @Schema(description = "禅道侧创建时间")
    private LocalDateTime createdDate;

    @Schema(description = "禅道侧修改人账号")
    private String editedBy;

    @Schema(description = "禅道侧修改时间")
    private LocalDateTime editedDate;

    @Schema(description = "框架侧创建时间")
    private LocalDateTime createTime;

}
