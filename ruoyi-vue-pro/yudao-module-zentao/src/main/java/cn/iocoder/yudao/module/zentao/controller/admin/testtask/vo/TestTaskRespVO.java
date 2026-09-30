package cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Schema(description = "管理后台 - 测试单 Response VO")
@Data
public class TestTaskRespVO {

    @Schema(description = "测试单编号", example = "94101")
    private Long id;

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "90001")
    private Long execution;

    @Schema(description = "所属构建", example = "1")
    private Long build;

    @Schema(description = "构建名称", example = "V1.0")
    private String buildName;

    @Schema(description = "名称", example = "V1.0 冒烟测试")
    private String name;

    @Schema(description = "类型", example = "feature")
    private String type;

    @Schema(description = "负责人", example = "admin")
    private String owner;

    @Schema(description = "优先级", example = "1")
    private Integer pri;

    @Schema(description = "计划开始日期")
    private LocalDate begin;

    @Schema(description = "计划结束日期")
    private LocalDate end;

    @Schema(description = "实际开始日期")
    private LocalDate realBegan;

    @Schema(description = "实际完成时间")
    private LocalDateTime realFinishedDate;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "测试总结")
    private String report;

    @Schema(description = "状态", example = "doing")
    private String status;

    @Schema(description = "状态文案", example = "进行中")
    private String statusName;

    @Schema(description = "用例总数", example = "3")
    private Long caseCount;

    @Schema(description = "已执行数（有执行结果的）", example = "2")
    private Long runCount;

    @Schema(description = "通过数", example = "1")
    private Long passCount;

    @Schema(description = "失败数", example = "1")
    private Long failCount;

    @Schema(description = "阻塞数", example = "0")
    private Long blockedCount;

    @Schema(description = "未执行数", example = "1")
    private Long unexecutedCount;

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

}
