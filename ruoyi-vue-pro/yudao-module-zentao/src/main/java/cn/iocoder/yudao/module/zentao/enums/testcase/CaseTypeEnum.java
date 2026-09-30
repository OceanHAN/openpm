package cn.iocoder.yudao.module.zentao.enums.testcase;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 用例类型，对齐禅道 {@code $lang->testcase->typeList}
 */
@Getter
@AllArgsConstructor
public enum CaseTypeEnum {

    UNIT("unit", "单元测试"),
    INTERFACE("interface", "接口测试"),
    FEATURE("feature", "功能测试"),
    INSTALL("install", "安装部署"),
    CONFIG("config", "配置相关"),
    PERFORMANCE("performance", "性能测试"),
    SECURITY("security", "安全相关"),
    OTHER("other", "其他");

    private final String type;
    private final String name;

    public static CaseTypeEnum of(String type) {
        return Arrays.stream(values()).filter(item -> item.type.equals(type)).findFirst().orElse(null);
    }

}
