package cn.iocoder.yudao.module.zentao.controller.admin.bug.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - 缺陷创建/修改 Request VO")
@Data
public class BugSaveReqVO {

    @Schema(description = "缺陷编号，新建时为空", example = "1024")
    private Long id;

    @Schema(description = "所属产品", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属产品不能为空")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "1")
    private Long execution;

    @Schema(description = "所属模块", example = "0")
    private Long module;

    @Schema(description = "所属计划", example = "1")
    private Long plan;

    @Schema(description = "所属分支/平台。产品类型决定含义（platform → 平台，branch → 分支）", example = "0")
    private Long branch;

    @Schema(description = "关联需求", example = "0")
    private Long story;

    @Schema(description = "关联任务", example = "0")
    private Long task;

    @Schema(description = "来源用例（从用例执行失败建 Bug 时由测试单回填）", example = "93102")
    private Long caseId;

    @Schema(description = "来源用例的版本（冻结值）", example = "1")
    private Integer caseVersion;

    @Schema(description = "来源测试单", example = "94101")
    private Long testtask;

    @Schema(description = "缺陷标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "登录接口返回 500")
    @NotBlank(message = "缺陷标题不能为空")
    @Size(max = 255, message = "缺陷标题长度不能超过 255 个字符")
    private String title;

    @Schema(description = "关键词")
    private String keywords;

    @Schema(description = "严重程度 1~4", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "严重程度不能为空")
    @Min(value = 1, message = "严重程度最小为 1")
    @Max(value = 4, message = "严重程度最大为 4")
    private Integer severity;

    @Schema(description = "优先级 1~4", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @NotNull(message = "优先级不能为空")
    @Min(value = 1, message = "优先级最小为 1")
    @Max(value = 4, message = "优先级最大为 4")
    private Integer pri;

    @Schema(description = "缺陷类型", example = "codeerror")
    private String type;

    @Schema(description = "操作系统", example = "Windows 11")
    private String os;

    @Schema(description = "浏览器", example = "Chrome 130")
    private String browser;

    @Schema(description = "重现步骤", example = "1. 打开登录页\n2. 输入正确账号密码\n3. 点击登录")
    private String steps;

    @Schema(description = "影响版本", example = "v1.0")
    private String openedBuild;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

    @Schema(description = "截止日期")
    private LocalDate deadline;

}
