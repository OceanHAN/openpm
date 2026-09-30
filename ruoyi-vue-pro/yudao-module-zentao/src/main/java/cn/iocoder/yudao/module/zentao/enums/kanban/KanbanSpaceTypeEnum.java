package cn.iocoder.yudao.module.zentao.enums.kanban;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 看板空间类型（禅道 {@code $lang->kanbanspace->typeList}）
 *
 * <pre>
 *   private     私人空间
 *   cooperation 协作空间
 *   public      公共空间
 * </pre>
 */
@Getter
@AllArgsConstructor
public enum KanbanSpaceTypeEnum {

    PRIVATE("private", "私人空间"),
    COOPERATION("cooperation", "协作空间"),
    PUBLIC("public", "公共空间");

    private final String type;

    private final String name;

    public static KanbanSpaceTypeEnum of(String type) {
        for (KanbanSpaceTypeEnum item : values()) {
            if (item.type.equals(type)) {
                return item;
            }
        }
        return null;
    }

    public static String nameOf(String type) {
        KanbanSpaceTypeEnum item = of(type);
        return item != null ? item.name : String.valueOf(type);
    }

}
