package cn.iocoder.yudao.module.zentao.enums.testcase;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 用例适用的测试环节，对齐禅道 {@code $lang->testcase->stageList}
 *
 * <p>注意 {@code zt_case.stage} 是**逗号列表**（一条用例可以同时属于多个环节），
 * 所以过滤时要 {@code FIND_IN_SET}，展示时要逐段翻译。
 */
@Getter
@AllArgsConstructor
public enum CaseStageEnum {

    UNIT("unittest", "单元测试环节"),
    FEATURE("feature", "功能测试环节"),
    INTEGRATE("intergrate", "集成测试环节"),
    SYSTEM("system", "系统测试环节"),
    SMOKE("smoke", "冒烟测试环节"),
    BVT("bvt", "版本验证环节");

    private final String stage;
    private final String name;

    public static CaseStageEnum of(String stage) {
        return Arrays.stream(values()).filter(item -> item.stage.equals(stage)).findFirst().orElse(null);
    }

}
