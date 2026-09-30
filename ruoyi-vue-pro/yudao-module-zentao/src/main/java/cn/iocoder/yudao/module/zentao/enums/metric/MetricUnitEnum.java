package cn.iocoder.yudao.module.zentao.enums.metric;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 度量单位（禅道 $lang->metric->unitList）
 */
@Getter
@AllArgsConstructor
public enum MetricUnitEnum {

    COUNT("count", "个"),
    MEASURE("measure", "工时"),
    HOUR("hour", "小时"),
    DAY("day", "天"),
    MANDAY("manday", "人天"),
    PERCENTAGE("percentage", "百分比"),
    TIMES("times", "次"),
    PEOPLE("people", "人"),
    ROW("row", "行");

    private final String value;

    private final String name;

    public static MetricUnitEnum of(String value) {
        for (MetricUnitEnum item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return null;
    }

    public static String nameOf(String value) {
        MetricUnitEnum item = of(value);
        return item != null ? item.name : String.valueOf(value);
    }
}
