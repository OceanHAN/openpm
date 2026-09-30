package cn.iocoder.yudao.module.zentao.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 任务完成 Request VO")
@Data
public class TaskFinishReqVO {

    @Schema(description = "任务编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "任务编号不能为空")
    private Long id;

    /**
     * 禅道的工时模型：完成任务时要登记这次消耗了多少工时、还剩多少。
     * 剩余工时归零才表示任务真正做完。
     */
    @Schema(description = "本次消耗工时", requiredMode = Schema.RequiredMode.REQUIRED, example = "3.00")
    @NotNull(message = "消耗工时不能为空")
    @DecimalMin(value = "0", message = "消耗工时不能为负")
    private BigDecimal consumed;

    @Schema(description = "剩余工时", requiredMode = Schema.RequiredMode.REQUIRED, example = "0.00")
    @NotNull(message = "剩余工时不能为空")
    @DecimalMin(value = "0", message = "剩余工时不能为负")
    private BigDecimal left;

    @Schema(description = "完成说明", example = "接口已联调通过")
    private String comment;

}
