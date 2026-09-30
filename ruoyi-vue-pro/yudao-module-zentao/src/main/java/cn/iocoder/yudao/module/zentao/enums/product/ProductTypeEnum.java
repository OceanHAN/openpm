package cn.iocoder.yudao.module.zentao.enums.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 产品类型枚举
 *
 * <p>对齐禅道 {@code zt_product.type}。这个字段决定了「分支」模块叫什么名字：
 * <pre>
 *   $lang->product->branchName['normal']   = '';      // 普通产品，没有分支概念
 *   $lang->product->branchName['branch']   = '分支';  // 多分支产品
 *   $lang->product->branchName['platform'] = '平台';  // 多平台产品
 * </pre>
 * 禅道在 {@code module/branch/model.php} 里到处用
 * {@code str_replace('@branch@', $lang->product->branchName[$productType], ...)}
 * 来切换文案，所以本实现也把「类型 → 文案」这一层抽出来，
 * 避免在 Service 里散落 {@code "分支".equals(type) ? ... : ...} 这种判断。
 */
@Getter
@AllArgsConstructor
public enum ProductTypeEnum {

    /** 普通产品。不启用分支 */
    NORMAL("normal", ""),
    /** 多分支产品 */
    BRANCH("branch", "分支"),
    /** 多平台产品 */
    PLATFORM("platform", "平台");

    private final String type;

    /**
     * 业务文案。normal 为空字符串，与禅道保持一致（调用方用 hasText 判断）
     */
    private final String branchName;

    public static ProductTypeEnum of(String type) {
        for (ProductTypeEnum item : values()) {
            if (item.type.equals(type)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 产品是否启用了分支/平台
     */
    public static boolean supportsBranch(String type) {
        return BRANCH.type.equals(type) || PLATFORM.type.equals(type);
    }

    /**
     * 取分支文案。未知类型或 normal 返回「分支」兜底，避免报错信息里出现空词
     */
    public static String branchNameOf(String type) {
        ProductTypeEnum item = of(type);
        return item != null && !item.branchName.isEmpty() ? item.branchName : "分支";
    }

}
