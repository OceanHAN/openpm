package cn.iocoder.yudao.module.zentao.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Schema(description = "管理后台 - 任务信息 Response VO")
@Data
public class TaskRespVO {

    @Schema(description = "任务编号", example = "1024")
    private Long id;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "1")
    private Long execution;

    @Schema(description = "所属模块", example = "0")
    private Long module;

    @Schema(description = "关联需求", example = "0")
    private Long story;

    @Schema(description = "建任务时需求的版本（冻结）", example = "1")
    private Integer storyVersion;

    @Schema(description = "关联需求的标题", example = "支持需求批量导入V2")
    private String storyTitle;

    @Schema(description = "需求当前版本", example = "2")
    private Integer latestStoryVersion;

    @Schema(description = "需求已变更（需求升版且仍激活，任务需要确认）", example = "true")
    private Boolean storyChanged;

    @Schema(description = "来源缺陷", example = "0")
    private Long fromBug;

    @Schema(description = "任务名称", example = "实现登录接口")
    private String name;

    @Schema(description = "任务类型", example = "devel")
    private String type;

    @Schema(description = "优先级", example = "3")
    private Integer pri;

    @Schema(description = "预计工时", example = "8.00")
    private BigDecimal estimate;

    @Schema(description = "已消耗工时", example = "3.00")
    private BigDecimal consumed;

    @Schema(description = "剩余工时", example = "5.00")
    private BigDecimal left;

    @Schema(description = "截止日期")
    private LocalDate deadline;

    @Schema(description = "关键词")
    private String keywords;

    @Schema(description = "任务描述")
    private String desc;

    @Schema(description = "版本号", example = "1")
    private Integer version;

    @Schema(description = "状态", example = "doing")
    private String status;

    @Schema(description = "创建人", example = "admin")
    private String openedBy;

    @Schema(description = "创建时间")
    private LocalDateTime openedDate;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

    @Schema(description = "指派时间")
    private LocalDateTime assignedDate;

    @Schema(description = "预计开始")
    private LocalDate estStarted;

    @Schema(description = "实际开始")
    private LocalDateTime realStarted;

    @Schema(description = "完成人", example = "admin")
    private String finishedBy;

    @Schema(description = "完成时间")
    private LocalDateTime finishedDate;

    @Schema(description = "取消人", example = "admin")
    private String canceledBy;

    @Schema(description = "取消时间")
    private LocalDateTime canceledDate;

    @Schema(description = "关闭人", example = "admin")
    private String closedBy;

    @Schema(description = "关闭时间")
    private LocalDateTime closedDate;

    @Schema(description = "关闭原因", example = "done")
    private String closedReason;

    @Schema(description = "最后修改人", example = "admin")
    private String lastEditedBy;

    @Schema(description = "最后修改时间")
    private LocalDateTime lastEditedDate;

    @Schema(description = "激活时间")
    private LocalDateTime activatedDate;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
