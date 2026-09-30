package cn.iocoder.yudao.module.zentao.controller.admin.story.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - 需求关闭 Request VO")
@Data
public class StoryCloseReqVO {

    @Schema(description = "需求编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    /**
     * 对应禅道 {@code zt_story.closedReason}
     */
    @Schema(description = "关闭原因：done/duplicate/postponed/willnotdo/bydesign",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "done")
    @NotBlank(message = "关闭原因不能为空")
    private String closedReason;

    /**
     * 禅道规则：关闭原因为 duplicate 时，必须指定重复的目标需求，且该需求必须真实存在。
     */
    @Schema(description = "重复需求编号，关闭原因为 duplicate 时必填", example = "2048")
    private Long duplicateStory;

    @Schema(description = "备注", example = "已由 2048 覆盖")
    private String comment;

}
