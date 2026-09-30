package cn.iocoder.yudao.module.zentao.enums.action;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作类型枚举
 *
 * 取值对齐禅道 {@code zt_action.action} 字段的实际取值。
 * 禅道用的是动词过去式（created / edited / changed / closed ...），保持同样风格，
 * 便于日后和禅道的历史日志数据一起展示。
 */
@Getter
@AllArgsConstructor
public enum ActionTypeEnum {

    CREATED("created", "创建"),
    EDITED("edited", "编辑"),
    CHANGED("changed", "变更"),
    CLOSED("closed", "关闭"),
    ACTIVATED("activated", "激活"),
    DELETED("deleted", "删除"),
    /** 回收站：还原（禅道 action/undelete 记的就是这个动作） */
    UNDELETED("undeleted", "还原"),
    /** 回收站：从回收站里隐藏（对象仍然是删除状态，禅道 hideOne 会同时给对象建一条 hidden 动作） */
    HIDDEN("hidden", "隐藏"),
    COMMENTED("commented", "备注"),
    /** 提交评审 */
    SUBMIT_REVIEW("submitReview", "提交评审"),
    /** 评审表决 */
    REVIEWED("reviewed", "评审"),
    /** 评审结果导致的自动状态流转 */
    REVIEW_RESULT("reviewResult", "评审结果流转"),
    /** 记录/修改/删除工时。禅道用同一个动作名，靠 $extra 区分消耗了多少小时 */
    RECORD_WORKHOUR("recordworkhour", "记录工时"),
    /** 待办：完成 */
    FINISHED("finished", "完成"),
    /** 待办：指派给他人 */
    ASSIGNED("assigned", "指派");

    private final String action;
    private final String name;

    public static ActionTypeEnum of(String action) {
        for (ActionTypeEnum item : values()) {
            if (item.action.equals(action)) {
                return item;
            }
        }
        return null;
    }

}
