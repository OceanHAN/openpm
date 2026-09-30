package cn.iocoder.yudao.module.zentao.enums.metric;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 度量计算方式（zt_metriclib.calcType）：禅道定时任务算的标 cron，人在界面上点「计算」的标 inference
 */
@Getter
@AllArgsConstructor
public enum MetricCalcTypeEnum {

    CRON("cron", "定时采集"),
    INFERENCE("inference", "人工触发");

    private final String value;

    private final String name;

    public static MetricCalcTypeEnum of(String value) {
        for (MetricCalcTypeEnum item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return null;
    }

    public static String nameOf(String value) {
        MetricCalcTypeEnum item = of(value);
        return item != null ? item.name : String.valueOf(value);
    }
}
