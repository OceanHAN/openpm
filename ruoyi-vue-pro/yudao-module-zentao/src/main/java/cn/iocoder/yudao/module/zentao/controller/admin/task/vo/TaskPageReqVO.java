package cn.iocoder.yudao.module.zentao.controller.admin.task.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 任务分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskPageReqVO extends PageParam {

    @Schema(description = "所属执行", example = "1")
    private Long execution;

    @Schema(description = "所属模块", example = "0")
    private Long module;

    @Schema(hidden = true, description = "模块子树编号，由 Service 展开后填充")
    private List<Long> moduleIds;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "关联需求", example = "1")
    private Long story;

    @Schema(description = "状态，参见 TaskStatusEnum", example = "doing")
    private String status;

    @Schema(description = "优先级", example = "3")
    private Integer pri;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

    @Schema(description = "任务名称，模糊匹配", example = "登录")
    private String name;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] openedDate;

}
