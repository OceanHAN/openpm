package cn.iocoder.yudao.module.zentao.controller.admin.story.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 提交需求评审 Request VO")
@Data
public class StoryReviewStartReqVO {

    @Schema(description = "需求编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "需求编号不能为空")
    private Long id;

    /**
     * 评审人账号列表。禅道会把这些人写进 zt_storyreview，每人一行，result 初始为空。
     */
    @Schema(description = "评审人账号列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "评审人不能为空")
    private List<String> reviewers;

    @Schema(description = "提交说明", example = "请评审 v2 的验收标准")
    private String comment;

}
