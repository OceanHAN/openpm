package cn.iocoder.yudao.module.zentao.controller.admin.bi.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 图表数据 Response VO")
@Data
public class ChartDataRespVO {

    @Schema(description = "图表定义")
    private ChartRespVO chart;

    @Schema(description = "维度名（分组字段）", example = "status")
    private String dimensionField;

    @Schema(description = "指标名", example = "id")
    private String metricField;

    @Schema(description = "聚合方式", example = "count")
    private String agg;

    @Schema(description = "数据行：[{name, value}]")
    private List<Map<String, Object>> rows = new ArrayList<>();

    @Schema(description = "实际执行的 SQL")
    private String executedSql;

}
