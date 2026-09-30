package cn.iocoder.yudao.module.zentao.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 项目创建/修改 Request VO")
@Data
public class ProjectSaveReqVO {

    @Schema(description = "项目编号，新建时为空", example = "1")
    private Long id;

    @Schema(description = "所属项目。执行专用：type 为 sprint/stage/kanban 时指向所属项目；项目自身为 0", example = "0")
    private Long project;

    @Schema(description = "所属项目集。项目靠它归属到项目集（0 = 不属于任何项目集）", example = "9001")
    private Long parent;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "禅道迁移一期")
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 90, message = "项目名称长度不能超过 90 个字符")
    private String name;

    @Schema(description = "项目代号", example = "ZENTAO-P1")
    private String code;

    @Schema(description = "模型：scrum/waterfall/kanban/agileplus/waterfallplus",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "scrum")
    @NotBlank(message = "项目模型不能为空")
    private String model;

    @Schema(description = "项目类型", example = "")
    private String type;

    @Schema(description = "项目分类", example = "")
    private String category;

    @Schema(description = "优先级 1~4", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @NotNull(message = "优先级不能为空")
    @Min(value = 1, message = "优先级最小为 1")
    @Max(value = 4, message = "优先级最大为 4")
    private Integer pri;

    @Schema(description = "是否关联产品。为 0 时关闭项目会连带关闭自动创建的产品", example = "1")
    private Integer hasProduct;

    @Schema(description = "是否多执行。为 0 时关闭项目会连带关闭其执行", example = "0")
    private Integer multiple;

    @Schema(description = "预算", example = "100000.00")
    private BigDecimal budget;

    @Schema(description = "预算币种", example = "CNY")
    private String budgetUnit;

    @Schema(description = "计划开始")
    private LocalDate begin;

    @Schema(description = "计划结束")
    private LocalDate end;

    @Schema(description = "预计工时", example = "200.00")
    private BigDecimal estimate;

    @Schema(description = "剩余工时", example = "200.00")
    private BigDecimal left;

    @Schema(description = "产品负责人", example = "admin")
    private String PO;

    @Schema(description = "项目经理", example = "admin")
    private String PM;

    @Schema(description = "测试负责人", example = "admin")
    private String QD;

    @Schema(description = "研发负责人", example = "admin")
    private String RD;

    @Schema(description = "团队成员，逗号分隔", example = "admin,dev1")
    private String team;

    @Schema(description = "访问控制：open/private", example = "open")
    private String acl;

    @Schema(description = "项目描述")
    private String desc;

    @Schema(description = "排序", example = "0")
    private Integer order;

}
