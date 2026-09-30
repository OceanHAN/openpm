package cn.iocoder.yudao.module.zentao.controller.admin.metric.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 度量数据 Response VO")
@Data
public class MetricDataRespVO {

    @Schema(description = "度量项定义")
    private MetricRespVO metric;

    @Schema(description = "数据行（维度列 + 时间列 + value + date，另带 scopeObjectName 便于展示）")
    private List<Map<String, Object>> rows = new ArrayList<>();

    @Schema(description = "总条数", example = "3")
    private Long total;

}
