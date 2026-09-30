package cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 当前维度（禅道 {@code dimension::getDimension(dimensionID)}）。
 *
 * <p>禅道这个方法只返回一个 int，但它内部走的 {@code saveState()} 有**四级兜底链**
 * （{@code model.php:83-117}）：
 * <pre>
 *   ① config->dimensions->lastDimension   （配置；本项目 = zentao.dimension.last-dimension）
 *   ② session->dimension                  （会话；本项目 = Redis 的用户级末次访问记录）
 *   ③ 可见性校验：不在可见集合里 → 取可见的第一条
 *   ④ getFirst()                          （可见的第一条）
 * </pre>
 * 本实现把 {@code dimensionID} 之外的「最终命中哪一级（{@code source}）」与解析出来的维度一起返回 ——
 * 禅道这个链路的中间状态在页面上不可见，出了问题只能读代码；单列一个 {@code source}
 * 让前端能显示「当前维度来自哪里」，测试也能把四级链路逐段钉住。这是**有意偏离**（见 README）。
 */
@Schema(description = "管理后台 - 当前维度（含四级兜底链的来源）")
@Data
public class DimensionCurrentRespVO {

    @Schema(description = "最终生效的维度编号（禅道 getDimension 的返回值）", example = "1")
    private Long dimensionID;

    @Schema(description = "标签页（禅道 session 按 app->tab 分桶，本实现默认 bi）", example = "bi")
    private String tab;

    @Schema(description = "命中来源：explicit 入参 / config 配置 / last 末次访问 / fallback 可见性兜底 / "
            + "first 第一条 / none 一个维度都没有", example = "last")
    private String source;

    @Schema(description = "来源的中文说明（给页面直接显示）",
            example = "来自末次访问记录（禅道 session->dimension）")
    private String sourceDesc;

    @Schema(description = "解析出来的维度（dimensionID=0 时为 null）")
    private DimensionRespVO dimension;

    @Schema(description = "当前账号可见的维度编号（禅道 biModel::getViewableObject('dimension')）",
            example = "[1,2,3]")
    private List<Long> viewableIds;

}
