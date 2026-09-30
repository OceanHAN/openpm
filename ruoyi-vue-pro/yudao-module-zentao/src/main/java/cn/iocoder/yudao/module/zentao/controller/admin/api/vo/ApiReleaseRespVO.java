package cn.iocoder.yudao.module.zentao.controller.admin.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布版本（快照）。
 *
 * <p>{@code snapApis} / {@code snapStructs} 是从 {@code snap} JSON 里解出来的
 * {@code {id, version}} 列表 —— 发布冻结的就是这两个数，内容仍在 spec 表里，
 * 所以这里给的是「当时冻结了哪些接口/结构的第几版」，不是内容副本。
 */
@Schema(description = "管理后台 - 接口库发布版本 Response VO")
@Data
public class ApiReleaseRespVO {

    @Schema(description = "发布编号", example = "92761")
    private Long id;

    @Schema(description = "所属接口库", example = "92751")
    private Long lib;

    @Schema(description = "版本说明", example = "首个对外版本")
    private String desc;

    @Schema(description = "版本号（字符串）", example = "v1.0")
    private String version;

    @Schema(description = "发布人", example = "admin")
    private String addedBy;

    @Schema(description = "发布时间", example = "2026-04-11 09:00:00")
    private LocalDateTime addedDate;

    @Schema(description = "冻结的目录数", example = "3")
    private Integer moduleCount;

    @Schema(description = "冻结的接口数", example = "2")
    private Integer apiCount;

    @Schema(description = "冻结的结构数", example = "2")
    private Integer structCount;

    @Schema(description = "冻结的接口清单：{id, version}")
    private List<SnapItemVO> snapApis;

    @Schema(description = "冻结的结构清单：{id, version}")
    private List<SnapItemVO> snapStructs;

    @Schema(description = "原始快照 JSON（原样返回，便于排查）")
    private String snap;

    /** 快照里的一格：只有编号 + 版本号 */
    @Schema(description = "快照条目")
    @Data
    public static class SnapItemVO {

        @Schema(description = "接口/结构编号", example = "92711")
        private Long id;

        @Schema(description = "冻结时的版本号", example = "2")
        private Integer version;

    }

}
