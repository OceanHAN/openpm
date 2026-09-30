package cn.iocoder.yudao.module.zentao.enums.kanban;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 卡片状态：只有「进行中」和「已完成」两个值（禅道 {@code zt_kanbancard.status}）
 *
 * <p>完成卡片 = {@code progress=100 + status=done}；激活 = {@code status=doing} 并把进度填回去
 * （进度必须是 0~99，100 已经属于「完成」）。
 */
@Getter
@AllArgsConstructor
public enum KanbanCardStatusEnum {

    DOING("doing", "进行中"),
    DONE("done", "已完成");

    private final String status;

    private final String name;

    public static KanbanCardStatusEnum of(String status) {
        for (KanbanCardStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return item;
            }
        }
        return null;
    }

    public static String nameOf(String status) {
        KanbanCardStatusEnum item = of(status);
        return item != null ? item.name : String.valueOf(status);
    }

}
