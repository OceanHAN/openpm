package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 看板卡片 Response VO")
@Data
public class KanbanCardRespVO {

    @Schema(description = "卡片编号", example = "96501")
    private Long id;

    @Schema(description = "所属看板", example = "96101")
    private Long kanban;

    @Schema(description = "所属区域", example = "96201")
    private Long region;

    @Schema(description = "所属分组", example = "96251")
    private Long groupId;

    @Schema(description = "卡片标题", example = "打通登录链路")
    private String name;

    @Schema(description = "状态：doing 进行中 / done 已完成", example = "doing")
    private String status;

    @Schema(description = "状态名", example = "进行中")
    private String statusName;

    @Schema(description = "优先级", example = "2")
    private Integer pri;

    @Schema(description = "指派给", example = "dev1")
    private String assignedTo;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "预计开始")
    private LocalDate begin;

    @Schema(description = "截止日期")
    private LocalDate end;

    @Schema(description = "预计工时", example = "8.00")
    private BigDecimal estimate;

    @Schema(description = "进度", example = "40.00")
    private BigDecimal progress;

    @Schema(description = "卡片颜色")
    private String color;

    @Schema(description = "是否归档", example = "false")
    private Boolean archived;

    @Schema(description = "来源对象编号（0=看板自建）", example = "0")
    private Long fromID;

    @Schema(description = "来源对象类型", example = "")
    private String fromType;

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

}
