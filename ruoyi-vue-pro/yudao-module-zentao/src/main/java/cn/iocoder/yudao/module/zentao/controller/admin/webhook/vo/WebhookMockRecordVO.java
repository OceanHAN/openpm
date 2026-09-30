package cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仅用于联调/测试的 mock 接收端点记录。
 *
 * <p>真实环境里 webhook 指向的是钉钉/企微/飞书或调用方自己的 HTTP 服务；
 * 端到端回归需要一个「本地可被 mock 的地址」，所以本模块把收到的请求留在服务端内存里，
 * 测试脚本再用 {@code GET /zentao/webhook/mock-list} 把它取出来断言 payload。
 */
@Schema(description = "管理后台 - Webhook mock 接收记录 Response VO")
@Data
public class WebhookMockRecordVO {

    @Schema(description = "接收序号（自增）", example = "1")
    private Long seq;

    @Schema(description = "接收时间")
    private LocalDateTime receivedAt;

    @Schema(description = "Content-Type 请求头", example = "application/json;charset=utf-8")
    private String contentType;

    @Schema(description = "请求体原文")
    private String body;

}
