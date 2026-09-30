package cn.iocoder.yudao.module.zentao.enums.stakeholder;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 干系人来源
 *
 * 取值对齐禅道 {@code $lang->stakeholder->fromList}：
 * <pre>
 *   team     项目/项目集团队成员
 *   company  公司同事
 *   outside  外部人员
 * </pre>
 */
@Getter
@AllArgsConstructor
public enum StakeholderFromEnum {

    TEAM("team", "团队成员"),
    COMPANY("company", "公司同事"),
    OUTSIDE("outside", "外部人员");

    private final String from;
    private final String name;

    public static StakeholderFromEnum of(String from) {
        for (StakeholderFromEnum item : values()) {
            if (item.from.equals(from)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isValid(String from) {
        return of(from) != null;
    }

}
