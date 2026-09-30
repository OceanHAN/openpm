package cn.iocoder.yudao.module.zentao.controller.admin.webhook;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookMockRecordVO;
import cn.iocoder.yudao.module.zentao.service.webhook.WebhookMockReceiver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * **仅用于联调/测试**的 mock 接收端点。
 *
 * <p>webhook 的功能本身就是「向任意 URL POST JSON」，端到端回归必须有一个**本地可达**的接收端：
 * 测试脚本把演示 webhook 的 url 指向 {@code /admin-api/zentao/webhook/mock-receive}，
 * 触发一次发送后从 {@code /mock-list} 把收到的 body 取出来断言 payload，
 * 再用 {@code /mock-clear} 清空，保证断言可重复。
 *
 * <h3>为什么要 {@link PermitAll}</h3>
 * 事件外发是**没有登录态**的（禅道是 cron / 请求结束后触发），服务端发给自己的这个地址时
 * 也不会带 {@code Authorization}。这跟 entry 的 {@code /verify} 是同一类路径。
 * 它只往内存里写一条记录、不碰任何业务数据，且记录条数有上限（见 {@link WebhookMockReceiver}）。
 *
 * <p>生产环境不需要这个端点，但它也无害：没有任何鉴权信息、也不读写业务表。
 */
@Tag(name = "管理后台 - 禅道 Webhook mock 接收（仅测试）")
@RestController
@RequestMapping("/zentao/webhook")
@PermitAll
public class ZentaoWebhookMockController {

    @Resource
    private WebhookMockReceiver mockReceiver;

    /**
     * 接收端。**不校验任何东西**，把 body 原样存进内存。
     *
     * <p>返回的 {@code "success"} 就是三方系统常见的 200 回执，让 {@code fetchHook} 侧
     * 拿到一段非空 result（禅道「result 非空就 saveLog」的判据）。
     */
    @PostMapping(value = "/mock-receive", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "mock 接收 webhook 请求（仅联调/测试用）")
    public ResponseEntity<String> receive(@RequestBody(required = false) String body,
                                        @RequestHeader(value = "Content-Type", required = false) String contentType) {
        mockReceiver.receive(StrUtil.blankToDefault(body, ""), StrUtil.blankToDefault(contentType, ""));
        /* 回**裸文本** success，而不是 yudao 的 {code,data,msg} 信封 ——
           它是三方系统的替身，真实的三方回执就是一段文本；用信封会让
           「webhook 发送结果」这一列存成一坨 JSON，断言也失去意义。 */
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("success");
    }

    @GetMapping("/mock-list")
    @Operation(summary = "列出 mock 收到的请求", description = "按接收顺序，最新的在最后")
    public CommonResult<List<WebhookMockRecordVO>> list() {
        return success(mockReceiver.list());
    }

    @DeleteMapping("/mock-clear")
    @Operation(summary = "清空 mock 收到的请求（测试收尾用）")
    public CommonResult<Boolean> clear() {
        mockReceiver.clear();
        return success(true);
    }

}
