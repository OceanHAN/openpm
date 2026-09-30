package cn.iocoder.yudao.module.zentao.controller.admin.stage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 阶段模板创建/修改 Request VO")
@Data
public class StageSaveReqVO {

    @Schema(description = "阶段模板编号，新建时为空", example = "1")
    private Long id;

    // 注意：批量创建接口的 workflowGroup 来自路径参数，所以这里不做 @NotNull 校验，
    // 由 Service 在单条创建时自行校验（否则 @Valid 会先把批量请求拦下来）
    @Schema(description = "所属流程模板组。同一个瀑布流程共用一套阶段。单条创建时必填", example = "1")
    private Long workflowGroup;

    @Schema(description = "阶段名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "开发")
    @NotBlank(message = "阶段名称不能为空")
    @Size(max = 255, message = "阶段名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "工作量占比（%）。同组累计不能超过 100", example = "30")
    private String percent;

    @Schema(description = "阶段类型：request/design/dev/qa/release/review/other",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "dev")
    @NotBlank(message = "阶段类型不能为空")
    private String type;

    @Schema(description = "适用的项目流程类型：waterfall/waterfallplus/ipd", example = "waterfall")
    private String projectType;

}
