package cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 从用例执行结果建缺陷 Request VO")
@Data
public class TestRunBugReqVO {

    @Schema(description = "执行记录编号（runId）", requiredMode = Schema.RequiredMode.REQUIRED, example = "94152")
    @NotNull(message = "执行记录编号不能为空")
    private Long runId;

    @Schema(description = "失败的那个步骤编号（可选，会写进复现步骤）", example = "93161")
    private Long stepId;

    @Schema(description = "缺陷标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "登录接口返回 500")
    @NotBlank(message = "缺陷标题不能为空")
    private String title;

    @Schema(description = "严重程度 1-4", example = "2")
    private Integer severity;

    @Schema(description = "优先级 1-4", example = "2")
    private Integer pri;

    @Schema(description = "缺陷类型：code/data/performance/standard/security/install/others", example = "code")
    private String type;

    @Schema(description = "复现步骤。**不填会自动用「用例步骤 + 失败步骤」生成**")
    private String steps;

    @Schema(description = "操作系统", example = "all")
    private String os;

    @Schema(description = "浏览器", example = "all")
    private String browser;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

    @Schema(description = "影响版本（构建编号，逗号列表）", example = "2")
    private String openedBuild;

    @Schema(description = "关键词", example = "登录")
    private String keywords;

    @Schema(description = "严重程度为 1 时必须填的说明", example = "影响主流程")
    private String severityRemark;

}
