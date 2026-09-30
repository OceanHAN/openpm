package cn.iocoder.yudao.module.zentao.controller.admin.burn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 燃尽图数据（禅道 {@code execution::buildBurnData}）。
 *
 * <p>三条线的长度都和 {@code labels} 一致，前端直接丢给 ECharts：
 * <ul>
 *   <li>{@code burnLine}：实际剩余（或实际原计划，看 {@code burnBy}），来自 {@code zt_burn} 的每日快照，缺失的日期用前一个快照补齐；</li>
 *   <li>{@code baseLine}：理想线 —— 从第一天的工作量线性降到执行结束那天为 0；</li>
 *   <li>{@code delayLine}：延期线 —— 执行结束日之后的那段实际值（只有真的延期了才给）。</li>
 * </ul>
 */
@Schema(description = "管理后台 - 燃尽图数据 Response VO")
@Data
public class BurnChartRespVO {

    @Schema(description = "执行编号", example = "90001")
    private Long executionId;

    @Schema(description = "执行名称", example = "V1.0迭代")
    private String executionName;

    @Schema(description = "执行开始日期", example = "2026-01-05")
    private String begin;

    @Schema(description = "执行计划结束日期", example = "2026-02-28")
    private String end;

    @Schema(description = "曲线取值字段：left 剩余工时 / estimate 原计划 / consumed 已消耗 / storyPoint 需求规模", example = "left")
    private String burnBy;

    @Schema(description = "日期过滤：noweekend 跳过周末 / weekend 含周末 / withdelay 含延期段", example = "noweekend,withdelay")
    private String type;

    @Schema(description = "采样间隔（0 表示每天一个点）", example = "5")
    private Integer interval;

    @Schema(description = "横轴标签（YYYY-MM-DD）")
    private List<String> labels = new ArrayList<>();

    @Schema(description = "实际曲线（与 labels 等长）")
    private List<Double> burnLine = new ArrayList<>();

    @Schema(description = "理想曲线（与 labels 等长，执行结束日之后为 0）")
    private List<Double> baseLine = new ArrayList<>();

    @Schema(description = "延期曲线（只有延期时才返回）")
    private List<Double> delayLine;

    @Schema(description = "已落库的每日快照原始值（date -> 各字段）")
    private List<Map<String, Object>> rows = new ArrayList<>();

    @Schema(description = "理想线的起点值（第一天的工作量）", example = "120")
    private Double firstValue;

}
