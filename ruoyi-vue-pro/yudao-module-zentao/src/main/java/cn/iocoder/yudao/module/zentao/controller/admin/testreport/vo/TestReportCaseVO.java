package cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 测试报告里的按用例汇总行")
@Data
public class TestReportCaseVO {

    @Schema(description = "用例编号", example = "93101")
    private Long caseId;

    @Schema(description = "用例标题", example = "正常登录-用户名密码正确")
    private String caseTitle;

    @Schema(description = "执行次数（范围内）", example = "2")
    private Integer runCount;

    @Schema(description = "最后一次结果", example = "pass")
    private String lastResult;

    @Schema(description = "最后一次结果文案", example = "通过")
    private String lastResultName;

    @Schema(description = "最后执行人", example = "admin")
    private String lastRunner;

    @Schema(description = "最后执行时间")
    private LocalDateTime lastRunDate;

}
