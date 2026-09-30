package cn.iocoder.yudao.module.zentao.enums.plan;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 计划关闭原因枚举
 *
 * <p>对齐禅道 {@code $lang->productplan->closedReasonList}：
 * <pre>
 *   done   已完成
 *   cancel 已取消
 * </pre>
 * 禅道在关闭原因选「已完成」时，还会顺带写 finishedDate（见 {@code buildPlanByStatus()}）。
 */
@Getter
@AllArgsConstructor
public enum PlanClosedReasonEnum {

    DONE("done", "已完成"),
    CANCEL("cancel", "已取消");

    private final String reason;
    private final String name;

    public static PlanClosedReasonEnum of(String reason) {
        for (PlanClosedReasonEnum item : values()) {
            if (item.reason.equals(reason)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isValid(String reason) {
        return of(reason) != null;
    }

}
