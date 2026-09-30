package cn.iocoder.yudao.module.zentao.enums.doc;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 文档库类型
 *
 * <p>禅道 {@code $lang->doc->libTypeList}：product / project / execution / api / custom。
 * 本实现不含 api 接口库（依赖 api 模块，未迁移），其余四种照搬。
 *
 * <p>{@code product/project/execution} 三种库是**跟着对象走的主库**（{@code main=1}），
 * 建库时把对象编号抄进对应的列；{@code custom} 是团队空间下的自定义库。
 */
@Getter
@AllArgsConstructor
public enum DocLibTypeEnum {

    PRODUCT("product", "产品文档库"),
    PROJECT("project", "项目文档库"),
    EXECUTION("execution", "执行文档库"),
    CUSTOM("custom", "自定义文档库");

    private final String type;
    private final String name;

    /**
     * 是否「跟着对象走」的主库类型
     */
    public static boolean isObjectLib(String type) {
        return PRODUCT.type.equals(type) || PROJECT.type.equals(type) || EXECUTION.type.equals(type);
    }

    public static DocLibTypeEnum of(String type) {
        return Arrays.stream(values()).filter(item -> item.type.equals(type)).findFirst().orElse(null);
    }

}
