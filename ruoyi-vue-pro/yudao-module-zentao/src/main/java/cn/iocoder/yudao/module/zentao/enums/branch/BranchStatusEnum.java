package cn.iocoder.yudao.module.zentao.enums.branch;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 分支状态枚举
 *
 * <p>对齐禅道 {@code $lang->branch->statusList}：
 * <pre>
 *   $lang->branch->statusList['active'] = '激活';
 *   $lang->branch->statusList['closed'] = '已关闭';
 * </pre>
 * 注意禅道**没有**删除状态 —— 删除就是物理/逻辑删除，
 * 关闭（closed）和删除是两件事：关闭的分支仍然可以被需求引用，只是不再出现在默认筛选里。
 */
@Getter
@AllArgsConstructor
public enum BranchStatusEnum {

    ACTIVE("active", "激活"),
    CLOSED("closed", "已关闭");

    private final String status;
    private final String name;

    public static BranchStatusEnum of(String status) {
        for (BranchStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return item;
            }
        }
        return null;
    }

}
