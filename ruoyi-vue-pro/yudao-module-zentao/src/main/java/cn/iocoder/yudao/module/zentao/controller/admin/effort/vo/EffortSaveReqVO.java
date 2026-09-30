package cn.iocoder.yudao.module.zentao.controller.admin.effort.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 工时新增/修改 Request VO")
@Data
public class EffortSaveReqVO {

    @Schema(description = "工时编号，修改时必填", example = "96101")
    private Long id;

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "任务编号不能为空")
    private Long taskId;

    @Schema(description = "报工时的账号，不填则取当前登录用户", example = "admin")
    private String account;

    @Schema(description = "工作日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-03-02")
    @NotNull(message = "工作日期不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @Schema(description = "本次消耗工时", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @NotNull(message = "消耗工时不能为空")
    @DecimalMin(value = "0", message = "消耗工时不能为负数")
    private BigDecimal consumed;

    @Schema(description = "这之后剩余工时。不填则按「任务当前剩余 - 本次消耗」推算（不会小于 0）", example = "5")
    @DecimalMin(value = "0", message = "剩余工时不能为负数")
    private BigDecimal left;

    @Schema(description = "做了什么", requiredMode = Schema.RequiredMode.REQUIRED, example = "搭好接口骨架")
    @NotBlank(message = "工作内容不能为空")
    @Size(max = 500, message = "工作内容不能超过 500 字")
    private String work;

    @Schema(description = "开始时间 HHMM", example = "0900")
    @Size(max = 4, message = "开始时间必须是四位 HHMM")
    private String begin;

    @Schema(description = "结束时间 HHMM", example = "1200")
    @Size(max = 4, message = "结束时间必须是四位 HHMM")
    private String end;

}
