package cn.iocoder.yudao.module.zentao.enums.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 请求参数的位置（scope）。
 *
 * <p>照抄禅道 {@code apiModel::SCOPE_*}（{@code model.php:21-26}）与
 * {$lang->api->paramsScopeOptions}（{@code module/api/lang/zh-cn.php:203}）：
 * <pre>
 *   formData / path / query / body / header / cookie
 * </pre>
 *
 * <h3>其中只有三个会落进 params JSON，另外三个是「表单位置」而不是「JSON 键」</h3>
 * 禅道 {@code params} 列的形状是固定的四个键
 * （{@code ui/create.html.php:346 formHidden('params', '{"header":[],"params":[],"paramsType":"formData","query":[]}')}）：
 * <pre>
 *   header     → 请求头字段行
 *   query      → 请求参数（URL query）字段行
 *   params     → 请求体字段树（无限级，见 common.ui.js buildNestedParams）
 *   paramsType → 请求体的类型：formData/json/array/object
 * </pre>
 * 所以本枚举多带一个 {@link #jsonKey}：只有 {@code header/query/params} 是 JSON 的键，
 * {@code path/body/cookie/formData} 是「这些字段在文档里的位置说明」，
 * 在 params JSON 里没有独立容器（这是禅道的实际形状，别按枚举名硬造四个键）。
 */
@Getter
@AllArgsConstructor
public enum ApiParamsScopeEnum {

    FORM_DATA("formData", "params", "表单/请求体（paramsType 的默认值）"),
    PATH("path", null, "路径参数（禅道文档里只用文字说明，没有独立容器）"),
    QUERY("query", "query", "URL 查询参数"),
    BODY("body", "params", "请求体"),
    HEADER("header", "header", "请求头"),
    COOKIE("cookie", null, "Cookie（禅道文档里只用文字说明）");

    /** 禅道的 scope 值 */
    private final String scope;

    /** 在 params JSON 里对应的键；为 null 表示它没有独立容器 */
    private final String jsonKey;

    /** 说明 */
    private final String desc;

}
