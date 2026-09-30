package cn.iocoder.yudao.module.zentao.controller.admin.bi.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 数据视图新增/修改 Request VO")
@Data
public class DataViewSaveReqVO {

    @Schema(description = "编号（修改时必填）", example = "97001")
    private Long id;

    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "需求数据")
    @NotBlank(message = "名称不能为空")
    @Size(max = 155, message = "名称长度不能超过 155 个字符")
    private String name;

    @Schema(description = "代码（图表用它引用数据视图）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "story_data")
    @NotBlank(message = "代码不能为空")
    @Size(max = 50, message = "代码长度不能超过 50 个字符")
    private String code;

    @Schema(description = "只读查询 SQL（单条 SELECT，只允许 zt_* 表）",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "SELECT id, status FROM zt_story WHERE deleted = 0")
    @NotBlank(message = "SQL 不能为空")
    private String sql;

    @Schema(description = "字段定义（不传则按 SQL 自动解析）")
    private List<Map<String, Object>> fields;

    @Schema(description = "字段中文名")
    private Map<String, String> langs;

    @Schema(description = "数据来源对象")
    private List<Map<String, Object>> objects;

}
