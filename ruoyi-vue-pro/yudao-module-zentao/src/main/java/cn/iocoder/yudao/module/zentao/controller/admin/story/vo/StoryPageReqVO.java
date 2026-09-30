package cn.iocoder.yudao.module.zentao.controller.admin.story.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 需求分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class StoryPageReqVO extends PageParam {

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属模块", example = "0")
    private Long module;

    @Schema(description = "所属分支/平台。产品类型决定含义", example = "0")
    private Long branch;

    @Schema(description = "所属计划。需求侧 plan 是逗号列表，匹配规则是「包含」", example = "1")
    private Long plan;

    @Schema(hidden = true, description = "模块子树编号。由 Service 把 module 展开成「自己 + 子孙」后填充，"
            + "对齐禅道「选父模块连带查出子模块数据」的行为")
    private List<Long> moduleIds;

    @Schema(description = "状态，参见 StoryStatusEnum", example = "active")
    private String status;

    @Schema(description = "研发阶段，参见 StoryStageEnum", example = "developing")
    private String stage;

    @Schema(description = "需求分类，参见 StoryCategoryEnum", example = "feature")
    private String category;

    @Schema(description = "需求分层类型，参见 StoryTypeEnum：epic=业务需求 / requirement=用户需求 / story=研发需求。"
            + "不传表示全部", example = "story")
    private String type;

    @Schema(description = "需求分层类型集合，用于「全部」之外的多类型筛选（如只要业务需求+用户需求）",
            example = "epic,requirement")
    private List<String> types;

    @Schema(description = "优先级", example = "3")
    private Integer pri;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

    @Schema(description = "需求标题，模糊匹配", example = "导入")
    private String title;

    @Schema(description = "创建时间区间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] openedDate;

}
