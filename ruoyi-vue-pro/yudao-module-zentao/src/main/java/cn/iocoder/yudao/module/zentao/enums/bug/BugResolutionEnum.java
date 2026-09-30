package cn.iocoder.yudao.module.zentao.enums.bug;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 缺陷解决方案枚举
 *
 * 取值对齐禅道 {@code $lang->bug->resolutionList}。
 * 禅道对其中两个取值有强制联动校验：
 * <ul>
 *   <li>{@code duplicate} —— 必须指定 {@code duplicateBug}，且该缺陷必须存在</li>
 *   <li>{@code fixed} —— 必须指定 {@code resolvedBuild}</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum BugResolutionEnum {

    BY_DESIGN("bydesign", "设计如此"),
    DUPLICATE("duplicate", "重复Bug"),
    EXTERNAL("external", "外部原因"),
    FIXED("fixed", "已解决"),
    NOT_REPRO("notrepro", "无法重现"),
    POSTPONED("postponed", "延期处理"),
    WILL_NOT_FIX("willnotfix", "不予解决"),
    TO_STORY("tostory", "转为需求");

    private final String resolution;
    private final String name;

    public static BugResolutionEnum of(String resolution) {
        for (BugResolutionEnum item : values()) {
            if (item.resolution.equals(resolution)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isValid(String resolution) {
        return of(resolution) != null;
    }

}
