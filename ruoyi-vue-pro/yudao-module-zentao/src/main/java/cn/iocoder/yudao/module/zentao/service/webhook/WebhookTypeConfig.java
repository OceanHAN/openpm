package cn.iocoder.yudao.module.zentao.service.webhook;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Webhook 的「静态配置」—— 逐条照抄禅道 {@code module/webhook/config.php}。
 *
 * <p>这些常量是**只读规格**，不落库、不可配：禅道把它们写在 PHP 配置里，本实现原样搬过来，
 * 好处是「哪些对象类型、哪些动作会触发」只有一处定义，前端与测试都从这里取。
 *
 * <h3>① objectTypes 白名单（config.php:13-22）</h3>
 * <pre>
 *   product     opened, edited, closed, undeleted
 *   story       opened, edited, commented, frombug, changed, reviewed, closed, activated
 *   productplan opened, edited
 *   execution   opened, edited, started, delayed, suspended, closed, activated, undeleted
 *   task        opened, edited, commented, assigned, confirmed, started, finished, paused,
 *               canceled, restarted, closed, activated
 *   bug         opened, edited, commented, assigned, confirmed, bugconfirmed, resolved, closed, activated
 *   case        opened, edited, commented, reviewed, confirmed
 *   testtask    opened, edited, started, blocked, closed, activated
 *   todo        opened, edited
 * </pre>
 *
 * <h3>② needAssignTypes（config.php:24）</h3>
 * {@code story / task / bug / todo / feedback}：这些类型的对象如果 {@code assignedTo} 非空，
 * {@code buildData} 会把指派人的 mobile/email 带上（钉钉/企微群「@ 某人」就靠它）。
 *
 * <h3>③ 三方 API 根地址（config.php:8-10）</h3>
 * {@code dinguser}/{@code wechatuser}/{@code feishuuser} 三种「应用消息」在 create/update 时
 * 会被**强制**把 url 改写成这三个根地址（{@code webhookTao::getDingdingSecret} 等），
 * 因此它们其实没有独立的 webhook url，走的是 {@code lib/dingapi|wechatapi|feishuapi}。
 *
 * <h3>与 zentao 的差异（有意保留常量、不实现调用）</h3>
 * 本实现只做**通用 JSON / 三个群机器人**这条主链路（generic POST，本地 mock 就能端到端回归）；
 * 三种「应用消息」需要钉钉/企微/飞书的企业应用凭据 + openID 绑定，属于外部系统适配，
 * 见 README 的「已知限制」。
 */
@Component
public class WebhookTypeConfig {

    /** 对象类型 → 该类型允许触发的动作（顺序与禅道 config.php 一致，用 LinkedHashSet 保序） */
    private static final Map<String, Set<String>> OBJECT_TYPES = new LinkedHashMap<>();

    /** 需要带指派人 mobile/email 的对象类型（禅道 needAssignTypes） */
    private static final Set<String> NEED_ASSIGN_TYPES = new LinkedHashSet<>();

    /** 对象类型 → 中文名（禅道 {@code $lang->action->objectTypes}，只登记白名单里的 9 种） */
    private static final Map<String, String> OBJECT_TYPE_NAMES = new LinkedHashMap<>();

    static {
        OBJECT_TYPES.put("product", setOf("opened", "edited", "closed", "undeleted"));
        OBJECT_TYPES.put("story", setOf("opened", "edited", "commented", "frombug", "changed",
                "reviewed", "closed", "activated"));
        OBJECT_TYPES.put("productplan", setOf("opened", "edited"));
        OBJECT_TYPES.put("execution", setOf("opened", "edited", "started", "delayed", "suspended",
                "closed", "activated", "undeleted"));
        OBJECT_TYPES.put("task", setOf("opened", "edited", "commented", "assigned", "confirmed",
                "started", "finished", "paused", "canceled", "restarted", "closed", "activated"));
        OBJECT_TYPES.put("bug", setOf("opened", "edited", "commented", "assigned", "confirmed",
                "bugconfirmed", "resolved", "closed", "activated"));
        OBJECT_TYPES.put("case", setOf("opened", "edited", "commented", "reviewed", "confirmed"));
        OBJECT_TYPES.put("testtask", setOf("opened", "edited", "started", "blocked", "closed", "activated"));
        OBJECT_TYPES.put("todo", setOf("opened", "edited"));

        NEED_ASSIGN_TYPES.add("story");
        NEED_ASSIGN_TYPES.add("task");
        NEED_ASSIGN_TYPES.add("bug");
        NEED_ASSIGN_TYPES.add("todo");
        NEED_ASSIGN_TYPES.add("feedback");

        OBJECT_TYPE_NAMES.put("product", "产品");
        OBJECT_TYPE_NAMES.put("story", "需求");
        OBJECT_TYPE_NAMES.put("productplan", "计划");
        OBJECT_TYPE_NAMES.put("execution", "执行");
        OBJECT_TYPE_NAMES.put("task", "任务");
        OBJECT_TYPE_NAMES.put("bug", "Bug");
        OBJECT_TYPE_NAMES.put("case", "用例");
        OBJECT_TYPE_NAMES.put("testtask", "测试单");
        OBJECT_TYPE_NAMES.put("todo", "待办");
    }

