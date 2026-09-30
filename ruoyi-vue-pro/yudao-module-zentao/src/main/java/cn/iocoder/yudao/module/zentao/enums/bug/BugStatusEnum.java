package cn.iocoder.yudao.module.zentao.enums.bug;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 缺陷状态枚举
 *
 * 取值对齐禅道 {@code $lang->bug->statusList}。缺陷的生命周期比需求简单：
 * active(激活) → resolved(已解决) → closed(已关闭)，关闭后可以重新激活。
 */
@Getter
@AllArgsConstructor
public enum BugStatusEnum {

    ACTIVE("active", "激活"),
    RESOLVED("resolved", "已解决"),
    CLOSED("closed", "已关闭");

    private final String status;
    private final String name;

    public static BugStatusEnum of(String status) {
        for (BugStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return item;
            }
        }
        return null;
    }

}
