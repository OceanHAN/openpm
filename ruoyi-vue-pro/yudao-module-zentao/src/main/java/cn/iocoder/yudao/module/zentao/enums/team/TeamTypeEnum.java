package cn.iocoder.yudao.module.zentao.enums.team;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 团队成员类型
 *
 * 禅道 {@code zt_team} 一张表存项目与执行的成员，靠 {@code type} 区分：
 * <pre>
 *   type='project'   → root 是项目编号
 *   type='execution' → root 是执行编号
 * </pre>
 */
@Getter
@AllArgsConstructor
public enum TeamTypeEnum {

    PROJECT("project", "项目"),
    EXECUTION("execution", "执行");

    private final String type;
    private final String name;

    public static TeamTypeEnum of(String type) {
        for (TeamTypeEnum item : values()) {
            if (item.type.equals(type)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isValid(String type) {
        return of(type) != null;
    }

}
