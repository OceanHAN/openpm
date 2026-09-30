package cn.iocoder.yudao.module.zentao.enums.story;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 需求评审结果枚举
 *
 * 取值对齐禅道 {@code $lang->story->reviewResultList}。
 *
 * 注意：这里既用于「单个评审人的表决结果」，也用于「多人表决后的聚合结果」。
 * 区别在于聚合结果可能为空串（表示还有人没评完）。
 */
@Getter
@AllArgsConstructor
public enum StoryReviewResultEnum {

    /**
     * 确认通过
     */
    PASS("pass", "确认通过"),
    /**
     * 有待明确
     */
    CLARIFY("clarify", "有待明确"),
    /**
     * 撤销变更。会触发版本回滚：version-1，并删除当前版本的快照与评审记录。
     */
    REVERT("revert", "撤销变更"),
    /**
     * 拒绝。需求会被直接关闭。
     */
    REJECT("reject", "拒绝");

    private final String result;
    private final String name;

    public static StoryReviewResultEnum of(String result) {
        for (StoryReviewResultEnum item : values()) {
            if (item.result.equals(result)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isValid(String result) {
        return of(result) != null;
    }

}
