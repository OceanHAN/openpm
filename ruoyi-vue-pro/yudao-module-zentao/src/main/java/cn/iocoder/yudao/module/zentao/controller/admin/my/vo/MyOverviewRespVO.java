package cn.iocoder.yudao.module.zentao.controller.admin.my.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 「我的地盘」概览
 *
 * 计数口径对齐禅道 {@code module/my/model.php#getOverview}：
 * 任务/需求/缺陷都是**指派给我**的数量（assignedTo = 当前账号）。
 * 待办与工时是这一轮补的（有了 zt_todo 与 zt_effort 才可能算）。
 */
@Schema(description = "管理后台 - 我的地盘概览 Response VO")
@Data
public class MyOverviewRespVO {

    @Schema(description = "账号", example = "admin")
    private String account;

    // ==================== 待办 ====================

    @Schema(description = "今天的待办（未完成）", example = "2")
    private Long todoToday;

    @Schema(description = "未完成的待办总数", example = "4")
    private Long todoUndone;

    @Schema(description = "已过期未完成的待办", example = "1")
    private Long todoOverdue;

    // ==================== 指派给我的 ====================

    @Schema(description = "指派给我的任务总数", example = "6")
    private Long taskTotal;

    @Schema(description = "其中进行中的任务", example = "2")
    private Long taskDoing;

    @Schema(description = "指派给我的未关闭缺陷", example = "1")
    private Long bugActive;

    @Schema(description = "指派给我的激活需求", example = "3")
    private Long storyActive;

    // ==================== 工时 ====================

    @Schema(description = "我本月的消耗工时合计", example = "26.5")
    private BigDecimal effortThisMonth;

}