    private static Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(List.of(values)));
    }

    /** 全部对象类型（保序） */
    public List<String> objectTypeList() {
        return new ArrayList<>(OBJECT_TYPES.keySet());
    }

    /** 该对象类型允许的动作；不在白名单里返回空集合（调用方据此跳过） */
    public Set<String> actionTypes(String objectType) {
        return OBJECT_TYPES.getOrDefault(objectType, Collections.emptySet());
    }

    /** 中文名，没登记就原样返回（不为显示去猜） */
    public String displayName(String objectType) {
        return OBJECT_TYPE_NAMES.getOrDefault(objectType, objectType);
    }

    /** 是否属于 needAssignTypes */
    public boolean needAssign(String objectType) {
        return NEED_ASSIGN_TYPES.contains(objectType);
    }

    /**
     * 禅道 {@code action->label->$actionType}：动作的中文标签。
     *
     * <p>只登记白名单里会出现的动作（取自 {@code module/action/lang/zh-cn.php} 的
     * {@code $lang->action->label}），未登记的原样返回 —— 禅道 buildData 里
     * 「label 不存在就 return false」的判据就是这张表。
     */
    public String actionLabel(String actionType) {
        return ACTION_LABELS.getOrDefault(actionType == null ? "" : actionType, actionType);
    }

    /**
     * 动作标签表（键集合就是「禅道 {@code $lang->action->label} 里认得的动作」）。
     *
     * <p>禅道 {@code buildData()} 的第一道闸就是
     * {@code if(!isset($this->lang->action->label->$actionType)) return false;}
     * —— 认不出的动作直接不发。本实现用这张 Map 的键集合做同一件事。
     */
    private static final Map<String, String> ACTION_LABELS = new LinkedHashMap<>();

    static {
        ACTION_LABELS.put("opened", "创建了");
        ACTION_LABELS.put("edited", "编辑了");
        ACTION_LABELS.put("closed", "关闭了");
        ACTION_LABELS.put("undeleted", "还原了");
        ACTION_LABELS.put("commented", "评论了");
        ACTION_LABELS.put("frombug", "转了需求");
        ACTION_LABELS.put("changed", "变更了");
        ACTION_LABELS.put("reviewed", "评审了");
        ACTION_LABELS.put("activated", "激活了");
        ACTION_LABELS.put("started", "开始了");
        ACTION_LABELS.put("delayed", "延期了");
        ACTION_LABELS.put("suspended", "挂起了");
        ACTION_LABELS.put("assigned", "指派了");
        ACTION_LABELS.put("confirmed", "确认了需求");
        ACTION_LABELS.put("finished", "完成了");
        ACTION_LABELS.put("paused", "暂停了");
        ACTION_LABELS.put("canceled", "取消了");
        ACTION_LABELS.put("restarted", "继续了");
        ACTION_LABELS.put("bugconfirmed", "确认了");
        ACTION_LABELS.put("resolved", "解决了");
        ACTION_LABELS.put("blocked", "阻塞了");
    }

    /** 禅道 buildData 第一道闸：{@code isset($this->lang->action->label->$actionType)} */
    public boolean hasActionLabel(String actionType) {
        return actionType != null && ACTION_LABELS.containsKey(actionType);
    }

    /** 群机器人类：fetchHook 里 Content-Type 被**强制**成 application/json（config 里的 contentType 被忽略） */
    public boolean isGroupRobot(String type) {
        return "dinggroup".equals(type) || "wechatgroup".equals(type) || "feishugroup".equals(type);
    }

    /** 是否是需要企业应用凭据的「应用消息」类型（本实现不投递，仅保留字段语义） */
    public boolean isUserApp(String type) {
        return "dinguser".equals(type) || "wechatuser".equals(type) || "feishuuser".equals(type);
    }

}
