package cn.iocoder.yudao.module.zentao.controller.admin.execution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 执行创建/修改 Request VO
 *
 * <h3>为什么不复用 ProjectSaveReqVO</h3>
 * 执行和项目共用 {@code zt_project} 表，字段几乎一致，一开始复用了 {@link
 * cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectSaveReqVO}。
 * 但实测发现复用会带来两个问题：
 * <ol>
 *   <li>项目 VO 上 {@code model}（模型）是 {@code @NotBlank}，而执行没有自己的模型，
 *       是从所属项目继承的。复用时调用方被迫传一个无意义的 model，
 *       否则接口直接报「项目模型不能为空」。</li>
 *   <li>项目 VO 允许传 {@code parent}，而执行不参与项目层级（parent 恒为 0），
 *       暴露出去容易让调用方误用。</li>
 * </ol>
 * 所以这里按「执行的语义」单独定义 VO：只保留执行真正需要的字段，
 * {@code project}（所属项目）是必填，{@code model} 变为可选（缺省继承父项目）。
 */
@Schema(description = "管理后台 - 执行创建/修改 Request VO")
@Data
public class ExecutionSaveReqVO {

    @Schema(description = "执行编号，新建时为空", example = "10")
    private Long id;

    @Schema(description = "所属项目（必填，且必须是 type=project 的记录）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属项目不能为空")
    private Long project;

    @Schema(description = "执行名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "第 1 迭代")
    @NotBlank(message = "执行名称不能为空")
    @Size(max = 90, message = "执行名称长度不能超过 90 个字符")
    private String name;

    @Schema(description = "执行类型：sprint/阶段 stage/看板 kanban",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "sprint")
    @NotBlank(message = "执行类型不能为空")
    private String type;

    @Schema(description = "执行代号", example = "SPRINT-1")
    private String code;

    @Schema(description = "执行分类", example = "")
    private String category;

    @Schema(description = "项目模型。执行没有自己的模型，缺省继承所属项目，一般不用传", example = "scrum")
    private String model;

    @Schema(description = "优先级 1~4", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @NotNull(message = "优先级不能为空")
    @Min(value = 1, message = "优先级最小为 1")
    @Max(value = 4, message = "优先级最大为 4")
    private Integer pri;

    @Schema(description = "计划开始")
    private LocalDate begin;

    @Schema(description = "计划结束")
    private LocalDate end;

    @Schema(description = "预计工时", example = "80.00")
    private BigDecimal estimate;

    @Schema(description = "剩余工时。不传时默认等于预计工时", example = "80.00")
    private BigDecimal left;

    @Schema(description = "负责人", example = "admin")
    private String PM;

    @Schema(description = "产品负责人", example = "admin")
    private String PO;

    @Schema(description = "测试负责人", example = "admin")
    private String QD;

    @Schema(description = "研发负责人", example = "admin")
    private String RD;

    @Schema(description = "团队成员，逗号分隔", example = "admin,dev1")
    private String team;

    @Schema(description = "访问控制：open/private", example = "open")
    private String acl;

    @Schema(description = "交付物")
    private String output;

    @Schema(description = "执行描述")
    private String desc;

    @Schema(description = "排序", example = "0")
    private Integer order;

}
