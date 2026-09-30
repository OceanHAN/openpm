package cn.iocoder.yudao.module.zentao.dal.dataobject.webhook;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Webhook 发送日志（禅道 {@code zt_log}，{@code objectType='webhook'}）。
 *
 * <p>禅道 {@code zt_log} 是**通用**日志表：{@code objectType + objectID} 指向任意对象，
 * 目前两类消费者：{@code entry}（应用接入调用）与 {@code webhook}（本模块的发送日志）。
 * 表已经由 entry 模块建好（{@code deploy/sql/49-zt_entry.sql}），**这里只是同表再插入**。
 *
 * <p>为什么单独一个 DO 而不复用 {@code EntryLogDO}：两者对同一张表用同一个「列名映射」，
 * 但按模块各自持有能避免 webhook 与 entry 互相耦合；两张 DO 指向同一个
 * {@code @TableName("zt_log")} 是允许的（MyBatis-Plus 的表信息可以按实体注册多份）。
 *
 * <p>本表没有 {@code deleted} 列（禅道原样）：日志是只增不改的事实数据，**不继承 BaseDO**。
 */
@TableName("zt_log")
@Data
public class WebhookLogDO {

    /** 禅道 {@code zt_log.objectType} 里 webhook 的取值 */
    public static final String OBJECT_TYPE_WEBHOOK = "webhook";

    @TableId
    private Long id;

    /** 对象类型，本模块恒为 webhook */
    @TableField("objectType")
    private String objectType;

    /** 对象编号（zt_webhook.id） */
    @TableField("objectID")
    private Long objectID;

    /**
     * 动作编号（zt_action.id）。
     *
     * <p>禅道 {@code log} 页面靠它把「这次发的是哪个对象、什么动作」渲染出来；
     * 本实现也存，前端日志抽屉直接展示。
     */
    private Integer action;

    /** 发送时间。列名 date 是关键字 */
    @TableField("`date`")
    private LocalDateTime date;

    /** 请求地址（禅道存的是 webhook.url） */
    private String url;

    /** 内容类型（禅道存的是 webhook.contentType） */
    @TableField("contentType")
    private String contentType;

    /** 实际发出去的 payload */
    private String data;

    /** 结果：三方返回的 body、HTTP 状态码，或异常信息（失败时） */
    private String result;

}
