package cn.iocoder.yudao.module.zentao.enums.testtask;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 测试单状态，对齐禅道 {@code $lang->testtask->statusList}
 *
 * <p>{@code wait} →（开始）→ {@code doing} →（关闭）→ {@code done}；
 * {@code doing} ⇄ {@code blocked}（阻塞 / 激活）。
 */
@Getter
@AllArgsConstructor
public enum TestTaskStatusEnum {

    WAIT("wait", "未开始"),
    DOING("doing", "进行中"),
    DONE("done", "已关闭"),
    BLOCKED("blocked", "被阻塞");

    private final String status;
    private final String name;

    public static TestTaskStatusEnum of(String status) {
        return Arrays.stream(values()).filter(item -> item.status.equals(status)).findFirst().orElse(null);
    }

}
