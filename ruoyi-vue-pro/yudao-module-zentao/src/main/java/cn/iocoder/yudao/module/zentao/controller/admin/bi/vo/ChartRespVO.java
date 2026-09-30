package cn.iocoder.yudao.module.zentao.controller.admin.bi.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 图表 Response VO")
@Data
public class ChartRespVO {

    @Schema(description = "编号", example = "97101")
    private Long id;

    @Schema(description = "图表名称", example = "需求状态分布")
    private String name;

    @Schema(description = "图表代码", example = "story_status_pie")
    private String code;

    @Schema(description = "图表类型", example = "pie")
    private String type;

    @Schema(description = "图表类型名", example = "饼图")
    private String typeName;

    @Schema(description = "前端渲染类型（echarts）：pie/line/bar", example = "pie")
    private String echartsType;

    @Schema(description = "数据源：数据视图代码")
    private String viewCode;

    @Schema(description = "数据源 SQL")
    private String sql;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "状态：draft 草稿 / published 已发布", example = "published")
    private String stage;

    @Schema(description = "版本", example = "1")
    private String version;

    @Schema(description = "查询设置")
    private Map<String, Object> settings = new LinkedHashMap<>();

    @Schema(description = "过滤器")
    private List<Map<String, Object>> filters = new ArrayList<>();

    @Schema(description = "字段")
    private List<Map<String, Object>> fields = new ArrayList<>();

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

}
