package cn.iocoder.yudao.module.zentao.controller.admin.release.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - 发布 Response VO")
@Data
public class ReleaseRespVO {

    @Schema(description = "发布编号", example = "1")
    private Long id;

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "产品名称", example = "禅道研发管理平台")
    private String productName;

    @Schema(description = "所属项目，逗号列表", example = ",1,")
    private String project;

    @Schema(description = "项目名称列表", example = "[\"禅道迁移一期\"]")
    private List<String> projectNames;

    @Schema(description = "分支/平台，逗号列表", example = ",0,")
    private String branch;

    @Schema(description = "分支/平台名称", example = "主干")
    private String branchName;

    @Schema(description = "包含的构建，逗号列表", example = ",1,2,")
    private String build;

    @Schema(description = "构建名称列表", example = "[\"V1.0-beta1\"]")
    private List<String> buildNames;

    @Schema(description = "影子构建编号（创建发布时自动生成的构建）", example = "9")
    private Long shadow;

    @Schema(description = "发布版本号", example = "V1.0")
    private String name;

    @Schema(description = "是否里程碑", example = "1")
    private Integer marker;

    @Schema(description = "计划发布日期", example = "2026-02-28")
    private LocalDate date;

    @Schema(description = "实际发布日期")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime releasedDate;

    @Schema(description = "状态：wait/normal/fail/terminate", example = "normal")
    private String status;

    @Schema(description = "状态文案", example = "已发布")
    private String statusName;

    @Schema(description = "本次完成的需求，逗号列表", example = "1,2,4")
    private String stories;

    @Schema(description = "本次解决的 Bug，逗号列表", example = "")
    private String bugs;

    @Schema(description = "遗留的 Bug，逗号列表", example = "")
    private String leftBugs;

    @Schema(description = "包含的子发布，逗号列表")
    private String releases;

    @Schema(description = "需求数量", example = "3")
    private Long storyCount;

    @Schema(description = "解决的 Bug 数量", example = "0")
    private Long bugCount;

    @Schema(description = "遗留的 Bug 数量", example = "0")
    private Long leftBugCount;

    @Schema(description = "是否被别的发布包含（子发布）", example = "false")
    private Boolean included;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

}
