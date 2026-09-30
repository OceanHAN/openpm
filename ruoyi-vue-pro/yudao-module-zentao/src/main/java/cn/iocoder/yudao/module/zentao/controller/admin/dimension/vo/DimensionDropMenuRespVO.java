package cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 1.5 级导航下拉（禅道 {@code dimension::ajaxGetDropMenu} + {@code ui/ajaxgetdropmenu.html.php}）。
 *
 * <p>{@code data/searchHint/link/labelMap/expandName/itemType} 六个键是禅道渲染的 JSON 原样
 * （见 {@code module/dimension/ui/ajaxgetdropmenu.html.php:18-24}），其中：
 * <pre>
 *   data       = [{id, text, keys(拼音)}]
 *   searchHint = $lang->searchAB = '搜索'
 *   link       = {"dimension": "&lt;切换维度的链接&gt;"}
 *   labelMap   = {"dimension": $lang->dimension->common = '维度'}
 *   expandName = "closed"
 *   itemType   = "dimension"
 * </pre>
 *
 * <p>另外三个键（{@code module/method/params/dimensionID}）是**本实现附加**的：禅道的链接形状是
 * {@code createLink} 生成的 PHP 路由（{@code index.php?m=x&f=y&...}），本实现返回
 * {@code /{module}/{method}?{params}}，把这三段单列出来，前端可以直接拼、断言也可以直接对。
 */
@Schema(description = "管理后台 - 维度 1.5 级导航下拉")
@Data
public class DimensionDropMenuRespVO {

    @Schema(description = "下拉项（禅道 items 原样：id/text/keys）")
    private List<DimensionItemVO> data;

    @Schema(description = "搜索框提示（禅道 $lang->searchAB = 「搜索」）", example = "搜索")
    private String searchHint;

    @Schema(description = "切换维度的链接模板（含 {id} 占位），禅道 link.dimension 原样", example = "/pivot/browse?dimensionID={id}")
    private Map<String, String> link;

    @Schema(description = "标签映射，禅道 labelMap.dimension = 「维度」")
    private Map<String, String> labelMap;

    @Schema(description = "禅道 expandName 原样", example = "closed")
    private String expandName;

    @Schema(description = "禅道 itemType 原样", example = "dimension")
    private String itemType;

    // ==================== 以下为本实现附加，便于前端拼链接与断言 ====================

    @Schema(description = "当前维度编号（原样回显入参）", example = "1")
    private Long dimensionID;

    @Schema(description = "目标模块（原样回显；两条例外都只改写 method 与 params，不改模块）", example = "pivot")
    private String module;

    @Schema(description = "改写后的目标方法（module=pivot 且 method=design → browse）", example = "browse")
    private String method;

    @Schema(description = "链接参数模板（tree+browsegroup 会追加 groupID=0&type={viewType}）",
            example = "dimensionID={id}")
    private String params;

}
