package cn.iocoder.yudao.module.zentao.controller.admin.bi.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 图表新增/修改 Request VO")
@Data
public class ChartSaveReqVO {

    @Schema(description = "编号（修改时必填）", example = "97101")
    private Long id;

    @Schema(description = "图表名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "需求状态分布")
    @NotBlank(message = "图表名称不能为空")
    @Size(max = 255, message = "图表名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "图表代码", example = "story_status_pie")
    @Size(max = 255, message = "图表代码长度不能超过 255 个字符")
    private String code;

    @Schema(description = "图表类型：pie/line/cluBarX/stackedBar/...", example = "pie")
    private String type;

    @Schema(description = "数据源：数据视图代码（与 sql 二选一，viewCode 优先）", example = "story_data")
    private String viewCode;

    @Schema(description = "数据源：只读查询 SQL（viewCode 为空时用）",
            example = "SELECT id, status FROM zt_story WHERE deleted = 0")
    private String sql;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "查询设置：{dimensionField, metricField, agg, limit, sort}")
    private Map<String, Object> settings;

    @Schema(description = "过滤器：[{field, operator, value}]，operator 支持 eq/ne/like/gt/ge/lt/le/in/between")
    private List<Map<String, Object>> filters;

}
