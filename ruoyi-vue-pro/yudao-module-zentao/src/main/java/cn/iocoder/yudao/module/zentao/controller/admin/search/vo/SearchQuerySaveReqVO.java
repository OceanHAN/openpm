package cn.iocoder.yudao.module.zentao.controller.admin.search.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 保存/修改一条查询。
 *
 * <p>⚠️ {@code conditions} 是**结构化条件 JSON**，不是 SQL —— 见 {@code SearchQueryDO} 的类注释。
 * 例：{@code [{"field":"status","op":"eq","value":"active"},{"field":"title","op":"like","value":"登录"}]}
 */
@Schema(description = "管理后台 - 保存查询 Request VO")
@Data
public class SearchQuerySaveReqVO {

    @Schema(description = "编号（修改时传）", example = "1")
    private Long id;

    @Schema(description = "模块（story/task/bug/…）", requiredMode = Schema.RequiredMode.REQUIRED, example = "story")
    @NotEmpty(message = "模块不能为空")
    private String module;

    @Schema(description = "查询名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "激活的需求")
    @NotEmpty(message = "查询名称不能为空")
    private String title;

    @Schema(description = "表单定义快照（JSON，前端回填搜索表单用）")
    private String form;

    @Schema(description = "结构化条件 JSON（不是 SQL）",
            example = "[{\"field\":\"status\",\"op\":\"eq\",\"value\":\"active\"}]")
    private String conditions;

    @Schema(description = "是否快捷方式：1 是（显示在列表页页签上）", example = "0")
    private Integer shortcut;

    @Schema(description = "是否公共查询：1 是（所有人可见）", example = "0")
    private Integer common;

}
