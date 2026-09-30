package cn.iocoder.yudao.module.zentao.controller.admin.story.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;


@Schema(description = "管理后台 - 需求评审情况 Response VO")
@Data
public class StoryReviewRespVO {

    @Schema(description = "需求编号", example = "1024")
    private Long story;

    @Schema(description = "被评审的版本号", example = "2")
    private Integer version;

    @Schema(description = "评审人列表")
    private List<ReviewerItem> reviewers;

    @Schema(description = "聚合结果。为空表示还有评审人未提交", example = "pass")
    private String finalResult;

    @Schema(description = "是否已全部评审完成")
    private Boolean finished;

    @Schema(description = "单个评审人的记录")
    @Data
    public static class ReviewerItem {

        @Schema(description = "评审人账号", example = "admin")
        private String reviewer;

        @Schema(description = "评审结果，空表示未提交", example = "pass")
        private String result;

        @Schema(description = "评审时间")
        private LocalDateTime reviewDate;

    }

}
