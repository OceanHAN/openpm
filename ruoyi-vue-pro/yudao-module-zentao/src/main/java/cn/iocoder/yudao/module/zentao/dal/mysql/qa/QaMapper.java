package cn.iocoder.yudao.module.zentao.dal.mysql.qa;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 测试仪表盘（禅道 {@code qa.index} → {@code block} 的 qa 看板）的只读聚合。
 *
 * <h3>为什么是 SQL 而不是 Wrapper</h3>
 * 与报表模块同理：全是「按产品 group by + 条件计数」，Wrapper 表达不了。
 *
 * <h3>口径从哪来</h3>
 * 禅道的 qa 看板本身只负责「摆积木」，真正的口径在 {@code module/bi/config/metrics.php}
 * 的度量定义里（看板调 {@code metric->getResultByCodeWithArray('count_of_daily_created_bug_in_product')}）。
 * 本实现不引入 metric 框架，直接把那几条口径写成 SQL，并在每条上面标出对应的度量 code：
 * <ul>
 *   <li>{@code count_of_daily_created_bug_in_product}：产品中新增 Bug 个数求和、创建日期在某区间；</li>
 *   <li>{@code count_of_daily_resolved_bug_in_product} / {@code count_of_daily_closed_bug_in_product}：解决/关闭日期在某区间；</li>
 *   <li>{@code count_of_effective_bug_in_product}：**解决方案为已修复/延期处理/不予解决，或状态为激活**；</li>
 *   <li>{@code count_of_fixed_bug_in_product}：解决方案为已修复；</li>
 *   <li>{@code rate_of_fixed_bug_in_product}：已修复 ÷ 有效缺陷。</li>
 * </ul>
 * 「有效缺陷」的这条定义是最容易写错的：它不是「缺陷总数」，而是「还没被判定为无效的那些」
 * （重复/设计如此/不做 都不算有效），所以修复率的分母比缺陷总数小。
 */
@Mapper
public interface QaMapper {

    /** 有效缺陷的判定片段（三处复用，改动时一起改） */
    String EFFECTIVE_BUG = " (b.status = 'active' OR b.resolution IN ('fixed', 'postponed', 'willnotfix')) ";

    /**
     * 按产品的质量统计（qa 看板的 statistic 那一块）。
     * 一个产品一行：区间内新增/解决/关闭，当前有效/已修复/激活，以及未完成测试单数。
     */
    @Select("SELECT p.id AS product, p.name AS productName, "
            + "(SELECT COUNT(1) FROM zt_bug b WHERE b.product = p.id AND b.deleted = 0 AND b.openedDate >= #{begin}) AS opened, "
            + "(SELECT COUNT(1) FROM zt_bug b WHERE b.product = p.id AND b.deleted = 0 AND b.resolvedDate >= #{begin}) AS resolved, "
            + "(SELECT COUNT(1) FROM zt_bug b WHERE b.product = p.id AND b.deleted = 0 AND b.closedDate >= #{begin}) AS closed, "
            + "(SELECT COUNT(1) FROM zt_bug b WHERE b.product = p.id AND b.deleted = 0 AND " + EFFECTIVE_BUG + ") AS effective, "
            + "(SELECT COUNT(1) FROM zt_bug b WHERE b.product = p.id AND b.deleted = 0 AND b.resolution = 'fixed') AS fixed, "
            + "(SELECT COUNT(1) FROM zt_bug b WHERE b.product = p.id AND b.deleted = 0 AND b.status = 'active') AS active, "
            + "(SELECT COUNT(1) FROM zt_testtask t WHERE t.product = p.id AND t.deleted = 0 "
            + "   AND t.status IN ('wait', 'doing')) AS unclosedTestTasks, "
            + "(SELECT COUNT(1) FROM zt_case c WHERE c.product = p.id AND c.deleted = 0 AND c.lib = 0 AND c.status = 'wait') AS reviewCases "
            + "FROM zt_product p WHERE p.deleted = 0 ORDER BY p.id")
    List<Map<String, Object>> selectProductQuality(@Param("begin") String begin);

