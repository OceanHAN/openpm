package cn.iocoder.yudao.module.zentao.controller.admin.effort.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - 任务工时统计 Response VO")
@Data
public class EffortTaskStatRespVO {

    @Schema(description = "任务编号", example = "1")
    private Long taskId;

    @Schema(description = "任务名称", example = "实现登录接口")
    private String taskName;

    @Schema(description = "预计工时", example = "8")
    private BigDecimal estimate;

    @Schema(description = "已消耗工时（所有工时记录之和）", example = "7")
    private BigDecimal consumed;

    @Schema(description = "剩余工时（最后一条工时声明的值）", example = "1")
    private BigDecimal left;

    @Schema(description = "任务状态", example = "done")
    private String status;

    @Schema(description = "工时记录条数", example = "2")
    private Integer effortCount;

    @Schema(description = "工时明细")
    private List<EffortRespVO> efforts;

}
