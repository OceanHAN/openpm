package cn.iocoder.yudao.module.zentao.enums.stage;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 阶段类型枚举
 *
 * <p>对齐禅道 {@code $lang->stage->typeList}：
 * <pre>
 *   mix     综合
 *   request 需求
 *   design  设计
 *   dev     开发
 *   qa      测试
 *   release 发布
 *   review  总结评审
 *   other   其他
 * </pre>
 * 阶段模板靠这个字段决定图标与默认行为；数量与占比无关（占比在 {@code percent} 里单列）。
 */
@Getter
@AllArgsConstructor
public enum StageTypeEnum {

    MIX("mix", "综合"),
    REQUEST("request", "需求"),
    DESIGN("design", "设计"),
    DEV("dev", "开发"),
    QA("qa", "测试"),
    RELEASE("release", "发布"),
    REVIEW("review", "总结评审"),
    OTHER("other", "其他");

    private final String type;
    private final String name;

    public static StageTypeEnum of(String type) {
        for (StageTypeEnum item : values()) {
            if (item.type.equals(type)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isValid(String type) {
        return of(type) != null;
    }

    public static String nameOf(String type) {
        StageTypeEnum item = of(type);
        return item != null ? item.name : String.valueOf(type);
    }

}
