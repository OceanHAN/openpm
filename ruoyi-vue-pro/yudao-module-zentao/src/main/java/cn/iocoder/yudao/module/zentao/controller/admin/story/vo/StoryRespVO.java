package cn.iocoder.yudao.module.zentao.controller.admin.story.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Schema(description = "管理后台 - 需求信息 Response VO")
@Data
public class StoryRespVO {

    @Schema(description = "需求编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "所属产品", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long product;

    @Schema(description = "所属模块", example = "0")
    private Long module;

    @Schema(description = "所属计划", example = "1,2")
    private String plan;

    @Schema(description = "所属分支", example = "0")
    private Long branch;

    @Schema(description = "需求标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "支持需求批量导入")
    private String title;

    @Schema(description = "关键词", example = "导入,批量")
    private String keywords;

    @Schema(description = "需求描述")
    private String spec;

    @Schema(description = "验收标准")
    private String verify;

    @Schema(description = "需求类型", example = "story")
    private String type;

    @Schema(description = "需求分类", example = "feature")
    private String category;

    @Schema(description = "优先级", example = "3")
    private Integer pri;

    @Schema(description = "预计工时", example = "8.00")
    private BigDecimal estimate;

    @Schema(description = "状态", example = "active")
    private String status;

    @Schema(description = "研发阶段", example = "developing")
    private String stage;

    @Schema(description = "版本号", example = "1")
    private Integer version;

    @Schema(description = "需求来源", example = "customer")
    private String source;

    @Schema(description = "来源备注")
    private String sourceNote;

    @Schema(description = "父需求", example = "4")
    private Long parent;

    @Schema(description = "父需求标题", example = "支持需求批量导入（含校验）")
    private String parentTitle;

    @Schema(description = "分解时父需求的版本（冻结）", example = "2")
    private Integer parentVersion;

    @Schema(description = "父需求已变更（父需求升版且仍激活，需要确认）", example = "false")
    private Boolean parentChanged;

    @Schema(description = "顶层祖先", example = "4")
    private Long root;

    @Schema(description = "树路径", example = ",4,92201,")
    private String path;

    @Schema(description = "层级", example = "2")
    private Integer grade;

    @Schema(description = "是否已分解（有子需求）", example = "false")
    private Boolean isParent;

    @Schema(description = "子需求数", example = "2")
    private Long childCount;

    @Schema(description = "由哪个 Bug 转化而来", example = "0")
    private Long fromBug;

    @Schema(description = "创建人", example = "admin")
    private String openedBy;

    @Schema(description = "创建时间")
    private LocalDateTime openedDate;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

    @Schema(description = "指派时间")
    private LocalDateTime assignedDate;

    @Schema(description = "关闭人", example = "admin")
    private String closedBy;

    @Schema(description = "关闭时间")
    private LocalDateTime closedDate;

    @Schema(description = "关闭原因", example = "done")
    private String closedReason;

    @Schema(description = "重复需求指向的需求编号", example = "0")
    private Long duplicateStory;

    @Schema(description = "激活时间")
    private LocalDateTime activatedDate;

    @Schema(description = "最后修改人", example = "admin")
    private String lastEditedBy;

    @Schema(description = "最后修改时间")
    private LocalDateTime lastEditedDate;

    @Schema(description = "已评审人，逗号分隔", example = "admin")
    private String reviewedBy;

    @Schema(description = "最后评审时间")
    private LocalDateTime reviewedDate;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;

}
