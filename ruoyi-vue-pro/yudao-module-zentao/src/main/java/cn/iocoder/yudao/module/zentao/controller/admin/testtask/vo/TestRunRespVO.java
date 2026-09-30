package cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;


@Schema(description = "管理后台 - 测试单用例执行 Response VO")
@Data
public class TestRunRespVO {

    @Schema(description = "执行记录编号（runId）", example = "94151")
    private Long id;

    @Schema(description = "测试单编号", example = "94101")
    private Long task;

    @Schema(description = "用例编号", example = "93101")
    private Long caseId;

    @Schema(description = "用例标题", example = "正常登录-用户名密码正确")
    private String caseTitle;

    @Schema(description = "用例类型", example = "feature")
    private String caseType;

    @Schema(description = "用例优先级", example = "1")
    private Integer casePri;

    @Schema(description = "用例所属模块", example = "93301")
    private Long caseModule;

    @Schema(description = "排进来时的用例版本", example = "2")
    private Integer caseVersion;

    @Schema(description = "用例当前版本", example = "2")
    private Integer latestCaseVersion;

    @Schema(description = "用例版本已变更（排进来之后又改过步骤）", example = "false")
    private Boolean caseChanged;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

    @Schema(description = "最近执行结果", example = "pass")
    private String lastRunResult;

    @Schema(description = "最近执行结果文案", example = "通过")
    private String lastRunResultName;

    @Schema(description = "最近执行人", example = "admin")
    private String lastRunner;

    @Schema(description = "最近执行时间")
    private LocalDateTime lastRunDate;

    @Schema(description = "状态：normal 正常 / blocked 被阻塞", example = "normal")
    private String status;

    @Schema(description = "该 run 的步骤数（执行时按步骤逐条判定）", example = "6")
    private Integer stepCount;

}
