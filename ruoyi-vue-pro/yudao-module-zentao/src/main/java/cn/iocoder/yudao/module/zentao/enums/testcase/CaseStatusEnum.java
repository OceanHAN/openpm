package cn.iocoder.yudao.module.zentao.enums.testcase;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 用例状态，对齐禅道 {@code $lang->testcase->statusList}
 *
 * <p>{@code wait}（待评审）是**流程状态**：新建时如果开了评审就是 wait；
 * 更关键的是**步骤一旦变化，状态会被打回 wait** —— 因为步骤是用例的核心，
 * 改了步骤等于换了一条用例，必须重新评审。
 */
@Getter
@AllArgsConstructor
public enum CaseStatusEnum {

    WAIT("wait", "待评审"),
    NORMAL("normal", "正常"),
    BLOCKED("blocked", "被阻塞"),
    INVESTIGATE("investigate", "研究中");

    private final String status;
    private final String name;

    public static CaseStatusEnum of(String status) {
        return Arrays.stream(values()).filter(item -> item.status.equals(status)).findFirst().orElse(null);
    }

}
