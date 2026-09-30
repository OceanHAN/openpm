package cn.iocoder.yudao.module.zentao.controller.admin.qa.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试仪表盘（禅道 {@code qa.index} 渲染的 {@code block} qa 看板）。
 *
 * <p>四块内容与禅道看板一一对应：
 * <ul>
 *   <li>{@code statistic}：按产品的质量统计（新增/解决/关闭、有效缺陷、修复率、未完成测试单）；</li>
 *   <li>{@code bug}：待处理缺陷；</li>
 *   <li>{@code case}：待评审用例；</li>
 *   <li>{@code testtask}：未完成测试单。</li>
 * </ul>
 */
@Schema(description = "管理后台 - 测试仪表盘 Response VO")
@Data
public class QaDashboardRespVO {

    @Schema(description = "统计区间：最近 N 天", example = "7")
    private Integer days;

    @Schema(description = "区间起始日期（含）", example = "2026-09-08")
    private String begin;

    @Schema(description = "只看某个产品（不传=全部产品）", example = "1")
    private Long product;

    @Schema(description = "汇总卡片")
    private Summary summary = new Summary();

    @Schema(description = "按产品的质量统计")
    private List<Map<String, Object>> productQuality = new ArrayList<>();

    @Schema(description = "缺陷状态分布")
    private List<Map<String, Object>> bugStatus = new ArrayList<>();

    @Schema(description = "缺陷严重程度分布")
    private List<Map<String, Object>> bugSeverity = new ArrayList<>();

    @Schema(description = "缺陷解决方案分布")
    private List<Map<String, Object>> bugResolution = new ArrayList<>();

    @Schema(description = "用例状态分布")
    private List<Map<String, Object>> caseStatus = new ArrayList<>();

    @Schema(description = "用例最近执行结果分布")
    private List<Map<String, Object>> caseResult = new ArrayList<>();

    @Schema(description = "测试单状态分布")
    private List<Map<String, Object>> testTaskStatus = new ArrayList<>();

    @Schema(description = "待处理缺陷（激活态）")
    private List<Map<String, Object>> pendingBugs = new ArrayList<>();

    @Schema(description = "待评审用例")
    private List<Map<String, Object>> reviewCases = new ArrayList<>();

    @Schema(description = "未完成测试单")
    private List<Map<String, Object>> unclosedTestTasks = new ArrayList<>();

    @Schema(description = "汇总卡片")
    @Data
    public static class Summary {
        private Integer productCount = 0;
        private Integer bugTotal = 0;
        private Integer bugActive = 0;
        private Integer bugResolved = 0;
        private Integer bugClosed = 0;
        /** 有效缺陷：解决方案为已修复/延期处理/不予解决，或状态为激活 */
        private Integer bugEffective = 0;
        private Integer bugFixed = 0;
        /** 修复率 = 已修复 ÷ 有效缺陷（百分比，保留 2 位） */
        private Double bugFixRate = 0d;
        /** 区间内新增/解决/关闭 */
        private Integer bugOpenedInRange = 0;
        private Integer bugResolvedInRange = 0;
        private Integer bugClosedInRange = 0;
        private Integer caseTotal = 0;
        private Integer caseWait = 0;
        private Integer testTaskTotal = 0;
        private Integer testTaskUnclosed = 0;
    }

    /** 把「name/value」两列的分组结果转成 Map（前端画图直接用） */
    public static Map<String, Integer> toMap(List<Map<String, Object>> rows) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object name = row.get("name");
            Object value = row.get("value");
            if (name != null) {
                map.put(String.valueOf(name), value == null ? 0 : ((Number) value).intValue());
            }
        }
        return map;
    }

}
