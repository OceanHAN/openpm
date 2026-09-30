package cn.iocoder.yudao.module.zentao.enums.bug;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 缺陷类型枚举
 *
 * 取值对齐禅道 {@code $lang->bug->typeList}。
 */
@Getter
@AllArgsConstructor
public enum BugTypeEnum {

    CODE_ERROR("codeerror", "代码错误"),
    CONFIG("config", "配置相关"),
    INSTALL("install", "安装部署"),
    SECURITY("security", "安全相关"),
    PERFORMANCE("performance", "性能问题"),
    STANDARD("standard", "标准规范"),
    AUTOMATION("automation", "测试脚本"),
    DESIGN_DEFECT("designdefect", "设计缺陷"),
    CODE_IMPROVEMENT("codeimprovement", "代码改进"),
    OTHERS("others", "其他");

    private final String type;
    private final String name;

}
