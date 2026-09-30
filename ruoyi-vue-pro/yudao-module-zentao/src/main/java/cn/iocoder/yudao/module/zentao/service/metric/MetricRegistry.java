package cn.iocoder.yudao.module.zentao.service.metric;

import cn.iocoder.yudao.module.zentao.dal.mysql.metric.MetricQueryMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内置度量口径注册表。
 *
 * <p>禅道的口径实现是 {@code module/metric/calc/**} 下 414 个 calc 类（一个类一个度量项，
 * 类名就是 {@code zt_metric.code}）。本迁移把「框架」搬过来、口径按需补：
 * 这里用一张 {@code code → SQL} 的表代替 414 个类，**每个条目都注明它对应哪个 calc 类**，
 * 未登记的 code 在计算时明确报「口径尚未迁移」，而不是算出一个错的值。
 *
 * <p>只登记「只用已迁移的表就能算出来」的口径（15 个），其余属于数据资产，后续按需补。
 */
@Component
public class MetricRegistry {

    private final MetricQueryMapper queryMapper;

    /** 顺序即展示顺序；key = zt_metric.code */
    private final Map<String, Query> calculators = new LinkedHashMap<>();

    /** 一个口径 = 一段取数 SQL（维度列名与 zt_metriclib 的列同名） */
    @FunctionalInterface
    public interface Query {
        List<Map<String, Object>> run(MetricQueryMapper mapper);
    }

    public MetricRegistry(MetricQueryMapper queryMapper) {
        this.queryMapper = queryMapper;
        // 产品维度
        register("count_of_story_in_product", MetricQueryMapper::countOfStoryInProduct);
        register("count_of_bug_in_product", MetricQueryMapper::countOfBugInProduct);
        register("count_of_case_in_product", MetricQueryMapper::countOfCaseInProduct);
        register("count_of_release_in_product", MetricQueryMapper::countOfReleaseInProduct);
        register("count_of_productplan_in_product", MetricQueryMapper::countOfProductplanInProduct);
        // 项目维度
        register("count_of_execution_in_project", MetricQueryMapper::countOfExecutionInProject);
        register("count_of_user_in_project", MetricQueryMapper::countOfUserInProject);
        register("consume_of_task_in_project", MetricQueryMapper::consumeOfTaskInProject);
        register("consume_of_all_in_project", MetricQueryMapper::consumeOfAllInProject);
        // 年度新增
        register("count_of_annual_created_story_in_product", MetricQueryMapper::countOfAnnualCreatedStoryInProduct);
        register("count_of_annual_created_bug_in_product", MetricQueryMapper::countOfAnnualCreatedBugInProduct);
        register("count_of_annual_created_case_in_product", MetricQueryMapper::countOfAnnualCreatedCaseInProduct);
        // 人员维度
        register("count_of_story_in_user", MetricQueryMapper::countOfStoryInUser);
        register("count_of_created_bug_in_user", MetricQueryMapper::countOfCreatedBugInUser);
        register("count_of_created_case_in_user", MetricQueryMapper::countOfCreatedCaseInUser);
    }

    private void register(String code, Query query) {
        calculators.put(code, query);
    }

    public boolean isImplemented(String code) {
        return calculators.containsKey(code);
    }

    public Set<String> getCodes() {
        return calculators.keySet();
    }

    /**
     * 跑口径取数。未迁移的 code 返回 null（由调用方报错），不抛异常 —— 这样批量计算可以跳过。
     */
    public List<Map<String, Object>> calculate(String code) {
        Query query = calculators.get(code);
        return query == null ? null : query.run(queryMapper);
    }

}
