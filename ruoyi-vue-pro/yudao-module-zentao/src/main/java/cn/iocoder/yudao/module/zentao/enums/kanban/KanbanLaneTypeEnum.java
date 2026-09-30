package cn.iocoder.yudao.module.zentao.enums.kanban;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 泳道类型
 *
 * <p>{@code common} 是普通泳道（看板自建卡片都放这种泳道）；
 * story/bug/task/parentStory 是「按对象分泳道」，研发看板按对象类型把需求/缺陷/任务
 * 分到不同泳道里（{@code zt_kanbanlane.type}）。
 */
@Getter
@AllArgsConstructor
public enum KanbanLaneTypeEnum {

    COMMON("common", "普通泳道"),
    PARENT_STORY("parentStory", "父研发需求"),
    STORY("story", "研发需求"),
    BUG("bug", "Bug"),
    TASK("task", "任务");

    private final String type;

    private final String name;

    public static KanbanLaneTypeEnum of(String type) {
        for (KanbanLaneTypeEnum item : values()) {
            if (item.type.equals(type)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isValid(String type) {
        return of(type) != null;
    }

    public static String nameOf(String type) {
        KanbanLaneTypeEnum item = of(type);
        return item != null ? item.name : String.valueOf(type);
    }

}
