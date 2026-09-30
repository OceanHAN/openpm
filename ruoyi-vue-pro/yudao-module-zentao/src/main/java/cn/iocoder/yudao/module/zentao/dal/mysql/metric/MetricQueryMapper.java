package cn.iocoder.yudao.module.zentao.dal.mysql.metric;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 内置度量口径的取数 SQL。
 *
 * <p>每一个方法对应禅道 {@code module/metric/calc/**} 里的一个 calc 类（方法名就是度量项 code），
 * 返回「维度 → 值」的行；维度列名与 {@code zt_metriclib} 的列同名（product/project/execution/user/year/...），
 * 这样上层可以**不做映射**直接落库 —— 这也是禅道 calc 类的做法（getResult 返回的就是库表字段）。
 *
 * <p>共同口径：只统计未删除的数据（禅道各 dataset 的过滤条件见每个 calc 类的「定义」）。
 */
@Mapper
public interface MetricQueryMapper {

    /** module/metric/calc/product/scale/count_of_story_in_product.php：产品下研发需求总数 */
    @Select("SELECT product, COUNT(*) AS value FROM zt_story "
            + "WHERE deleted = 0 AND product > 0 AND type = 'story' GROUP BY product")
    List<Map<String, Object>> countOfStoryInProduct();

    /** module/metric/calc/product/scale/count_of_bug_in_product.php：产品下 Bug 总数 */
    @Select("SELECT product, COUNT(*) AS value FROM zt_bug "
            + "WHERE deleted = 0 AND product > 0 GROUP BY product")
    List<Map<String, Object>> countOfBugInProduct();

    /** module/metric/calc/product/qc/count_of_case_in_product.php：产品下用例总数（不含用例库里的） */
    @Select("SELECT product, COUNT(*) AS value FROM zt_case "
            + "WHERE deleted = 0 AND product > 0 AND lib = 0 GROUP BY product")
    List<Map<String, Object>> countOfCaseInProduct();

    /** module/metric/calc/product/scale/count_of_release_in_product.php：产品下发布总数 */
    @Select("SELECT product, COUNT(*) AS value FROM zt_release "
            + "WHERE deleted = 0 AND product > 0 GROUP BY product")
    List<Map<String, Object>> countOfReleaseInProduct();

    /** module/metric/calc/product/scale/count_of_productplan_in_product.php：产品下计划总数 */
    @Select("SELECT product, COUNT(*) AS value FROM zt_productplan "
            + "WHERE deleted = 0 AND product > 0 GROUP BY product")
    List<Map<String, Object>> countOfProductplanInProduct();

    /** module/metric/calc/project/scale/count_of_execution_in_project.php：项目下执行总数 */
    @Select("SELECT project, COUNT(*) AS value FROM zt_project "
            + "WHERE deleted = 0 AND project > 0 AND type IN ('sprint', 'stage', 'kanban') GROUP BY project")
    List<Map<String, Object>> countOfExecutionInProject();

    /** module/metric/calc/project/scale/count_of_user_in_project.php：项目团队成员数 */
    @Select("SELECT root AS project, COUNT(DISTINCT account) AS value FROM zt_team "
            + "WHERE type = 'project' GROUP BY root")
    List<Map<String, Object>> countOfUserInProject();

    /** module/metric/calc/project/hour/consume_of_task_in_project.php：项目内任务消耗工时 */
    @Select("SELECT project, ROUND(SUM(consumed), 2) AS value FROM zt_effort "
            + "WHERE deleted = 0 AND objectType = 'task' AND project > 0 GROUP BY project")
    List<Map<String, Object>> consumeOfTaskInProject();

    /** module/metric/calc/project/hour/consume_of_all_in_project.php：项目内所有消耗工时 */
    @Select("SELECT project, ROUND(SUM(consumed), 2) AS value FROM zt_effort "
            + "WHERE deleted = 0 AND project > 0 GROUP BY project")
    List<Map<String, Object>> consumeOfAllInProject();

    /** module/metric/calc/product/scale/count_of_annual_created_story_in_product.php：按年统计的产品新增研发需求 */
    @Select("SELECT product, YEAR(openedDate) AS year, COUNT(*) AS value FROM zt_story "
            + "WHERE deleted = 0 AND product > 0 AND type = 'story' AND openedDate IS NOT NULL "
            + "GROUP BY product, YEAR(openedDate)")
    List<Map<String, Object>> countOfAnnualCreatedStoryInProduct();

    /** module/metric/calc/product/scale/count_of_annual_created_bug_in_product.php：按年统计的产品新增 Bug */
    @Select("SELECT product, YEAR(openedDate) AS year, COUNT(*) AS value FROM zt_bug "
            + "WHERE deleted = 0 AND product > 0 AND openedDate IS NOT NULL GROUP BY product, YEAR(openedDate)")
    List<Map<String, Object>> countOfAnnualCreatedBugInProduct();

    /** module/metric/calc/product/qc/count_of_annual_created_case_in_product.php：按年统计的产品新增用例 */
    @Select("SELECT product, YEAR(openedDate) AS year, COUNT(*) AS value FROM zt_case "
            + "WHERE deleted = 0 AND product > 0 AND lib = 0 AND openedDate IS NOT NULL "
            + "GROUP BY product, YEAR(openedDate)")
    List<Map<String, Object>> countOfAnnualCreatedCaseInProduct();

    /** module/metric/calc/user/scale/count_of_story_in_user.php：按创建人统计的研发需求数 */
    @Select("SELECT openedBy AS user, COUNT(*) AS value FROM zt_story "
            + "WHERE deleted = 0 AND type = 'story' AND openedBy <> '' GROUP BY openedBy")
    List<Map<String, Object>> countOfStoryInUser();

    /** module/metric/calc/user/scale/count_of_created_bug_in_user.php：按创建人统计的 Bug 数 */
    @Select("SELECT openedBy AS user, COUNT(*) AS value FROM zt_bug "
            + "WHERE deleted = 0 AND openedBy <> '' GROUP BY openedBy")
    List<Map<String, Object>> countOfCreatedBugInUser();

    /** module/metric/calc/user/scale/count_of_created_case_in_user.php：按创建人统计的用例数 */
    @Select("SELECT openedBy AS user, COUNT(*) AS value FROM zt_case "
            + "WHERE deleted = 0 AND lib = 0 AND openedBy <> '' GROUP BY openedBy")
    List<Map<String, Object>> countOfCreatedCaseInUser();

}