    /** 缺陷状态分布（含产品过滤） */
    @Select("<script>SELECT b.status AS name, COUNT(1) AS value FROM zt_bug b "
            + "WHERE b.deleted = 0 <if test='product != null'> AND b.product = #{product}</if> "
            + "GROUP BY b.status</script>")
    List<Map<String, Object>> selectBugStatusStat(@Param("product") Long product);

    /** 缺陷严重程度分布 */
    @Select("<script>SELECT b.severity AS name, COUNT(1) AS value FROM zt_bug b "
            + "WHERE b.deleted = 0 <if test='product != null'> AND b.product = #{product}</if> "
            + "GROUP BY b.severity ORDER BY b.severity</script>")
    List<Map<String, Object>> selectBugSeverityStat(@Param("product") Long product);

    /** 缺陷解决方案分布（只统计已解决的） */
    @Select("<script>SELECT b.resolution AS name, COUNT(1) AS value FROM zt_bug b "
            + "WHERE b.deleted = 0 AND b.resolution &lt;&gt; '' "
            + "<if test='product != null'> AND b.product = #{product}</if> "
            + "GROUP BY b.resolution</script>")
    List<Map<String, Object>> selectBugResolutionStat(@Param("product") Long product);

    /** 用例状态分布（只算产品用例，库用例不算） */
    @Select("<script>SELECT c.status AS name, COUNT(1) AS value FROM zt_case c "
            + "WHERE c.deleted = 0 AND c.lib = 0 <if test='product != null'> AND c.product = #{product}</if> "
            + "GROUP BY c.status</script>")
    List<Map<String, Object>> selectCaseStatusStat(@Param("product") Long product);

    /** 用例最近执行结果分布 */
    @Select("<script>SELECT c.lastRunResult AS name, COUNT(1) AS value FROM zt_case c "
            + "WHERE c.deleted = 0 AND c.lib = 0 AND c.lastRunResult &lt;&gt; '' "
            + "<if test='product != null'> AND c.product = #{product}</if> "
            + "GROUP BY c.lastRunResult</script>")
    List<Map<String, Object>> selectCaseResultStat(@Param("product") Long product);

    /** 测试单状态分布 */
    @Select("<script>SELECT t.status AS name, COUNT(1) AS value FROM zt_testtask t "
            + "WHERE t.deleted = 0 <if test='product != null'> AND t.product = #{product}</if> "
            + "GROUP BY t.status</script>")
    List<Map<String, Object>> selectTestTaskStatusStat(@Param("product") Long product);

    /** 待处理缺陷（激活态，按严重程度、优先级排） */
    @Select("<script>SELECT b.id, b.title, b.product, b.severity, b.pri, b.assignedTo, b.deadline, b.openedDate "
            + "FROM zt_bug b WHERE b.deleted = 0 AND b.status = 'active' "
            + "<if test='product != null'> AND b.product = #{product}</if> "
            + "ORDER BY b.severity ASC, b.pri ASC, b.id DESC LIMIT #{limit}</script>")
    List<Map<String, Object>> selectPendingBugs(@Param("product") Long product, @Param("limit") int limit);

    /** 待评审用例（status=wait） */
    @Select("<script>SELECT c.id, c.title, c.product, c.type, c.stage, c.pri, c.openedBy, c.openedDate "
            + "FROM zt_case c WHERE c.deleted = 0 AND c.lib = 0 AND c.status = 'wait' "
            + "<if test='product != null'> AND c.product = #{product}</if> "
            + "ORDER BY c.id DESC LIMIT #{limit}</script>")
    List<Map<String, Object>> selectReviewCases(@Param("product") Long product, @Param("limit") int limit);

    /** 未完成的测试单（wait/doing） */
    @Select("<script>SELECT t.id, t.name, t.product, t.execution, t.build, t.owner, t.begin, t.end, t.status "
            + "FROM zt_testtask t WHERE t.deleted = 0 AND t.status IN ('wait', 'doing') "
            + "<if test='product != null'> AND t.product = #{product}</if> "
            + "ORDER BY t.id DESC LIMIT #{limit}</script>")
    List<Map<String, Object>> selectUnclosedTestTasks(@Param("product") Long product, @Param("limit") int limit);

}
