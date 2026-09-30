package cn.iocoder.yudao.module.zentao.dal.dataobject.webhook;

import lombok.Data;

/**
 * {@code zt_action} + 对象表里 buildData 需要的字段。
 *
 * <p>{@code objectName} 是「对象名称列」的别名（禅道 {@code $config->action->objectNameFields}：
 * product→name、story→title、bug→title……），SQL 里统一 {@code AS objectName}，
 * 这样 actionType 是 name 还是 title 都不影响 Java 侧。
 */
@Data
public class WebhookObjectRow {

    /** 对象编号 */
    private Long id;

    /** 对象名称（禅道 {@code objectNameFields[$objectType]} 指向的那一列） */
    private String objectName;

    /** 指派给（部分表没有这一列 → 保持 null） */
    private String assignedTo;

    /** 所属产品（逗号列表，用于 products 交集过滤） */
    private String product;

    /** 所属执行（用于 executions 过滤；zt_action.execution 是单值） */
    private Long execution;

}
