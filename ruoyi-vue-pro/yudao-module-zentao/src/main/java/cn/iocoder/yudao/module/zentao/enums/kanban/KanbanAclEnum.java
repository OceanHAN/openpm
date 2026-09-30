package cn.iocoder.yudao.module.zentao.enums.kanban;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 访问控制（空间的 acl 只有 open/private；看板多一个 extend = 继承空间权限）
 *
 * <pre>
 *   open    公开
 *   private 私有（团队成员 + 白名单 + 负责人）
 *   extend  继承空间访问权限（只有看板用它）
 * </pre>
 */
@Getter
@AllArgsConstructor
public enum KanbanAclEnum {

    OPEN("open", "公开"),
    PRIVATE("private", "私有"),
    EXTEND("extend", "继承空间访问权限");

    private final String acl;

    private final String name;

    public static KanbanAclEnum of(String acl) {
        for (KanbanAclEnum item : values()) {
            if (item.acl.equals(acl)) {
                return item;
            }
        }
        return null;
    }

}
