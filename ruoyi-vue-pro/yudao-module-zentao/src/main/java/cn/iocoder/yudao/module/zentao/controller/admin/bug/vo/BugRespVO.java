package cn.iocoder.yudao.module.zentao.controller.admin.bug.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Schema(description = "管理后台 - 缺陷信息 Response VO")
@Data
public class BugRespVO {

    @Schema(description = "缺陷编号", example = "1024")
    private Long id;

    @Schema(description = "所属产品", example = "1")
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

    @Schema(description = "来源用例（用例执行失败建的缺陷会有值）", example = "93102")
    private Long caseId;

    @Schema(description = "来源用例的版本（冻结值：用例后来改了步骤，这里仍然指向当初跑的那一版）", example = "1")
    private Integer caseVersion;

    @Schema(description = "来源测试单", example = "94101")
    private Long testtask;

    @Schema(description = "缺陷标题", example = "登录接口返回 500")
    private String title;

    @Schema(description = "严重程度", example = "2")
    private Integer severity;

    @Schema(description = "优先级", example = "3")
    private Integer pri;

    @Schema(description = "缺陷类型", example = "codeerror")
    private String type;

    @Schema(description = "操作系统", example = "Windows 11")
    private String os;

    @Schema(description = "浏览器", example = "Chrome 130")
    private String browser;

    @Schema(description = "重现步骤")
    private String steps;

    @Schema(description = "状态", example = "active")
    private String status;

    @Schema(description = "是否已确认", example = "0")
    private Integer confirmed;

    @Schema(description = "激活次数", example = "0")
    private Integer activatedCount;

    @Schema(description = "最后激活时间")
    private LocalDateTime activatedDate;

    @Schema(description = "创建人", example = "admin")
    private String openedBy;

    @Schema(description = "创建时间")
    private LocalDateTime openedDate;

    @Schema(description = "影响版本", example = "v1.0")
    private String openedBuild;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

    @Schema(description = "指派时间")
    private LocalDateTime assignedDate;

    @Schema(description = "截止日期")
    private LocalDate deadline;

    @Schema(description = "解决人", example = "admin")
    private String resolvedBy;

    @Schema(description = "解决方案", example = "fixed")
    private String resolution;

    @Schema(description = "解决版本", example = "v1.1")
    private String resolvedBuild;

    @Schema(description = "解决时间")
    private LocalDateTime resolvedDate;

    @Schema(description = "关闭人", example = "admin")
    private String closedBy;

    @Schema(description = "关闭时间")
    private LocalDateTime closedDate;

    @Schema(description = "重复缺陷编号", example = "0")
    private Long duplicateBug;

    @Schema(description = "最后修改人", example = "admin")
    private String lastEditedBy;

    @Schema(description = "最后修改时间")
    private LocalDateTime lastEditedDate;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
