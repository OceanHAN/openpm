package cn.iocoder.yudao.module.zentao.controller.admin.story.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 需求分层类型 Response VO")
@Data
public class StoryTypeRespVO {

    @Schema(description = "类型值，对应 zt_story.type", requiredMode = Schema.RequiredMode.REQUIRED, example = "epic")
    private String type;

    @Schema(description = "类型名", requiredMode = Schema.RequiredMode.REQUIRED, example = "业务需求")
    private String name;

    @Schema(description = "需求层级：1 业务需求 / 2 用户需求 / 3 研发需求",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer level;

    @Schema(description = "该类型允许挂在哪些父需求类型下，参见 StoryTypeEnum#parentTypesOf", example = "epic")
    private String parentTypes;

    @Schema(description = "分解（批量建子需求）时子需求的类型", example = "requirement")
    private String childType;

}
