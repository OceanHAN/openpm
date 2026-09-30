package cn.iocoder.yudao.module.zentao.dal.mysql.report;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 报表（report）的只读聚合查询。
 *
 * <p><b>类名带 {@code Zentao} 前缀是必须的</b>：MyBatis 的 mapper bean 名取的是短类名首字母小写，
 * 叫 {@code ReportMapper} 会生成 bean 名 {@code reportMapper}，而「测试报告」模块的
 * {@code TestReportServiceImpl} 里恰好有个 {@code @Resource private TestReportMapper reportMapper;} ——
 * {@code @Resource} 先按字段名注入，于是它拿到本 Mapper 的代理然后类型不匹配，启动直接失败（坑位 #25）。
 *
 * <h3>为什么这里全是原生 SQL</h3>
 * 报表的每一条都是「按年/按月 group by + 条件求和」，用 MyBatis-Plus 的 Wrapper 表达不了
 * （MP 不支持 {@code DATE_FORMAT} / {@code SUM(CASE WHEN ...)} 这类投影）。
 * 这些查询只读、只返回聚合结果，直接写 SQL 最清楚，也最好和禅道逐条对照。
 *
 * <h3>关于 ${table}</h3>
 * 贡献/产出那几条要按对象类型去对应的业务表里确认「对象还在不在」
 * （禅道 {@code getUserYearContributions} 与 {@code getOutputData} 都会过滤掉已删除对象的动作）。
 * 表名从 {@code ReportObjectTables} 的白名单里取，**不接受任何外部输入**，
 * 所以拼 {@code ${table}} 是安全的（与回收站 {@code ActionObjectMap} 同一做法）。
 *
 * <h3>租户</h3>
 * {@code system_login_log} 是 yudao 自己的表（带 tenant_id），原生 SQL 不走租户拦截器 ——
 * 报表本就要看全量，这里如实说明。{@code zt_*} 表都在 {@code yudao.tenant.ignore-tables} 里。
 */
@Mapper
public interface ZentaoReportMapper {

    // ==================== 年度数据：基础指标 ====================

