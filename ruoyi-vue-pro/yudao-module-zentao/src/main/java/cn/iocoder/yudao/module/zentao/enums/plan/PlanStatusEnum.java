package cn.iocoder.yudao.module.zentao.enums.plan;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 计划状态枚举
 *
 * <p>对齐禅道 {@code $lang->productplan->statusList}：
 * <pre>
 *   wait   未开始
 *   doing  进行中
 *   done   已完成
 *   closed 已关闭
 * </pre>
 *
 * <h3>两个容易做错的点</h3>
 * <ol>
 *   <li><b>「待定」不是状态</b>。禅道用一个日期哨兵 {@code $config->productplan->future = '2030-01-01'}
 *       表示「日期待定」，状态仍然是 wait。所以查询/展示时要把这个日期翻译成「待定」。</li>
 *   <li><b>激活（activate）后是 doing 而不是 wait</b>。禅道 {@code activate()} 调的是
 *       {@code updateStatus($planID, 'doing', 'activated')}，这点和需求/项目的激活不一样。</li>
 * </ol>
 */
@Getter
@AllArgsConstructor
public enum PlanStatusEnum {

    WAIT("wait", "未开始"),
    DOING("doing", "进行中"),
    DONE("done", "已完成"),
    CLOSED("closed", "已关闭");

    private final String status;
    private final String name;

    public static PlanStatusEnum of(String status) {
        for (PlanStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return item;
            }
        }
        return null;
    }

    public static String nameOf(String status) {
        PlanStatusEnum item = of(status);
        return item != null ? item.name : String.valueOf(status);
    }

}
