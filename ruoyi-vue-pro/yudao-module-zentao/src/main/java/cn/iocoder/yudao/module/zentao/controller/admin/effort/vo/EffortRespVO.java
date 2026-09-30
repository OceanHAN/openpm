package cn.iocoder.yudao.module.zentao.controller.admin.effort.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 工时明细 Response VO")
@Data
public class EffortRespVO {

    @Schema(description = "工时编号", example = "96101")
    private Long id;

    @Schema(description = "对象类型", example = "task")
    private String objectType;

    @Schema(description = "任务编号", example = "1")
    private Long objectID;

    @Schema(description = "任务名称", example = "实现登录接口")
    private String taskName;

    @Schema(description = "所属产品", example = "1")
    private String product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "90001")
    private Long execution;

    @Schema(description = "账号", example = "admin")
    private String account;

    @Schema(description = "做了什么", example = "搭好接口骨架")
    private String work;

    @Schema(description = "工作日期", example = "2026-03-02")
    private LocalDate date;

    @Schema(description = "这之后剩余工时", example = "5")
    private BigDecimal left;

    @Schema(description = "本次消耗工时", example = "3")
    private BigDecimal consumed;

    @Schema(description = "开始时间 HHMM", example = "0900")
    private String begin;

    @Schema(description = "结束时间 HHMM", example = "1200")
    private String end;

    @Schema(description = "登记时间")
    private LocalDateTime createTime;

}
