package cn.iocoder.yudao.module.zentao.enums.bi;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 聚合方式。指标的算法：count 数个数 / sum 求和 / avg 平均 / max / min。
 *
 * <p>只允许这 5 种（白名单），拼进 SQL 的是枚举自带的函数名，**不接受用户直接传 SQL 片段**。
 */
@Getter
@AllArgsConstructor
public enum AggTypeEnum {

    COUNT("count", "计数", "COUNT"),
    SUM("sum", "求和", "SUM"),
    AVG("avg", "平均值", "AVG"),
    MAX("max", "最大值", "MAX"),
    MIN("min", "最小值", "MIN");

    private final String agg;

    private final String name;

    private final String function;

    public static AggTypeEnum of(String agg) {
        for (AggTypeEnum item : values()) {
            if (item.agg.equals(agg)) {
                return item;
            }
        }
        return null;
    }

}
