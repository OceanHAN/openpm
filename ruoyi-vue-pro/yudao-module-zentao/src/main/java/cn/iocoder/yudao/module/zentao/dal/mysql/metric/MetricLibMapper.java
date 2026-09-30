package cn.iocoder.yudao.module.zentao.dal.mysql.metric;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.metric.MetricLibDO;
import cn.iocoder.yudao.module.zentao.enums.metric.MetricDateTypeEnum;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 度量数据 Mapper
 */
@Mapper
public interface MetricLibMapper extends BaseMapperX<MetricLibDO> {

    /**
     * 按度量项 + 范围查数据（时间区间在 Service 里按 dateType 拼）。
     *
     * <p>必须带上度量项的 {@code dateType}：nodate 型会额外过滤掉历史快照（见 {@link #applyNodateFilter}）。
     */
    default List<MetricLibDO> selectListByCode(String code, String scope, String dateType) {
        // 注意：orderByAsc 没有 X 版本（返回基类型），所以不能把它接在赋给 X 变量的链式调用末尾，
        // 否则整条表达式退化成 LambdaQueryWrapper、赋不回 X（踩过一次，见 MetricLibMapper 注释）
        LambdaQueryWrapperX<MetricLibDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.eq(MetricLibDO::getMetricCode, code);
        applyNodateFilter(wrapper, dateType);
        wrapper.orderByDesc(MetricLibDO::getDate);
        wrapper.orderByAsc(MetricLibDO::getId);
        applyScope(wrapper, scope);
        return selectList(wrapper);
    }

    default PageResult<MetricLibDO> selectPageByCode(PageParam pageParam, String code, String scope, String dateType) {
        LambdaQueryWrapperX<MetricLibDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.eq(MetricLibDO::getMetricCode, code);
        // nodate 型只保留「今天算的」快照（禅道 tao.php: fetchMetricRecordsWithOption 用 date >= today）
        applyNodateFilter(wrapper, dateType);
        wrapper.orderByDesc(MetricLibDO::getDate);
        wrapper.orderByAsc(MetricLibDO::getId);
        applyScope(wrapper, scope);
        return selectPage(pageParam, wrapper);
    }

    /**
     * nodate（快照）型只认「今天算的那份」，把历史快照挡在查询之外。
     *
     * <p>禅道 {@code model.php#clearOutDatedRecords} **没有 nodate 分支** —— nodate 的历史快照是
     * 故意留在 zt_metriclib 里的（不像 year/month/week/day 会按周期清旧）。所以「读当前值」的每条路径
     * 都必须自己加 {@code date >= today}（禅道 {@code tao.php#fetchMetricRecordsWithOption} 就是这么做的），
     * 否则多天累积的快照会被当成同一份当前值重复统计（表现为接口值 = 真值 × 累积天数）。
     *
     * <p>周期型（year/month/week/day）**一个字都不动**：它们按 year/month/week/day 区间取值，且每次
     * 计算都会 {@code deleteByPeriod} 清掉同周期旧数据，不存在跨天累积。
     */
    default void applyNodateFilter(LambdaQueryWrapperX<MetricLibDO> wrapper, String dateType) {
        if (MetricDateTypeEnum.NODATE.getValue().equals(dateType)) {
            wrapper.apply("`date` >= CURDATE()");
        }
    }

    /**
     * 按范围加维度条件。
     *
     * <p>注意用的是 {@code apply} 而不是 {@code ne(...)}：{@code LambdaQueryWrapperX} 只覆写了
     * {@code eq/orderByDesc/in} 等少数方法，{@code ne} 会退化成基类型、赋不回 X 变量（第 43 条坑的同族问题）。
     * 这里的条件全是写死的列名比较，没有用户输入，拼 SQL 是安全的。
     */
    default void applyScope(LambdaQueryWrapperX<MetricLibDO> wrapper, String scope) {
        if ("system".equals(scope)) {
            wrapper.eq(MetricLibDO::getSystem, 1);
        } else if ("project".equals(scope)) {
            wrapper.apply("project <> 0");
        } else if ("product".equals(scope)) {
            wrapper.apply("product <> 0");
        } else if ("execution".equals(scope)) {
            wrapper.apply("execution <> 0");
        } else if ("user".equals(scope)) {
            wrapper.apply("`user` <> ''");
        } else if ("program".equals(scope)) {
            wrapper.apply("program <> 0");
        }
    }

    /** 清掉某个周期的旧数据（禅道 clearOutDatedRecords：year → year+month → year+week → year+month+day） */
    @Delete("DELETE FROM zt_metriclib WHERE metricCode = #{code} AND `year` = #{year} "
            + "AND (#{month} IS NULL OR `month` = #{month}) AND (#{week} IS NULL OR `week` = #{week}) "
            + "AND (#{day} IS NULL OR `day` = #{day})")
    int deleteByPeriod(@Param("code") String code, @Param("year") String year,
                       @Param("month") String month, @Param("week") String week, @Param("day") String day);

    /** nodate（快照）型：同一天只留一份 */
    @Delete("DELETE FROM zt_metriclib WHERE metricCode = #{code} AND `date` >= #{begin}")
    int deleteByDateFrom(@Param("code") String code, @Param("begin") java.time.LocalDateTime begin);

    /**
     * 数据量：读当前值的路径，nodate 型同样只算今天那份（历史快照不算，见 {@link #applyNodateFilter}）。
     */
    default Long countByCode(String code, String dateType) {
        LambdaQueryWrapperX<MetricLibDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.eq(MetricLibDO::getMetricCode, code);
        applyNodateFilter(wrapper, dateType);
        return selectCount(wrapper);
    }

}
