package cn.iocoder.yudao.module.zentao.controller.admin.story.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 需求评审表决 Request VO")
@Data
public class StoryReviewSubmitReqVO {

    @Schema(description = "需求编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "需求编号不能为空")
    private Long id;

    /**
     * pass=确认通过 / clarify=有待明确 / revert=撤销变更 / reject=拒绝
     */
    @Schema(description = "评审结果：pass/clarify/revert/reject",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "pass")
    @NotBlank(message = "评审结果不能为空")
    private String result;

    @Schema(description = "评审意见", example = "验收标准清晰，同意通过")
    private String comment;

}
