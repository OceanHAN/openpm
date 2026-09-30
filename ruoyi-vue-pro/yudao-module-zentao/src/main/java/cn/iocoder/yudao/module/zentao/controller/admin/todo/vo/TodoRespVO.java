package cn.iocoder.yudao.module.zentao.controller.admin.todo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 待办 Response VO")
@Data
public class TodoRespVO {

    @Schema(description = "待办编号", example = "97101")
    private Long id;

    @Schema(description = "归属账号", example = "admin")
    private String account;

    @Schema(description = "日期", example = "2026-09-14")
    private LocalDate date;

    @Schema(description = "开始时间 HHMM", example = "0900")
    private String begin;

    @Schema(description = "结束时间 HHMM", example = "1000")
    private String end;

    @Schema(description = "类型", example = "custom")
    private String type;

    @Schema(description = "类型名称", example = "自定义")
    private String typeName;

    @Schema(description = "关联对象编号", example = "0")
    private Long objectID;

    @Schema(description = "优先级", example = "1")
    private Integer pri;

    @Schema(description = "名称", example = "评审需求变更")
    private String name;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "状态", example = "wait")
    private String status;

    @Schema(description = "是否私有", example = "0")
    private Integer privateFlag;

    @Schema(description = "是否周期待办", example = "0")
    private Integer cycle;

    @Schema(description = "指派人", example = "admin")
    private String assignedTo;

    @Schema(description = "指派人", example = "admin")
    private String assignedBy;

    @Schema(description = "指派时间")
    private LocalDateTime assignedDate;

    @Schema(description = "完成人", example = "admin")
    private String finishedBy;

    @Schema(description = "完成时间")
    private LocalDateTime finishedDate;

    @Schema(description = "关闭人", example = "admin")
    private String closedBy;

    @Schema(description = "关闭时间")
    private LocalDateTime closedDate;

    @Schema(description = "是否已过期（未完成且日期早于今天）", example = "false")
    private Boolean overdue;

}
