package cn.iocoder.yudao.module.zentao.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Schema(description = "管理后台 - 产品计划 Response VO")
@Data
public class PlanRespVO {

    @Schema(description = "计划编号", example = "1")
    private Long id;

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "产品名称", example = "禅道研发管理平台")
    private String productName;

    @Schema(description = "分支/平台，逗号列表", example = "1,2")
    private String branch;

    @Schema(description = "分支/平台名称，便于列表直接展示", example = "政务云平台,企业版")
    private String branchName;

    @Schema(description = "父计划编号。0 独立 / -1 有子计划", example = "0")
    private Long parent;

    @Schema(description = "计划名称", example = "V1.0 迭代计划")
    private String title;

    @Schema(description = "状态：wait/doing/done/closed", example = "doing")
    private String status;

    @Schema(description = "状态文案", example = "进行中")
    private String statusName;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "开始日期", example = "2026-01-05")
    private LocalDate begin;

    @Schema(description = "结束日期", example = "2026-02-28")
    private LocalDate end;

    @Schema(description = "是否是待定计划（begin/end 为哨兵日期 2030-01-01）", example = "false")
    private Boolean future;

    @Schema(description = "完成时间")
    private LocalDateTime finishedDate;

    @Schema(description = "关闭时间")
    private LocalDateTime closedDate;

    @Schema(description = "关闭原因：done/cancel", example = "")
    private String closedReason;

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

    @Schema(description = "子计划数量", example = "0")
    private Long childCount;

    @Schema(description = "关联需求数量（实时统计）", example = "3")
    private Long storyCount;

    @Schema(description = "关联缺陷数量（实时统计）", example = "1")
    private Long bugCount;

}
