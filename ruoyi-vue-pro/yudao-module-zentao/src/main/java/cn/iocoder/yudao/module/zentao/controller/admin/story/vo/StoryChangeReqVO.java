package cn.iocoder.yudao.module.zentao.controller.admin.story.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 需求变更 Request VO")
@Data
public class StoryChangeReqVO {

    @Schema(description = "需求编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "需求编号不能为空")
    private Long id;

    /**
     * 变更后的标题。对应禅道 change() 里写入新版本快照的 title。
     */
    @Schema(description = "需求标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "支持需求批量导入（含校验）")
    @NotBlank(message = "需求标题不能为空")
    private String title;

    @Schema(description = "需求描述（本次变更后的内容）")
    private String spec;

    @Schema(description = "验收标准（本次变更后的内容）")
    private String verify;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

    /**
     * 变更说明。禅道会把变更前后的字段差异记进 zt_action，
     * 这里先只保留文字说明，对接 yudao 的操作日志组件。
     */
    @Schema(description = "变更说明", example = "客户追加了导入校验要求")
    private String comment;

}
