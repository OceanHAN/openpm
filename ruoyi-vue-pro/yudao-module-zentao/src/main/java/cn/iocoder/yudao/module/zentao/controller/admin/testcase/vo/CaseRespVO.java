package cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Schema(description = "管理后台 - 测试用例 Response VO")
@Data
public class CaseRespVO {

    @Schema(description = "用例编号", example = "93101")
    private Long id;

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属分支/平台", example = "0")
    private Long branch;

    @Schema(description = "所属模块", example = "93301")
    private Long module;

    @Schema(description = "关联需求", example = "1")
    private Long story;

    @Schema(description = "关联需求的标题", example = "支持需求批量导入V2")
    private String storyTitle;

    @Schema(description = "关联时冻结的需求版本", example = "1")
    private Integer storyVersion;

    @Schema(description = "需求当前版本", example = "2")
    private Integer latestStoryVersion;

    @Schema(description = "是否需要确认需求变更（需求升版且仍激活）", example = "true")
    private Boolean needConfirm;

    @Schema(description = "标题", example = "正常登录-用户名密码正确")
    private String title;

    @Schema(description = "前置条件")
    private String precondition;

    @Schema(description = "关键词", example = "登录,冒烟")
    private String keywords;

    @Schema(description = "优先级", example = "1")
    private Integer pri;

    @Schema(description = "类型", example = "feature")
    private String type;

    @Schema(description = "类型文案", example = "功能测试")
    private String typeName;

    @Schema(description = "测试环节（逗号列表）", example = "smoke,feature")
    private String stage;

    @Schema(description = "测试环节文案", example = "冒烟测试环节,功能测试环节")
    private String stageName;

    @Schema(description = "状态", example = "normal")
    private String status;

    @Schema(description = "状态文案", example = "正常")
    private String statusName;

    @Schema(description = "当前版本号", example = "2")
    private Integer version;

    @Schema(description = "最近执行结果", example = "pass")
    private String lastRunResult;

    @Schema(description = "最近执行人", example = "admin")
    private String lastRunner;

    @Schema(description = "最近执行时间")
    private LocalDateTime lastRunDate;

    @Schema(description = "创建人", example = "admin")
    private String openedBy;

    @Schema(description = "创建时间")
    private LocalDateTime openedDate;

    @Schema(description = "评审人", example = "admin")
    private String reviewedBy;

    @Schema(description = "评审时间")
    private LocalDate reviewedDate;

    @Schema(description = "最后修改人", example = "admin")
    private String lastEditedBy;

    @Schema(description = "最后修改时间")
    private LocalDateTime lastEditedDate;

    @Schema(description = "来源缺陷", example = "0")
    private Long fromBug;

    @Schema(description = "所属用例库（0=产品用例）", example = "0")
    private Long lib;

    @Schema(description = "来源产品用例编号（库内用例从产品导入时记录，0=库里手建）", example = "0")
    private Long fromCaseID;

    @Schema(description = "导入时来源用例的版本", example = "1")
    private Integer fromCaseVersion;

    @Schema(description = "库内用例的来源用例已升版（来源版本 > 导入时的版本）", example = "false")
    private Boolean sourceChanged;

    @Schema(description = "步骤（带层级编号）")
    private List<CaseStepVO> steps;

}
