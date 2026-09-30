package cn.iocoder.yudao.module.zentao.enums.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 接口开发状态。
 *
 * <p>照抄禅道 {@code apiModel::STATUS_DOING/STATUS_DONE/STATUS_HIDDEN}（{@code model.php:16-18}）
 * 与 {$lang->api->statusOptions}（{@code module/api/lang/zh-cn.php:196}）：
 * <pre>
 *   done   开发完成
 *   doing  开发中
 *   hidden 不显示
 * </pre>
 *
 * <p>还有一条禅道行为要照抄：{@code getApiStatusText()}（{@code model.php:447}）
 * 只翻译 doing/done 两个值，**其它值原样返回**（{@code hidden} 就走这个分支，
 * 顺手也把脏数据兜住了）。所以这里的 {@link #textOf(String)} 也保留 fallback。
 */
@Getter
@AllArgsConstructor
public enum ApiStatusEnum {

    DONE("done", "开发完成"),
    DOING("doing", "开发中"),
    HIDDEN("hidden", "不显示");

    /** 落库值 */
    private final String status;

    /** 展示文案 */
    private final String name;

    public static ApiStatusEnum of(String status) {
        return Arrays.stream(values()).filter(item -> item.status.equals(status)).findFirst().orElse(null);
    }

    /**
     * 状态文案：认识的原样翻译，不认识的**原样返回**（禅道 getApiStatusText 的行为）。
     */
    public static String textOf(String status) {
        ApiStatusEnum item = of(status);
        return item == null ? status : item.name;
    }

}
