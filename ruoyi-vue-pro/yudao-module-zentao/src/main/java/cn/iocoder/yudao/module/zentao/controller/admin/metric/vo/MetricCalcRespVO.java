package cn.iocoder.yudao.module.zentao.controller.admin.metric.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 度量计算结果 Response VO")
@Data
public class MetricCalcRespVO {

    @Schema(description = "度量项代码", example = "count_of_story_in_product")
    private String code;

    @Schema(description = "度量名称", example = "按产品统计的研发需求总数")
    private String name;

    @Schema(description = "本次算出的记录数", example = "3")
    private Integer recordCount;

    @Schema(description = "时间粒度（由记录里的时间列决定）：year/month/week/day/nodate", example = "nodate")
    private String cycle;

    @Schema(description = "计算方式：cron 定时 / inference 人工触发", example = "inference")
    private String calcType;

    @Schema(description = "计算时间")
    private LocalDateTime calcTime;

}
