package cn.iocoder.yudao.module.zentao.dal.mysql.webhook;

import cn.iocoder.yudao.module.zentao.dal.dataobject.webhook.WebhookObjectRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Webhook 的「对象数据 / 动作数据」读取 Mapper。
 *
 * <p>它对应禅道 {@code webhookModel::buildData()} 里的三次查询：
 * <pre>
 *   $action = select * from zt_action where id = $actionID        → selectAction()
 *   $object = select * from $config->objectTables[$objectType] ... → selectObject()
 *   $users  = userModel::getList()                                → 由 yudao 的 AdminUserApi 承担
 * </pre>
 *
 * <p><b>表名从哪来</b>：{@code objectType → 表名} 的映射写死在 {@link WebhookObjectTables}
 * 常量类里（禅道是 {@code $config->objectTables}），**不接受任何外部输入**，
 * 所以这些 {@code ${}} 拼接是安全的 —— 与 {@code ActionObjectMap} / 报表模块同一套纪律。
 *
 * <p><b>注入字段名</b>：字段叫 {@code webhookObjectMapper}，避免与别的模块的同名短类冲突（坑位 #25/#47）。
 */
@Mapper
public interface WebhookObjectMapper {

    /**
     * 读动作行（禅道 {@code zt_action} 一行）。
     *
     * <p>{@code read} 是 MySQL 保留字，必须加反引号（坑位 #9）。
     */
    @Select("SELECT id, objectType, objectID, product, project, execution, actor, action, `date`, comment, extra "
            + "FROM zt_action WHERE id = #{actionID}")
    Map<String, Object> selectAction(@Param("actionID") Integer actionID);

    /**
     * 按「对象类型 + 对象编号 + 动作」取**最新**一条动作（id 最大）。
     *
     * <p>禅道 {@code send()} 必须传入 {@code $actionID}；本实现的 {@code /send} 接口允许省略它，
     * 靠这一条把最近一次动作补上，便于联调与测试。{@code ORDER BY id DESC LIMIT 1}
     * 把「取最新」的语义写进 SQL 本身（坑位 #31）。
     */
    @Select("SELECT id, objectType, objectID, product, project, execution, actor, action, `date`, comment, extra "
            + "FROM zt_action WHERE objectType = #{objectType} AND objectID = #{objectID} AND action = #{action} "
            + "ORDER BY id DESC LIMIT 1")
    Map<String, Object> selectLatestAction(@Param("objectType") String objectType,
                                           @Param("objectID") Long objectID,
                                           @Param("action") String action);

    /**
     * 读对象行，取 buildData 用得到的字段：名称列、assignedTo、product、execution。
     *
     * <p>{@code columns} / {@code table} 由 {@link WebhookObjectTables} 提供（白名单 + 该表真实存在的列）。
     * <b>列别名必须显式写</b>：yudao 把 MyBatis 配成了 {@code map-underscore-to-camel-case=true}，
     * 列 {@code assignedTo} 不去别名会被当成 {@code assigned_to} 而映射不上（坑位 #1 的同一根源）。
     */
    @Select("SELECT ${columns} FROM ${table} WHERE id = #{objectID}")
    WebhookObjectRow selectObject(@Param("table") String table,
                                  @Param("columns") String columns,
                                  @Param("objectID") Long objectID);

    /**
     * 某对象类型下 {@code zt_action} 里**真实出现过**的动作（去重）。
     *
     * <p>白名单是「允许哪些动作」，这张表是「本系统真的有这些动作」——
     * 页面上同时展示两者，避免使用者配了一堆永远不触发的动作。
     */
    @Select("SELECT DISTINCT action FROM zt_action WHERE objectType = #{objectType} ORDER BY action")
    List<String> selectObservedActions(@Param("objectType") String objectType);

}
