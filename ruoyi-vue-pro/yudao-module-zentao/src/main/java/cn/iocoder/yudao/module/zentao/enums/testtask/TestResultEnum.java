package cn.iocoder.yudao.module.zentao.enums.testtask;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 执行结果，对齐禅道 {@code $lang->testcase->resultList}
 *
 * <p>用例级结果由步骤结果**算出来**（见 TestTaskService#aggregateResult）：
 * 默认 {@code pass}，遇到非 pass/n-a 的步骤就以它为准，遇到 {@code fail} 直接结束。
 * 也就是 **fail 优先、其次按出现顺序**，不是「多数派」。
 */
@Getter
@AllArgsConstructor
public enum TestResultEnum {

    PASS("pass", "通过"),
    FAIL("fail", "失败"),
    BLOCKED("blocked", "阻塞"),
    NA("n/a", "忽略");

    private final String result;
    private final String name;

    public static TestResultEnum of(String result) {
        return Arrays.stream(values()).filter(item -> item.result.equals(result)).findFirst().orElse(null);
    }

    /**
     * 这个结果要不要「影响用例级结果」：n/a 和 pass 都不影响。
     * 注意 {@code unexecuted}（未执行）不是结果，是「还没跑」的占位。
     */
    public static boolean isEffective(String result) {
        return result != null && !NA.result.equals(result) && !PASS.result.equals(result);
    }

}
