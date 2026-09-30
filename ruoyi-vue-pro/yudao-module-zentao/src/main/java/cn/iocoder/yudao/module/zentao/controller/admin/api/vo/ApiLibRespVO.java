package cn.iocoder.yudao.module.zentao.controller.admin.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口库（禅道里就是 {@code zt_doclib} 中 {@code type='api'} 的记录）。
 *
 * <p><b>没有独立的库表</b>：禅道从来没有 {@code zt_apilib}，
 * 建库走 {@code doc::createApiLib()}（{@code module/api/model.php:636} 往 TABLE_DOCLIB 插 type='api'）。
 * 所以本 VO 的字段名与 {@code zt_doclib} 一致，前端拿到的就是「文档库里的接口库」。
 *
 * <p>{@code apiCount} / {@code structCount} 是给「按库筛选」的下拉补的计数
 * （禅道在空间首页用 {@code ajaxGetHome} 算同样的数）。
 */
@Schema(description = "管理后台 - 接口库 Response VO")
@Data
public class ApiLibRespVO {

    @Schema(description = "库编号（zt_doclib.id）", example = "92751")
    private Long id;

    @Schema(description = "库类型，恒为 api", example = "api")
    private String type;

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属项目", example = "0")
    private Long project;

    @Schema(description = "所属执行", example = "0")
    private Long execution;

    @Schema(description = "库名称", example = "禅道接口库")
    private String name;

    @Schema(description = "接口基础路径（禅道 zt_doclib.baseUrl）", example = "https://demo.zentao.net/api.php/v1")
    private String baseUrl;

    @Schema(description = "访问控制：open 公开 / private 私有", example = "open")
    private String acl;

    @Schema(description = "库描述")
    private String desc;

    @Schema(description = "排序", example = "7")
    private Integer order;

    @Schema(description = "库里的接口数", example = "2")
    private Integer apiCount;

    @Schema(description = "库里的结构数", example = "2")
    private Integer structCount;

    @Schema(description = "创建人", example = "admin")
    private String addedBy;

    @Schema(description = "创建时间", example = "2026-04-01 09:00:00")
    private LocalDateTime addedDate;

}
