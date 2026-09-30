package cn.iocoder.yudao.module.zentao.controller.admin.stage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Schema(description = "管理后台 - 阶段模板 / 项目阶段 Response VO")
@Data
public class StageRespVO {

    @Schema(description = "编号（模板编号，或项目阶段的项目编号）", example = "1")
    private Long id;

    @Schema(description = "所属流程模板组", example = "1")
    private Long workflowGroup;

    @Schema(description = "阶段名称", example = "开发")
    private String name;

    @Schema(description = "工作量占比", example = "30")
    private String percent;

    @Schema(description = "阶段类型：request/design/dev/qa/release/review/other", example = "dev")
    private String type;

    @Schema(description = "阶段类型文案", example = "开发")
    private String typeName;

    @Schema(description = "适用的项目流程类型", example = "waterfall")
    private String projectType;

    @Schema(description = "排序", example = "3")
    private Integer order;

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

    // ==================== 项目阶段（实例）专有字段 ====================

    @Schema(description = "所属项目（项目阶段专有）", example = "1")
    private Long project;

    @Schema(description = "所属项目名称", example = "禅道迁移一期")
    private String projectName;

    @Schema(description = "状态：wait/doing/suspended/closed（项目阶段专有）", example = "doing")
    private String status;

    @Schema(description = "状态文案", example = "进行中")
    private String statusName;

    @Schema(description = "计划开始（项目阶段专有）", example = "2026-02-06")
    private LocalDate begin;

    @Schema(description = "计划结束", example = "2026-03-10")
    private LocalDate end;

    @Schema(description = "实际开始")
    private LocalDate realBegan;

    @Schema(description = "实际结束")
    private LocalDate realEnd;

    @Schema(description = "预计工时", example = "30.00")
    private BigDecimal estimate;

    @Schema(description = "已消耗工时", example = "10.00")
    private BigDecimal consumed;

    @Schema(description = "剩余工时", example = "20.00")
    private BigDecimal left;

    @Schema(description = "进度百分比", example = "33.00")
    private BigDecimal progress;

}
