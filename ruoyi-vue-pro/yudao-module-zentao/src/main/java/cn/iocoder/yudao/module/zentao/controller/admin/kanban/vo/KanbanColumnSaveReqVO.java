package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 看板列新增/修改 Request VO")
@Data
public class KanbanColumnSaveReqVO {

    @Schema(description = "列编号（修改时必填）", example = "96401")
    private Long id;

    @Schema(description = "所属分组", requiredMode = Schema.RequiredMode.REQUIRED, example = "96251")
    @NotNull(message = "所属分组不能为空")
    private Long group;

    @Schema(description = "列名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "进行中")
    @NotBlank(message = "列名称不能为空")
    @Size(max = 255, message = "列名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "在制品上限（WIP）：-1 不限，或正整数", example = "3")
    private Integer limit;

    @Schema(description = "列颜色", example = "#2b519c")
    private String color;

    @Schema(description = "父列（拆分子列时用）", example = "0")
    private Long parent;

    @Schema(description = "排序", example = "1")
    private Integer order;

}
