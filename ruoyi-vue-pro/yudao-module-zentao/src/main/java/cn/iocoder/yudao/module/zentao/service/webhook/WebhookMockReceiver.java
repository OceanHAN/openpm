package cn.iocoder.yudao.module.zentao.service.webhook;

import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookMockRecordVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * mock 接收端的**内存**存储。
 *
 * <p>需求是「把收到的 body 存进内存/日志供断言」。这里选内存（{@code CopyOnWriteArrayList}）：
 * 断言要的是「刚刚那一次发了什么」，写进 zt_log 反而会和真正的发送日志混在一起。
 *
 * <p>两点保护：
 * <ol>
 *   <li>条数上限 {@value #MAX_RECORDS}：避免有人拿它当免费存储把内存撑爆（只保留最新的一批）；</li>
 *   <li>单条 body 超过 {@value #MAX_BODY_LENGTH} 字符时截断，同样是为了内存安全。</li>
 * </ol>
 * 进程重启即清空 —— 这是刻意的（它是测试脚手架，不是业务数据）。
 */
@Component
public class WebhookMockReceiver {

    private static final int MAX_RECORDS = 200;

    private static final int MAX_BODY_LENGTH = 20000;

    private final List<WebhookMockRecordVO> records = new ArrayList<>();

    private final AtomicLong sequence = new AtomicLong(0);

    /** 接收一条。并发安全靠 synchronized —— 这是低频测试路径，不值得为它引入并发容器 */
    public synchronized void receive(String body, String contentType) {
        WebhookMockRecordVO record = new WebhookMockRecordVO();
        record.setSeq(sequence.incrementAndGet());
        record.setReceivedAt(LocalDateTime.now());
        record.setContentType(contentType);
        record.setBody(body.length() > MAX_BODY_LENGTH ? body.substring(0, MAX_BODY_LENGTH) : body);
        records.add(record);
        while (records.size() > MAX_RECORDS) {
            records.remove(0);
        }
    }

    /** 按接收顺序返回（最新的在最后） */
    public synchronized List<WebhookMockRecordVO> list() {
        return new ArrayList<>(records);
    }

    public synchronized void clear() {
        records.clear();
    }

}
