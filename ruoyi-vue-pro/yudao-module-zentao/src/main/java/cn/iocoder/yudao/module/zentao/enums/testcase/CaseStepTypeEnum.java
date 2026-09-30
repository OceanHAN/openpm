package cn.iocoder.yudao.module.zentao.enums.testcase;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 步骤类型，对齐禅道 {@code zt_casestep.type}
 *
 * <p>{@code group} 是「步骤组」：只有描述、没有预期结果（禅道 insertSteps 里
 * {@code $step->expect = $stepType == 'group' ? '' : ...}）。它存在的意义是
 * 让步骤能分层次展示，前端算出的编号形如 1. / 1.1 / 1.1.1。
 */
@Getter
@AllArgsConstructor
public enum CaseStepTypeEnum {

    STEP("step", "步骤"),
    GROUP("group", "步骤组");

    private final String type;
    private final String name;

}
