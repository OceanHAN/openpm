package cn.iocoder.yudao.module.zentao.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 任务创建/修改 Request VO")
@Data
public class TaskSaveReqVO {

    @Schema(description = "任务编号，新建时为空", example = "1024")
    private Long id;

    @Schema(description = "所属项目", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属项目不能为空")
    private Long project;

    @Schema(description = "所属执行", example = "1")
    private Long execution;

    @Schema(description = "所属模块", example = "0")
    private Long module;

    @Schema(description = "关联需求", example = "0")
    private Long story;

    @Schema(description = "建任务时需求的版本（由后端冻结，无需前端传）", example = "1")
    private Integer storyVersion;

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "实现登录接口")
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 255, message = "任务名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "任务类型", example = "devel")
    private String type;

    @Schema(description = "优先级，1~4", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @NotNull(message = "优先级不能为空")
    @Min(value = 1, message = "优先级最小为 1")
    @Max(value = 4, message = "优先级最大为 4")
    private Integer pri;

    @Schema(description = "预计工时", example = "8.00")
    private BigDecimal estimate;

    @Schema(description = "剩余工时", example = "8.00")
    private BigDecimal left;

    @Schema(description = "截止日期")
    private LocalDate deadline;

    @Schema(description = "预计开始")
    private LocalDate estStarted;

    @Schema(description = "关键词")
    private String keywords;

    @Schema(description = "任务描述")
    private String desc;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

}
