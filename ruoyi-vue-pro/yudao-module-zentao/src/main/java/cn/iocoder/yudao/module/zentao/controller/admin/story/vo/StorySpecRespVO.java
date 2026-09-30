package cn.iocoder.yudao.module.zentao.controller.admin.story.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;


@Schema(description = "管理后台 - 需求版本快照 Response VO")
@Data
public class StorySpecRespVO {

    @Schema(description = "快照编号", example = "1")
    private Long id;

    @Schema(description = "需求编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long story;

    @Schema(description = "版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer version;

    @Schema(description = "该版本的需求标题", example = "支持需求批量导入")
    private String title;

    @Schema(description = "该版本的需求描述")
    private String spec;

    @Schema(description = "该版本的验收标准")
    private String verify;

    @Schema(description = "附件编号，逗号分隔")
    private String files;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "创建者", example = "admin")
    private String creator;

}
