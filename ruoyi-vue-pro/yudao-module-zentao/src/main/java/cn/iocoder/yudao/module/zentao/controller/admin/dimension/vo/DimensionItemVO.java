package cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 1.5 级导航下拉项。
 *
 * <p>字段名（{@code id / text / keys}）是禅道 {@code dimension::ajaxGetDropMenu()} 组装的
 * {@code $items[]} 的**原样结构**（{@code control.php:26-36}），前端 zui 的 dropmenu 直接吃这三个键，
 * 所以这里不做二次命名（对比 {@code company} 的外部公司下拉是 {@code text/value/keys}，
 * 两个禅道方法的键名不同，不要互相抄）。
 */
@Schema(description = "管理后台 - 维度下拉项（禅道 items 结构）")
@Data
public class DimensionItemVO {

    @Schema(description = "维度编号", example = "1")
    private Long id;

    @Schema(description = "显示文本（维度名）", example = "宏观管理维度")
    private String text;

    /** 禅道 {@code zget(common::convert2Pinyin([name]), name, '')}：查不到拼音的字原样保留 */
    @Schema(description = "搜索键（拼音首字母；码表缺字时退化成原文）", example = "hgGLwd")
    private String keys;

}
