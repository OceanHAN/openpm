package cn.iocoder.yudao.module.zentao.enums.project;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 项目状态枚举
 *
 * 取值对齐禅道 {@code $lang->project->statusList}。
 *
 * <pre>
 *   wait(未开始) ──start──> doing(进行中) ──close──> closed(已关闭)
 *                              ↑   │
 *                        activate  suspend
 *                              │   ↓
 *                          suspended(已挂起)
 *   doing ──(超过 end 日期)──> delay(已延期)
 * </pre>
 */
@Getter
@AllArgsConstructor
public enum ProjectStatusEnum {

    WAIT("wait", "未开始"),
    DOING("doing", "进行中"),
    SUSPENDED("suspended", "已挂起"),
    CLOSED("closed", "已关闭"),
    DELAY("delay", "已延期");

    private final String status;
    private final String name;

    /**
     * 取状态名，未知状态返回原值，避免接口里出现 null
     */
    public static String nameOf(String status) {
        ProjectStatusEnum item = of(status);
        return item != null ? item.name : String.valueOf(status);
    }

    public static ProjectStatusEnum of(String status) {
        for (ProjectStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return item;
            }
        }
        return null;
    }

}