    /**
     * 年度登录次数。禅道数的是 {@code zt_action.action='login'}（禅道每次登录写一条动作），
     * 本实现不写这类动作，登录记录在 yudao 的 {@code system_login_log} 里，所以直接读它。
     */
    @Select("<script>SELECT COUNT(1) FROM system_login_log WHERE result = 0 AND LEFT(create_time, 4) = #{year} "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND username IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if></script>")
    Long countLogins(@Param("year") String year, @Param("accounts") List<String> accounts);

    /** 年度动作数（{@code zt_action} 的条数，不区分对象类型） */
    @Select("<script>SELECT COUNT(1) FROM zt_action WHERE LEFT(date, 4) = #{year} "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND actor IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if></script>")
    Long countActions(@Param("year") String year, @Param("accounts") List<String> accounts);

    /** 年度待办：总数 / 未完成 / 已完成 */
    @Select("<script>SELECT COUNT(1) AS count, "
            + "SUM(CASE WHEN status &lt;&gt; 'done' THEN 1 ELSE 0 END) AS undone, "
            + "SUM(CASE WHEN status = 'done' THEN 1 ELSE 0 END) AS done "
            + "FROM zt_todo WHERE deleted = 0 AND LEFT(date, 4) = #{year} "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND account IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if></script>")
    Map<String, Object> selectTodoStat(@Param("year") String year, @Param("accounts") List<String> accounts);

    /** 年度工时：条数 + 消耗合计 */
    @Select("<script>SELECT COUNT(1) AS count, COALESCE(SUM(consumed), 0) AS consumed "
            + "FROM zt_effort WHERE deleted = 0 AND LEFT(date, 4) = #{year} "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND account IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if></script>")
    Map<String, Object> selectEffortStat(@Param("year") String year, @Param("accounts") List<String> accounts);

    // ==================== 年度数据：贡献 / 产出（按对象类型） ====================

    /**
     * 某对象类型下「各年各动作的条数」，并过滤掉**对象已被删除**的动作
     * （禅道 {@code getUserYearContributions} 与 {@code getOutputData} 都做这一步）。
     *
     * <p>一次把多个年份都取回来：年度页要画「历年雷达对比」，如果按年分别查就是
     * 对象类型数 × 年数 条 SQL；按年分组只需要对象类型数条。
     */
    @Select("<script>SELECT t.action AS action, LEFT(t.date, 4) AS year, COUNT(1) AS cnt FROM zt_action t "
            + "WHERE t.objectType = #{objectType} "
            + "AND t.action IN <foreach collection='actions' item='ac' open='(' separator=',' close=')'>#{ac}</foreach> "
            + "AND LEFT(t.date, 4) IN <foreach collection='years' item='y' open='(' separator=',' close=')'>#{y}</foreach> "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND t.actor IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if> "
            + "AND NOT EXISTS (SELECT 1 FROM ${table} o WHERE o.id = t.objectID AND o.deleted = 1) "
            + "GROUP BY t.action, year</script>")
    List<Map<String, Object>> selectActionCountByYears(@Param("years") List<String> years,
                                                       @Param("objectType") String objectType,
                                                       @Param("table") String table,
                                                       @Param("actions") List<String> actions,
                                                       @Param("accounts") List<String> accounts);

    /**
     * 年度对象状态分布：本年被「创建」过的对象，按**对象当前状态**分组。
     * 禅道 {@code getYearObjectStat} 里 statusStat 的口径就是这样（去重的对象数，不是动作数）。
     */
    @Select("<script>SELECT o.status AS status, COUNT(DISTINCT o.id) AS cnt "
            + "FROM zt_action t JOIN ${table} o ON o.id = t.objectID "
            + "WHERE LEFT(t.date, 4) = #{year} AND o.deleted = 0 "
            + "AND t.action IN <foreach collection='openedActions' item='ac' open='(' separator=',' close=')'>#{ac}</foreach> "
            + "AND t.objectType IN <foreach collection='objectTypes' item='ot' open='(' separator=',' close=')'>#{ot}</foreach> "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND t.actor IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if> "
            + "GROUP BY o.status</script>")
    List<Map<String, Object>> selectObjectStatusStat(@Param("year") String year, @Param("table") String table,
                                                     @Param("objectTypes") List<String> objectTypes,
                                                     @Param("openedActions") List<String> openedActions,
                                                     @Param("accounts") List<String> accounts);

    /** 年度对象动作的月度分布：{action, month, cnt} —— 前端画每月趋势用 */
    @Select("<script>SELECT t.action AS action, DATE_FORMAT(t.date, '%Y-%m') AS month, COUNT(1) AS cnt "
            + "FROM zt_action t "
            + "WHERE LEFT(t.date, 4) = #{year} "
            + "AND t.action IN <foreach collection='actions' item='ac' open='(' separator=',' close=')'>#{ac}</foreach> "
            + "AND t.objectType IN <foreach collection='objectTypes' item='ot' open='(' separator=',' close=')'>#{ot}</foreach> "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND t.actor IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if> "
            + "GROUP BY t.action, month</script>")
    List<Map<String, Object>> selectActionMonthStat(@Param("year") String year,
                                                    @Param("objectTypes") List<String> objectTypes,
                                                    @Param("actions") List<String> actions,
                                                    @Param("accounts") List<String> accounts);

    /** 年度用例执行结果分布 + 月度分布（数 zt_case 的 lastRun*，与禅道一致） */
    @Select("<script>SELECT lastRunResult AS result, DATE_FORMAT(lastRunDate, '%Y-%m') AS month, COUNT(1) AS cnt "
            + "FROM zt_case WHERE deleted = 0 AND LEFT(lastRunDate, 4) = #{year} "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND lastRunner IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if> "
            + "GROUP BY lastRunResult, month</script>")
    List<Map<String, Object>> selectCaseRunStat(@Param("year") String year, @Param("accounts") List<String> accounts);

    /** 年度用例执行次数（zt_testresult 的流水条数） */
    @Select("<script>SELECT COUNT(1) FROM zt_testresult t JOIN zt_case c ON c.id = t.case "
            + "WHERE c.deleted = 0 AND LEFT(t.date, 4) = #{year} "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND t.lastRunner IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if></script>")
    Long countCaseRuns(@Param("year") String year, @Param("accounts") List<String> accounts);

    /** 「执行用例时建了缺陷」的月度分布（用例报表里的 createBug 一格） */
    @Select("<script>SELECT DATE_FORMAT(t.date, '%Y-%m') AS month, COUNT(1) AS cnt "
            + "FROM zt_action t JOIN zt_bug b ON b.id = t.objectID "
            + "WHERE t.objectType = 'bug' AND t.action = 'created' AND b.deleted = 0 "
            + "AND b.case &lt;&gt; 0 AND b.case IS NOT NULL AND LEFT(t.date, 4) = #{year} "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND t.actor IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if> "
            + "GROUP BY month</script>")
    List<Map<String, Object>> selectCaseBugMonthStat(@Param("year") String year,
                                                     @Param("accounts") List<String> accounts);

    // ==================== 年度数据：产品 / 执行 ====================

    /** 本年内创建的产品计划数（按产品分组） */
    @Select("<script>SELECT p.product AS product, COUNT(DISTINCT p.id) AS cnt "
            + "FROM zt_productplan p JOIN zt_action t ON t.objectID = p.id AND t.objectType = 'productplan' "
            + "WHERE p.deleted = 0 AND t.action = 'created' AND LEFT(t.date, 4) = #{year} "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND t.actor IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if> "
            + "GROUP BY p.product</script>")
    List<Map<String, Object>> selectAnnualPlanStat(@Param("year") String year, @Param("accounts") List<String> accounts);

    /** 本年内创建的需求数（按产品 + 类型分组） */
    @Select("<script>SELECT product AS product, "
            + "SUM(CASE WHEN type = 'requirement' THEN 1 ELSE 0 END) AS requirement, "
            + "SUM(CASE WHEN type = 'story' THEN 1 ELSE 0 END) AS story, "
            + "SUM(CASE WHEN type = 'epic' THEN 1 ELSE 0 END) AS epic "
            + "FROM zt_story WHERE deleted = 0 AND LEFT(openedDate, 4) = #{year} "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND openedBy IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if> "
            + "GROUP BY product</script>")
    List<Map<String, Object>> selectAnnualCreatedStoryStat(@Param("year") String year,
                                                           @Param("accounts") List<String> accounts);

    /** 本年内关闭的需求数（按产品分组） */
    @Select("<script>SELECT product AS product, COUNT(1) AS closed FROM zt_story "
            + "WHERE deleted = 0 AND LEFT(closedDate, 4) = #{year} "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND closedBy IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if> "
            + "GROUP BY product</script>")
    List<Map<String, Object>> selectAnnualClosedStoryStat(@Param("year") String year,
                                                          @Param("accounts") List<String> accounts);

    /**
     * 本年内「有动静」的产品：本年创建的、我参与负责的、或者上面几个统计里出现过的。
     *
     * <p>禅道的条件里还有一条 {@code shadow = 0}（排除影子产品），本实现的 {@code zt_product}
     * 没有这一列（影子产品是禅道「项目没关联产品时自动建一个影子产品」的机制，本实现不做），
     * 所以这条条件去掉。
     */
    @Select("<script>SELECT id, name FROM zt_product WHERE deleted = 0 AND ("
            + "<choose>"
            + "<when test='accounts != null and accounts.size() > 0'>"
            + "(LEFT(createdDate, 4) = #{year} AND createdBy IN "
            + "<foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>) "
            + "OR PO IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach> "
            + "OR QD IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach> "
            + "OR RD IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</when>"
            + "<otherwise>LEFT(createdDate, 4) = #{year}</otherwise>"
            + "</choose>"
            + "<if test='extraIds != null and extraIds.size() > 0'>"
            + " OR id IN <foreach collection='extraIds' item='i' open='(' separator=',' close=')'>#{i}</foreach>"
            + "</if>"
            + ")</script>")
    List<Map<String, Object>> selectAnnualProducts(@Param("year") String year, @Param("accounts") List<String> accounts,
                                                   @Param("extraIds") List<Long> extraIds);

    /** 本年内完成的任务数（按执行分组） */
    @Select("<script>SELECT execution AS execution, COUNT(1) AS cnt FROM zt_task "
            + "WHERE deleted = 0 AND finishedBy &lt;&gt; '' AND LEFT(finishedDate, 4) = #{year} "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND finishedBy IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if> "
            + "GROUP BY execution</script>")
    List<Map<String, Object>> selectAnnualFinishedTaskStat(@Param("year") String year,
                                                           @Param("accounts") List<String> accounts);

    /**
     * 本年内有动静的执行：多迭代项目下的迭代（{@code type='sprint' AND multiple=1}），
     * 且开始或结束在本年（禅道 {@code getAnnualExecutionStat}）。
     */
    @Select("<script>SELECT e.id, e.name FROM zt_project e "
            + "WHERE e.deleted = 0 AND e.type = 'sprint' AND e.multiple = 1 "
            + "AND (LEFT(e.begin, 4) = #{year} OR LEFT(e.end, 4) = #{year}) "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + "AND ((e.openedBy IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>) "
            + "OR e.PM IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach> "
            + "OR e.PO IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach> "
            + "OR e.QD IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach> "
            + "OR e.RD IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "<if test='extraIds != null and extraIds.size() > 0'>"
            + " OR e.id IN <foreach collection='extraIds' item='i' open='(' separator=',' close=')'>#{i}</foreach>"
            + "</if>)"
            + "</if> "
            + "ORDER BY e.`order` DESC, e.id DESC</script>")
    List<Map<String, Object>> selectAnnualExecutions(@Param("year") String year, @Param("accounts") List<String> accounts,
                                                     @Param("extraIds") List<Long> extraIds);

    /** 某批执行里「已完成的需求」数（stage=verified/released 或 closedReason=done） */
    @Select("<script>SELECT ps.project AS project, COUNT(1) AS cnt "
            + "FROM zt_projectstory ps JOIN zt_story s ON s.id = ps.story "
            + "WHERE s.deleted = 0 AND (s.stage IN ('verified', 'released') OR s.closedReason = 'done') "
            + "AND ps.project IN <foreach collection='executionIds' item='e' open='(' separator=',' close=')'>#{e}</foreach> "
            + "GROUP BY ps.project</script>")
    List<Map<String, Object>> selectAnnualFinishedStoryStat(@Param("executionIds") List<Long> executionIds);

    /** 某批执行里「本年解决的缺陷」数 */
    @Select("<script>SELECT execution AS execution, COUNT(1) AS cnt FROM zt_bug "
            + "WHERE deleted = 0 AND status = 'closed' AND resolution = 'fixed' "
            + "AND LEFT(resolvedDate, 4) = #{year} "
            + "AND execution IN <foreach collection='executionIds' item='e' open='(' separator=',' close=')'>#{e}</foreach> "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND resolvedBy IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if> "
            + "GROUP BY execution</script>")
    List<Map<String, Object>> selectAnnualResolvedBugStat(@Param("year") String year,
                                                          @Param("executionIds") List<Long> executionIds,
                                                          @Param("accounts") List<String> accounts);

    /** 全量状态分布（公司视角才有：需求/任务/缺陷各状态有多少，不限年份） */
    @Select("SELECT status AS status, COUNT(1) AS cnt FROM zt_story "
            + "WHERE deleted = 0 AND type = 'story' GROUP BY status")
    List<Map<String, Object>> selectAllTimeStoryStatus();

    @Select("SELECT status AS status, COUNT(1) AS cnt FROM zt_task WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> selectAllTimeTaskStatus();

    @Select("SELECT status AS status, COUNT(1) AS cnt FROM zt_bug WHERE deleted = 0 GROUP BY status")
    List<Map<String, Object>> selectAllTimeBugStatus();

    /**
     * 「完成任务」的条数——**按 {@code finishedDate} 数，不按动作名**。
     *
     * <p>禅道有独立的 {@code finished} 动作可以直接数动作；本实现里「完成任务」
     * 是记成 {@code changed}（因为一次提交里既有状态变化又有工时变化，用 CHANGED 更能表达），
     * 动作名分不出「完成」与其它变更。而 {@code finishedDate/finishedBy} 是完成时写的**事实数据**，
     * 数它更准，也和禅道「年度执行里完成任务数」那条查询同一个口径（那边也是数 finishedDate）。
     */
    @Select("<script>SELECT COUNT(1) FROM zt_task WHERE deleted = 0 AND finishedBy &lt;&gt; '' "
            + "AND LEFT(finishedDate, 4) = #{year} "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND finishedBy IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if></script>")
    Long countFinishedTasks(@Param("year") String year, @Param("accounts") List<String> accounts);

    /** 「解决缺陷」的条数——同理，数 {@code resolvedDate/resolvedBy} 而不是动作名 */
    @Select("<script>SELECT COUNT(1) FROM zt_bug WHERE deleted = 0 AND resolvedBy &lt;&gt; '' "
            + "AND LEFT(resolvedDate, 4) = #{year} "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND resolvedBy IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if></script>")
    Long countResolvedBugs(@Param("year") String year, @Param("accounts") List<String> accounts);

    // ==================== 每日提醒 ====================

    /** 快到期的、指派出去的缺陷（deadline 为空或早于 nearDate，且不是 closed 伪用户） */
    @Select("SELECT t.id, t.title, t.assignedTo AS user, t.deadline FROM zt_bug t "
            + "JOIN system_users u ON u.username = t.assignedTo AND u.deleted = 0 "
            + "WHERE t.deleted = 0 AND t.assignedTo <> '' AND t.assignedTo <> 'closed' "
            + "AND (t.deadline IS NULL OR t.deadline < #{nearDate})")
    List<Map<String, Object>> selectReminderBugs(@Param("nearDate") String nearDate);

    /** 快到期的、未完成的任务（执行未挂起、项目未删除） */
    @Select("SELECT t.id, t.name, t.assignedTo AS user, t.deadline FROM zt_task t "
            + "JOIN zt_project e ON e.id = t.execution AND e.deleted = 0 "
            + "JOIN zt_project p ON p.id = t.project AND p.deleted = 0 "
            + "JOIN system_users u ON u.username = t.assignedTo AND u.deleted = 0 "
            + "WHERE t.deleted = 0 AND t.assignedTo <> '' AND t.status IN ('wait', 'doing') "
            + "AND e.status <> 'suspended' "
            + "AND (t.deadline IS NULL OR t.deadline < #{nearDate})")
    List<Map<String, Object>> selectReminderTasks(@Param("nearDate") String nearDate);

    /** 未完成的待办（非周期待办；归属人取 assignedTo，没指派就看 account） */
    @Select("SELECT t.id, t.name, t.type, t.objectID, t.date, t.status, "
            + "CASE WHEN t.assignedTo <> '' THEN t.assignedTo ELSE t.account END AS user "
            + "FROM zt_todo t JOIN system_users u ON u.username = "
            + "(CASE WHEN t.assignedTo <> '' THEN t.assignedTo ELSE t.account END) AND u.deleted = 0 "
            + "WHERE t.deleted = 0 AND (t.cycle = 0 OR t.cycle IS NULL) AND t.status IN ('wait', 'doing')")
    List<Map<String, Object>> selectReminderTodos();

    /** 未完成的测试单（wait/doing），按负责人 */
    @Select("SELECT t.id, t.name, t.owner AS user, t.begin, t.end, t.status FROM zt_testtask t "
            + "JOIN system_users u ON u.username = t.owner AND u.deleted = 0 "
            + "WHERE t.deleted = 0 AND t.status IN ('wait', 'doing')")
    List<Map<String, Object>> selectReminderTestTasks();

    /** 快到期且未完成的看板卡片（看板是激活态；assignedTo 是逗号列表） */
    @Select("SELECT c.id, c.name, c.assignedTo, c.end AS deadline, c.kanban FROM zt_kanbancard c "
            + "JOIN zt_kanban k ON k.id = c.kanban AND k.deleted = 0 "
            + "WHERE c.deleted = 0 AND c.assignedTo <> '' AND k.status = 'active' "
            + "AND (c.progress IS NULL OR c.progress < 100) AND (c.archived = 0 OR c.archived IS NULL) "
            + "AND c.end IS NOT NULL AND c.end < #{nearDate}")
    List<Map<String, Object>> selectReminderCards(@Param("nearDate") String nearDate);

    // ==================== 项目状态总览 ====================

    /** 按团队成员过滤的项目状态分布 */
    @Select("<script>SELECT p.status AS status, COUNT(DISTINCT p.id) AS cnt FROM zt_project p "
            + "JOIN zt_team t ON t.root = p.id AND t.type = 'project' "
            + "WHERE p.deleted = 0 AND p.type = 'project' "
            + "<if test='accounts != null and accounts.size() > 0'>"
            + " AND t.account IN <foreach collection='accounts' item='a' open='(' separator=',' close=')'>#{a}</foreach>"
            + "</if> "
            + "GROUP BY p.status</script>")
    List<Map<String, Object>> selectProjectStatusStat(@Param("accounts") List<String> accounts);

    // ==================== 部门 / 人员选项 ====================

    /** 最早一条动作的年份 —— 禅道用它决定「系统用了几年」 */
    @Select("SELECT MIN(LEFT(date, 4)) FROM zt_action")
    String selectFirstActionYear();

}
