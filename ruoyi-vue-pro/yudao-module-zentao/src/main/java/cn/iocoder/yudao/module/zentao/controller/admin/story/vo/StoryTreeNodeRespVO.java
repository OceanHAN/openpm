package cn.iocoder.yudao.module.zentao.controller.admin.story.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - 需求分层树节点 Response VO")
@Data
public class StoryTreeNodeRespVO {

    @Schema(description = "需求编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "父需求编号，0 表示顶层", example = "0")
    private Long parent;

    @Schema(description = "需求标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "支持需求批量导入")
    private String title;

    @Schema(description = "需求分层类型：epic / requirement / story", example = "requirement")
    private String type;

    @Schema(description = "需求分层类型名", example = "用户需求")
    private String typeName;

    @Schema(description = "树层级（业务需求 1 / 用户需求 2 / 研发需求 3 …），对应禅道 zt_story.grade", example = "2")
    private Integer grade;

    @Schema(description = "状态", example = "active")
    private String status;

    @Schema(description = "研发阶段", example = "developing")
    private String stage;

    @Schema(description = "优先级", example = "3")
    private Integer pri;

    @Schema(description = "预计工时（父需求是子需求之和）", example = "10.00")
    private BigDecimal estimate;

    @Schema(description = "子需求数量", example = "2")
    private Integer childCount;

    @Schema(description = "子需求")
    private List<StoryTreeNodeRespVO> children = new ArrayList<>();

}
