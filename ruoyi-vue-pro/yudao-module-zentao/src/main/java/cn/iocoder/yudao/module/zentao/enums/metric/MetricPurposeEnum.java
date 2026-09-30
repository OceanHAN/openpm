package cn.iocoder.yudao.module.zentao.enums.metric;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 度量目的（禅道 config->metric->purposeList）
 */
@Getter
@AllArgsConstructor
public enum MetricPurposeEnum {

    SCALE("scale", "规模估算"),
    QC("qc", "质量控制"),
    HOUR("hour", "工时统计"),
    COST("cost", "成本计算"),
    RATE("rate", "效率提升"),
    TIME("time", "工期控制");

    private final String value;

    private final String name;

    public static MetricPurposeEnum of(String value) {
        for (MetricPurposeEnum item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return null;
    }

    public static String nameOf(String value) {
        MetricPurposeEnum item = of(value);
        return item != null ? item.name : String.valueOf(value);
    }
}
