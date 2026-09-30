package cn.iocoder.yudao.module.zentao.enums.metric;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 度量范围（禅道 config->metric->scopeList）：决定数据落在 zt_metriclib 的哪个维度列上
 */
@Getter
@AllArgsConstructor
public enum MetricScopeEnum {

    SYSTEM("system", "系统"),
    PROGRAM("program", "项目集"),
    PROJECT("project", "项目"),
    PRODUCT("product", "产品"),
    EXECUTION("execution", "执行"),
    USER("user", "人员");

    private final String value;

    private final String name;

    public static MetricScopeEnum of(String value) {
        for (MetricScopeEnum item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        return null;
    }

    public static String nameOf(String value) {
        MetricScopeEnum item = of(value);
        return item != null ? item.name : String.valueOf(value);
    }
}
