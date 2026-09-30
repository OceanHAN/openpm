package cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 项目/执行关联需求 Request VO
 *
 * <p>对应禅道 {@code execution::linkStory()}：一次可以勾选多条需求。
 */
@Schema(description = "管理后台 - 项目/执行关联需求 Request VO")
@Data
public class ProjectStoryLinkReqVO {

    @Schema(description = "项目/执行编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "项目/执行编号不能为空")
    private Long project;

    @Schema(description = "需求编号列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1,2]")
    @NotEmpty(message = "请至少选择一条需求")
    private List<Long> storyIds;

}
