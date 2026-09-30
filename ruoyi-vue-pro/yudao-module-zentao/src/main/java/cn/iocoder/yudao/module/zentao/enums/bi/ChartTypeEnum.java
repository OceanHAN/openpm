package cn.iocoder.yudao.module.zentao.enums.bi;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 图表类型（禅道 {@code $lang->chart->typeList}）
 *
 * <p>{@code echarts} 表示前端要不要渲染成图表：水球图/雷达图本实现没做（类型保留，渲染时退化成柱状图）。
 */
@Getter
@AllArgsConstructor
public enum ChartTypeEnum {

    PIE("pie", "饼图", "pie"),
    LINE("line", "折线图", "line"),
    WATER_POLO("waterpolo", "水球图", "bar"),
    RADAR("radar", "雷达图", "bar"),
    CLU_BAR_Y("cluBarY", "簇状条形图", "bar"),
    STACKED_BAR_Y("stackedBarY", "堆积条形图", "bar"),
    CLU_BAR_X("cluBarX", "簇状柱形图", "bar"),
    STACKED_BAR("stackedBar", "堆积柱形图", "bar");

    private final String type;

    private final String name;

    /** 前端渲染用的类型（echarts 的 pie/line/bar） */
    private final String echarts;

    public static ChartTypeEnum of(String type) {
        for (ChartTypeEnum item : values()) {
            if (item.type.equals(type)) {
                return item;
            }
        }
        return null;
    }

    public static String nameOf(String type) {
        ChartTypeEnum item = of(type);
        return item != null ? item.name : String.valueOf(type);
    }

    public static String echartsOf(String type) {
        ChartTypeEnum item = of(type);
        return item != null ? item.echarts : "bar";
    }

}
