package cn.iocoder.yudao.module.zentao.enums.release;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发布状态枚举
 *
 * <p>对齐禅道 {@code $lang->release->statusList}：
 * <pre>
 *   wait      未开始
 *   normal    已发布
 *   fail      发布失败
 *   terminate 停止维护
 * </pre>
 *
 * <p>注意必填字段是**跟着状态变**的（禅道 {@code create()} 里动态改 requiredFields）：
 * <ul>
 *   <li>{@code wait}：不需要「实际发布日期」</li>
 *   <li>{@code normal}：不需要「计划发布日期」</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum ReleaseStatusEnum {

    WAIT("wait", "未开始"),
    NORMAL("normal", "已发布"),
    FAIL("fail", "发布失败"),
    TERMINATE("terminate", "停止维护");

    private final String status;
    private final String name;

    public static ReleaseStatusEnum of(String status) {
        for (ReleaseStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isValid(String status) {
        return of(status) != null;
    }

    public static String nameOf(String status) {
        ReleaseStatusEnum item = of(status);
        return item != null ? item.name : String.valueOf(status);
    }

}
