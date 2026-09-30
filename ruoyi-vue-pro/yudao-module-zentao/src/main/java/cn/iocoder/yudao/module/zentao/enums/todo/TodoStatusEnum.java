package cn.iocoder.yudao.module.zentao.enums.todo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 待办状态枚举
 *
 * 取值对齐禅道 {@code $lang->todo->statusList}：
 * wait(未开始) → doing(进行中) → done(已完成)，另外可以 closed(已关闭)；
 * 「激活」把状态退回 wait（禅道 module/todo/model.php#activate）。
 */
@Getter
@AllArgsConstructor
public enum TodoStatusEnum {

    WAIT("wait", "未开始"),
    DOING("doing", "进行中"),
    DONE("done", "已完成"),
    CLOSED("closed", "已关闭");

    private final String status;
    private final String name;

    public static TodoStatusEnum of(String status) {
        for (TodoStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return item;
            }
        }
        return null;
    }

}
