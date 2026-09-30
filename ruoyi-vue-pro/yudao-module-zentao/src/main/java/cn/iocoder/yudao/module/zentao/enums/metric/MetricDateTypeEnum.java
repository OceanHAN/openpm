package cn.iocoder.yudao.module.zentao.enums.metric;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 度量时间维度（决定 zt_metriclib 里填哪些时间列、以及重算时按什么周期清旧数据）
 */
@Getter
@AllArgsConstructor
public enum MetricDateTypeEnum {

    NODATE("nodate", "快照（不按时间分粒度）"),
    YEAR("year", "年"),
    MONTH("month", "月"),
    WEEK("week", "周"),
    DAY("day", "日");

    private final String value;

    private final String name;

    public static MetricDateTypeEnum of(String value) {
        for (MetricDateTypeEnum item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return null;
    }

    public static String nameOf(String value) {
        MetricDateTypeEnum item = of(value);
        return item != null ? item.name : String.valueOf(value);
    }

    /** 是否需要写时间列 */
    public boolean isDated() {
        return this != NODATE;
    }
}
