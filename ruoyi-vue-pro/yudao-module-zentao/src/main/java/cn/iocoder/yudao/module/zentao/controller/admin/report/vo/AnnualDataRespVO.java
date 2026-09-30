package cn.iocoder.yudao.module.zentao.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 年度数据（禅道 {@code report::annualData}）。
 *
 * <p>三种视角：{@code company}（全公司，不传 account/dept）、{@code dept}（某部门）、
 * {@code user}（某个人）。视角决定 {@code accounts} 过滤条件，所有指标都跟着变。
 */
@Schema(description = "管理后台 - 年度数据 Response VO")
@Data
public class AnnualDataRespVO {

    @Schema(description = "视角：company/dept/user", example = "company")
    private String mode;

    @Schema(description = "年份", example = "2026")
    private String year;

    @Schema(description = "统计对象的名称（部门名或人名，公司视角为空）", example = "admin")
    private String who;

    @Schema(description = "参与人数（仅公司视角）", example = "12")
    private Integer users;

    @Schema(description = "登录次数（来自 system_login_log，仅个人视角）", example = "231")
    private Long logins;

    @Schema(description = "动作数（zt_action 条数）", example = "18234")
    private Long actions;

    @Schema(description = "待办统计")
    private TodoStat todos = new TodoStat();

    @Schema(description = "本年消耗工时合计", example = "128.50")
    private BigDecimal consumed = BigDecimal.ZERO;

    @Schema(description = "贡献数（按禅道 contributionCount 的口径统计的动作数）", example = "456")
    private Integer contributionCount;

    @Schema(description = "本年度贡献里单项最大条数（雷达图按它做归一化）", example = "88")
    private Integer maxCount;

    @Schema(description = "12 个月（YYYY-MM）", example = "[\"2026-01\"]")
    private List<String> months = new ArrayList<>();

    @Schema(description = "贡献：对象类型 -> 贡献名 -> 条数")
    private Map<String, Map<String, Integer>> contributions = new LinkedHashMap<>();

    @Schema(description = "各年的雷达数据：年份 -> {product, execution, devel, qa, other}")
    private Map<String, Map<String, Integer>> contributionGroups = new LinkedHashMap<>();

    @Schema(description = "本年雷达数据")
    private Map<String, Integer> radarData = new LinkedHashMap<>();

    @Schema(description = "本年有动静的产品（含计划/需求/关闭数）")
    private List<Map<String, Object>> productStat = new ArrayList<>();

    @Schema(description = "本年有动静的执行（含完成任务/需求/解决缺陷数）")
    private List<Map<String, Object>> executionStat = new ArrayList<>();

    @Schema(description = "需求：状态分布 + 月度动作")
    private ObjectStat storyStat = new ObjectStat();

    @Schema(description = "任务：状态分布 + 月度动作")
    private ObjectStat taskStat = new ObjectStat();

    @Schema(description = "缺陷：状态分布 + 月度动作")
    private ObjectStat bugStat = new ObjectStat();

    @Schema(description = "用例：执行结果分布 + 月度动作")
    private CaseStat caseStat = new CaseStat();

    @Schema(description = "全量状态分布（仅公司视角）：story/task/bug -> status -> 条数")
    private Map<String, Map<String, Integer>> statusStat = new LinkedHashMap<>();

    @Schema(description = "每类对象的「总数 / 未完成」一句话概述")
    private Map<String, String> overview = new LinkedHashMap<>();

    @Schema(description = "待办统计")
    @Data
    public static class TodoStat {
        private Integer count = 0;
        private Integer undone = 0;
        private Integer done = 0;
    }

    @Schema(description = "对象统计（状态分布 + 月度动作）")
    @Data
    public static class ObjectStat {
        @Schema(description = "状态 -> 对象数（本年被创建过的对象，按当前状态统计）")
        private Map<String, Integer> statusStat = new LinkedHashMap<>();

        @Schema(description = "动作 -> 月份 -> 条数")
        private Map<String, Map<String, Integer>> actionStat = new LinkedHashMap<>();
    }

    @Schema(description = "用例统计")
    @Data
    public static class CaseStat {
        @Schema(description = "执行结果 -> 条数")
        private Map<String, Integer> resultStat = new LinkedHashMap<>();

        @Schema(description = "动作 -> 月份 -> 条数")
        private Map<String, Map<String, Integer>> actionStat = new LinkedHashMap<>();
    }

}
