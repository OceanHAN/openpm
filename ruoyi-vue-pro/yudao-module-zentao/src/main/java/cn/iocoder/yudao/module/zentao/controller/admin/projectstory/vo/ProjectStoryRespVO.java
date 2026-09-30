package cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;


@Schema(description = "管理后台 - 项目需求 Response VO")
@Data
public class ProjectStoryRespVO {

    @Schema(description = "关系编号", example = "1")
    private Long id;

    @Schema(description = "项目/执行编号", example = "1")
    private Long project;

    @Schema(description = "需求编号", example = "1")
    private Long story;

    @Schema(description = "需求标题", example = "支持需求批量导入")
    private String title;

    @Schema(description = "需求所属产品", example = "1")
    private Long product;

    @Schema(description = "产品名称", example = "禅道研发管理平台")
    private String productName;

    @Schema(description = "分支/平台", example = "0")
    private Long branch;

    @Schema(description = "需求状态", example = "active")
    private String status;

    @Schema(description = "需求当前阶段", example = "developing")
    private String stage;

    @Schema(description = "需求优先级", example = "3")
    private Integer pri;

    @Schema(description = "预计工时", example = "8.00")
    private java.math.BigDecimal estimate;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

    @Schema(description = "关联时的需求版本", example = "1")
    private Integer linkVersion;

    @Schema(description = "需求当前版本", example = "2")
    private Integer currentVersion;

    @Schema(description = "版本是否已变更（当前版本 > 关联版本）", example = "true")
    private Boolean versionChanged;

    @Schema(description = "排序", example = "1")
    private Integer order;

    @Schema(description = "需求创建时间")
    private LocalDateTime openedDate;

    @Schema(description = "已关联该需求的项目/执行编号（用于提示影响范围）", example = "[1,2]")
    private List<Long> relatedProjects;

}
