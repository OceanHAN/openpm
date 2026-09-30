package cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;


@Schema(description = "管理后台 - 测试执行结果 Response VO")
@Data
public class TestResultRespVO {

    @Schema(description = "结果编号", example = "94201")
    private Long id;

    @Schema(description = "执行记录编号", example = "94151")
    private Long run;

    @Schema(description = "用例编号", example = "93101")
    private Long caseId;

    @Schema(description = "用例版本", example = "2")
    private Integer version;

    @Schema(description = "用例级结果", example = "pass")
    private String caseResult;

    @Schema(description = "用例级结果文案", example = "通过")
    private String caseResultName;

    @Schema(description = "步骤级结果（JSON 原文）")
    private String stepResults;

    @Schema(description = "执行人", example = "admin")
    private String lastRunner;

    @Schema(description = "执行时间")
    private LocalDateTime date;

}
