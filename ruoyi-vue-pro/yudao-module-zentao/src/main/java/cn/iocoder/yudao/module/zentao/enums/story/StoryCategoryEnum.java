package cn.iocoder.yudao.module.zentao.enums.story;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 需求分类枚举
 *
 * 取值对齐禅道 {@code $lang->story->categoryList}。
 */
@Getter
@AllArgsConstructor
public enum StoryCategoryEnum {

    FEATURE("feature", "功能"),
    INTERFACE("interface", "接口"),
    PERFORMANCE("performance", "性能"),
    SAFE("safe", "安全"),
    EXPERIENCE("experience", "体验"),
    IMPROVE("improve", "改进"),
    OTHER("other", "其他");

    private final String category;
    private final String name;

}
