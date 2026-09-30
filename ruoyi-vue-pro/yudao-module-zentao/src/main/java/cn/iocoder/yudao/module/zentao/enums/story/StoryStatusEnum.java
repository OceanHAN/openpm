package cn.iocoder.yudao.module.zentao.enums.story;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 需求状态枚举
 *
 * 取值与禅道 {@code module/story/lang/zh-cn.php} 的 {@code $lang->story->statusList} 一一对应，
 * 目的是让 PHP 侧的历史数据可以原样迁移，不需要做值映射。
 */
@Getter
@AllArgsConstructor
public enum StoryStatusEnum {

    DRAFT("draft", "草稿"),
    REVIEWING("reviewing", "评审中"),
    ACTIVE("active", "激活"),
    CHANGING("changing", "变更中"),
    CLOSED("closed", "已关闭");

    /**
     * 状态值，对应禅道数据库中的存储值
     */
    private final String status;

    /**
     * 状态名
     */
    private final String name;

    public static StoryStatusEnum of(String status) {
        for (StoryStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 取状态名，未知状态返回原值，避免日志里出现 null
     */
    public static String nameOf(String status) {
        StoryStatusEnum item = of(status);
        return item != null ? item.name : String.valueOf(status);
    }

}
