package cn.iocoder.yudao.module.zentao.controller.admin.effort.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 按账号汇总工时 Response VO")
@Data
public class EffortSummaryRespVO {

    @Schema(description = "账号", example = "admin")
    private String account;

    @Schema(description = "涉及任务数", example = "3")
    private Integer taskCount;

    @Schema(description = "工时记录条数", example = "8")
    private Integer effortCount;

    @Schema(description = "合计消耗工时", example = "26.50")
    private BigDecimal consumed;

}
