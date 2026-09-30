package cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 执行用例 Request VO")
@Data
public class TestRunCaseReqVO {

    @Schema(description = "执行记录编号（runId，来自 run-list）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "94151")
    @NotNull(message = "执行记录编号不能为空")
    private Long runId;

    @Schema(description = "步骤结果。**用例级结果由它算出来**：默认 pass，遇到非 pass/n-a 就以它为准，"
            + "遇到 fail 直接结束", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "执行用例必须提供步骤结果")
    private List<TestStepResultVO> stepResults;

}
