package cn.iocoder.yudao.module.zentao.enums.stakeholder;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 干系人类型：内部 / 外部
 *
 * 取值对齐禅道 {@code $lang->stakeholder->typeList}。
 * 注意：它**不是独立选的**，而是由来源（from）推导出来的 ——
 * {@code from='outside' → outside}，其余 → {@code inside}
 * （禅道 module/stakeholder/model.php#create 就是这么写的）。
 */
@Getter
@AllArgsConstructor
public enum StakeholderTypeEnum {

    INSIDE("inside", "内部"),
    OUTSIDE("outside", "外部");

    private final String type;
    private final String name;

    public static StakeholderTypeEnum of(String type) {
        for (StakeholderTypeEnum item : values()) {
            if (item.type.equals(type)) {
                return item;
            }
        }
        return null;
    }

}
