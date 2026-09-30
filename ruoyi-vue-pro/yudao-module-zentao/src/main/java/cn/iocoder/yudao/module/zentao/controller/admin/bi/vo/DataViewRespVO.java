package cn.iocoder.yudao.module.zentao.controller.admin.bi.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 数据视图 Response VO")
@Data
public class DataViewRespVO {

    @Schema(description = "编号", example = "97001")
    private Long id;

    @Schema(description = "名称", example = "需求数据")
    private String name;

    @Schema(description = "代码", example = "story_data")
    private String code;

    @Schema(description = "模式", example = "sql")
    private String mode;

    @Schema(description = "只读查询 SQL")
    private String sql;

    @Schema(description = "字段定义")
    private List<Map<String, Object>> fields = new ArrayList<>();

    @Schema(description = "字段中文名")
    private Map<String, String> langs = new LinkedHashMap<>();

    @Schema(description = "数据来源对象")
    private List<Map<String, Object>> objects = new ArrayList<>();

    @Schema(description = "被多少图表引用", example = "1")
    private Long chartCount;

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

}
