package cn.iocoder.yudao.module.zentao.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Schema(description = "管理后台 - 项目 Response VO")
@Data
public class ProjectRespVO {

    @Schema(description = "项目编号", example = "1")
    private Long id;

    @Schema(description = "所属项目。执行专用", example = "0")
    private Long project;

    @Schema(description = "父项目", example = "0")
    private Long parent;

    @Schema(description = "层级路径")
    private String path;

    @Schema(description = "层级深度", example = "0")
    private Integer grade;

    @Schema(description = "模型", example = "scrum")
    private String model;

    @Schema(description = "项目类型")
    private String type;

    @Schema(description = "项目分类")
    private String category;

    @Schema(description = "项目名称", example = "禅道迁移一期")
    private String name;

    @Schema(description = "项目代号", example = "ZENTAO-P1")
    private String code;

    @Schema(description = "项目描述")
    private String desc;

    @Schema(description = "交付物")
    private String output;

    @Schema(description = "是否关联产品", example = "1")
    private Integer hasProduct;

    @Schema(description = "是否多执行", example = "0")
    private Integer multiple;

    @Schema(description = "预算", example = "100000.00")
    private BigDecimal budget;

    @Schema(description = "预算币种", example = "CNY")
    private String budgetUnit;

    @Schema(description = "计划开始")
    private LocalDate begin;

    @Schema(description = "计划结束")
    private LocalDate end;

    @Schema(description = "实际开始")
    private LocalDate realBegan;

    @Schema(description = "实际结束")
    private LocalDate realEnd;

    @Schema(description = "可用工作日", example = "20")
    private Integer days;

    @Schema(description = "状态", example = "doing")
    private String status;

    @Schema(description = "优先级", example = "3")
    private Integer pri;

    @Schema(description = "是否里程碑", example = "0")
    private Integer milestone;

    @Schema(description = "预计工时", example = "200.00")
    private BigDecimal estimate;

    @Schema(description = "剩余工时", example = "150.00")
    private BigDecimal left;

    @Schema(description = "已消耗工时", example = "50.00")
    private BigDecimal consumed;

    @Schema(description = "进度百分比", example = "25.00")
    private BigDecimal progress;

    @Schema(description = "阶段工作量占比（瀑布阶段）", example = "30.00")
    private BigDecimal percent;

    @Schema(description = "使用的流程模板编号（瀑布项目 > 0）", example = "1")
    private Long workflowGroup;

    @Schema(description = "产品负责人", example = "admin")
    private String PO;

    @Schema(description = "项目经理", example = "admin")
    private String PM;

    @Schema(description = "测试负责人", example = "admin")
    private String QD;

    @Schema(description = "研发负责人", example = "admin")
    private String RD;

    @Schema(description = "团队成员")
    private String team;

    @Schema(description = "团队人数", example = "2")
    private Integer teamCount;

    @Schema(description = "访问控制", example = "open")
    private String acl;

    @Schema(description = "创建人", example = "admin")
    private String openedBy;

    @Schema(description = "创建时间")
    private LocalDateTime openedDate;

    @Schema(description = "最后修改人", example = "admin")
    private String lastEditedBy;

    @Schema(description = "最后修改时间")
    private LocalDateTime lastEditedDate;

    @Schema(description = "关闭人", example = "admin")
    private String closedBy;

    @Schema(description = "关闭时间")
    private LocalDateTime closedDate;

    @Schema(description = "关闭原因", example = "done")
    private String closedReason;

    @Schema(description = "挂起时间")
    private LocalDateTime suspendedDate;

    @Schema(description = "激活时间")
    private LocalDateTime activatedDate;

    @Schema(description = "排序", example = "0")
    private Integer order;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
