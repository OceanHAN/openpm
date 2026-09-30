package cn.iocoder.yudao.module.zentao.enums.task;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态枚举
 *
 * 取值对齐禅道 {@code $lang->task->statusList}。
 */
@Getter
@AllArgsConstructor
public enum TaskStatusEnum {

    WAIT("wait", "未开始"),
    DOING("doing", "进行中"),
    DONE("done", "已完成"),
    PAUSE("pause", "已暂停"),
    CANCEL("cancel", "已取消"),
    CLOSED("closed", "已关闭");

    private final String status;
    private final String name;

    public static TaskStatusEnum of(String status) {
        for (TaskStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return item;
            }
        }
        return null;
    }

}
