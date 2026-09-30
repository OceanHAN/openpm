package cn.iocoder.yudao.module.zentao.dal.dataobject.webhook;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Webhook（禅道 {@code zt_webhook}）。
 *
 * <p>禅道这个模块是「事件外发的通用出口」：业务动作（新增/编辑/删除需求、任务、缺陷……）发生后，
 * 按 {@code config/webhook.php} 的 {@code objectTypes} 白名单匹配「对象类型 + 动作」，
 * 把该次 {@code zt_action} 的字段按 {@code params} 拼成 JSON，POST 到用户配置的 URL。
 *
 * <h3>列名纪律</h3>
 * 列名与禅道 {@code db/zentao.sql} 逐列对齐（`type`/`name`/`url`/`domain`/`secret`/`contentType`/
 * `sendType`/`products`/`executions`/`params`/`actions`/`desc` 等），因此**驼峰列必须显式
 * {@code @TableField}**（坑位 #1），保留字 {@code desc} 还要加反引号（坑位 #10）。
 *
 * <h3>框架列</h3>
 * 禅道原表已有 {@code deleted}，所以本 DO 继承 {@link BaseDO}（它提供
 * {@code create_time/update_time/creator/updater/deleted}）。注意 {@code createdBy/createdDate}
 * 与 {@code editedBy/editedDate} 是**禅道自己的**业务列，和框架的 {@code creator/create_time}
 * 并存 —— 前者存账号字符串（admin），后者存框架用户编号，两者不要混用。
 */
@TableName("zt_webhook")
@KeySequence("zt_webhook_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WebhookDO extends BaseDO {

    /** Webhook 类型：通用 JSON 与三个群机器人（禅道 {@code view/create.html.php} 的 type 下拉） */
    public static final String TYPE_DEFAULT = "default";
    public static final String TYPE_DINGGROUP = "dinggroup";
    public static final String TYPE_FEISHUGROUP = "feishugroup";
    public static final String TYPE_WECHATGROUP = "wechatgroup";

    @TableId
    private Long id;

    /**
     * 类型：default（通用 JSON）/ dinggroup / wechatgroup / feishugroup
     * （禅道还有 dinguser / wechatuser / feishuuser 三种「应用消息」，见类注释的偏离说明）
     */
    private String type;

    /** 名称（禅道 {@code config->webhook->create->requiredFields = 'name'}） */
    private String name;

    /** 请求地址。禅道要求必须匹配 {@code ^http(s)?://} */
    @TableField("url")
    private String url;

    /** 站点域名（用于拼「查看链接」；留空则用当前系统地址） */
    private String domain;

    /** 加签密钥（钉钉群 / 飞书群用） */
    private String secret;

    /** Content-Type。群机器人类在 fetchHook 里被**强制**成 application/json */
    @TableField("contentType")
    private String contentType;

    /** 发送方式：sync（默认，当场发）/ async（禅道落 zt_notify 由 cron 消费） */
    @TableField("sendType")
    private String sendType;

    /** 只对这些产品发（逗号列表，与动作行的 product 取交集；空 = 不限） */
    @TableField("products")
    private String products;

    /** 只对这个执行发（逗号包裹后做子串匹配；空 = 不限） */
    @TableField("executions")
    private String executions;

    /** 要写进 payload 的字段名，逗号列表，且**必然包含 text**（禅道 create/update 里强制补 text） */
    @TableField("params")
    private String params;

    /** 只在这些对象类型+动作上发，JSON 形如 {"story":["opened","edited"],"task":["opened"]}；空 = 用白名单全量 */
    private String actions;

    /** 备注。{@code desc} 是 MySQL 关键字，列名必须加反引号（坑位 #10） */
    @TableField("`desc`")
    private String desc;

    /** 禅道侧的创建人账号 */
    @TableField("createdBy")
    private String createdBy;

    /** 禅道侧的创建时间 */
    @TableField("createdDate")
    private LocalDateTime createdDate;

    /** 禅道侧的修改人账号 */
    @TableField("editedBy")
    private String editedBy;

    /** 禅道侧的修改时间 */
    @TableField("editedDate")
    private LocalDateTime editedDate;

}
