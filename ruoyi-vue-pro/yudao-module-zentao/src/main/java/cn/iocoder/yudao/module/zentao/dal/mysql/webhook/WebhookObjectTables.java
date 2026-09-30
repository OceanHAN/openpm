package cn.iocoder.yudao.module.zentao.dal.mysql.webhook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对象类型 → （业务表、对象名称列、可选列）。
 *
 * <p>逐条对照禅道的两份配置：
 * <ul>
 *   <li>{@code $config->objectTables}（{@code config/zentaopms.php}）—— 对象类型对应哪张表；</li>
 *   <li>{@code $config->action->objectNameFields}（{@code module/action/config.php}）——
 *       对象名称存在哪一列（product→name、story→title、bug→title、case→title、
 *       productplan→title、task→name、execution→name、testtask→name、todo→name）。</li>
 * </ul>
 *
 * <p><b>为什么要把「有哪些列」也写进来</b>：这 9 张表并不是同一副骨架 ——
 * {@code zt_product} / {@code zt_project} 连 {@code product}/{@code execution} 都没有，
 * {@code zt_todo}、{@code zt_case} 只有 {@code product} 没有 {@code execution}。
 * 禅道是 PHP 动态取属性（取不到就是 null），SQL 里必须显式选列，所以这里把骨架写死，
 * 由 {@code WebhookObjectMapper} 按它拼 {@code SELECT} 的列清单。
 * 表名与列名都是**代码常量**、不接受外部输入，因此 {@code ${}} 拼接是安全的
 * （与 {@code ActionObjectMap}、报表模块同一套纪律）。
 *
 * <p>{@code execution} 与 {@code project} 在禅道里共用 {@code zt_project}，这里只登记
 * {@code execution}（webhook 白名单里没有 project）。查询时用
 * {@code type IN ('sprint','stage','kanban')} 之外的语义不额外加条件 —— buildData 只按 id 取行，
 * 不做列表过滤，所以不存在「共用表混入另一半」的问题（坑位 #12 的适用边界）。
 */
public final class WebhookObjectTables {

    /**
     * 一行映射。
     *
     * @param table       业务表
     * @param nameColumn  对象名称列（禅道 objectNameFields）
     * @param hasAssigned 是否有 assignedTo 列（决定 buildData 要不要带移动端/邮箱）
     * @param hasProduct  是否有 product 列（products 交集过滤用）
     * @param hasExecution 是否有 execution 列（executions 过滤用）
     */
    public record Target(String table, String nameColumn, boolean hasAssigned,
                         boolean hasProduct, boolean hasExecution) {
    }

    private static final Map<String, Target> TARGETS = new LinkedHashMap<>();

    static {
        TARGETS.put("product", new Target("zt_product", "name", false, false, false));
        TARGETS.put("story", new Target("zt_story", "title", true, true, false));
        TARGETS.put("productplan", new Target("zt_productplan", "title", false, true, false));
        TARGETS.put("execution", new Target("zt_project", "name", false, false, false));
        TARGETS.put("task", new Target("zt_task", "name", true, false, true));
        TARGETS.put("bug", new Target("zt_bug", "title", true, true, true));
        TARGETS.put("case", new Target("zt_case", "title", false, true, true));
        TARGETS.put("testtask", new Target("zt_testtask", "name", false, true, true));
        TARGETS.put("todo", new Target("zt_todo", "name", true, false, false));
    }

    private WebhookObjectTables() {
    }

    /** 取映射；不在白名单返回 null（调用方据此判定 buildData 的第一道闸没通过） */
    public static Target find(String objectType) {
        return objectType == null ? null : TARGETS.get(objectType);
    }

    /**
     * 拼 {@code SELECT} 的列清单：{@code id, <名称列> AS objectName} + 该表真实存在的可选列。
     *
     * <p>不存在的列**不选**（而不是选出来再判空）—— 否则 SQL 会直接报 Unknown column。
     * 列别名必须显式写全：yudao 开了 {@code map-underscore-to-camel-case}，
     * 列 {@code assignedTo} 不带别名会被当成 {@code assigned_to} 而映射不上（坑位 #1）。
     */
    public static String selectColumns(Target target) {
        List<String> columns = new ArrayList<>();
        columns.add("id");
        columns.add("`" + target.nameColumn() + "` AS objectName");
        if (target.hasAssigned()) {
            columns.add("assignedTo AS assignedTo");
        }
        if (target.hasProduct()) {
            columns.add("product AS product");
        }
        if (target.hasExecution()) {
            columns.add("execution AS execution");
        }
        return String.join(", ", columns);
    }

}
