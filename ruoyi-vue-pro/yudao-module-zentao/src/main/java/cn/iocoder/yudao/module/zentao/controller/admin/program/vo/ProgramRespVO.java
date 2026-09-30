package cn.iocoder.yudao.module.zentao.controller.admin.program.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 项目集 Response VO")
@Data
public class ProgramRespVO {

    @Schema(description = "项目集编号", example = "9001")
    private Long id;

    @Schema(description = "上级项目集", example = "0")
    private Long parent;

    @Schema(description = "上级项目集名称", example = "禅道迁移项目集")
    private String parentName;

    @Schema(description = "名称", example = "禅道迁移项目集")
    private String name;

    @Schema(description = "代号", example = "ZENTAO-PGM")
    private String code;

    @Schema(description = "状态", example = "doing")
    private String status;

    @Schema(description = "负责人", example = "admin")
    private String PM;

    @Schema(description = "预算", example = "1000000.00")
    private BigDecimal budget;

    @Schema(description = "预算币种", example = "CNY")
    private String budgetUnit;

    @Schema(description = "层级，顶级项目集为 1", example = "1")
    private Integer grade;

    @Schema(description = "层级路径，逗号格式且包含自己", example = ",9001,")
    private String path;

    @Schema(description = "计划开始", example = "2026-01-01")
    private LocalDate begin;

    @Schema(description = "计划结束", example = "2026-12-31")
    private LocalDate end;

    @Schema(description = "实际开始")
    private LocalDate realBegan;

    @Schema(description = "实际结束")
    private LocalDate realEnd;

    @Schema(description = "优先级", example = "1")
    private Integer pri;

    @Schema(description = "访问控制", example = "open")
    private String acl;

    @Schema(description = "描述")
    private String desc;

    // ==================== 统计（读时现算） ====================

    @Schema(description = "下级项目集数量", example = "1")
    private Integer childCount;

    @Schema(description = "项目数量（parent 指向本项目的项目）", example = "2")
    private Integer projectCount;

    @Schema(description = "产品数量（zt_product.program 指向本项目集）", example = "1")
    private Integer productCount;

    @Schema(description = "创建人", example = "admin")
    private String openedBy;

    @Schema(description = "创建时间")
    private LocalDateTime openedDate;

    @Schema(description = "最后修改人", example = "admin")
    private String lastEditedBy;

    @Schema(description = "最后修改时间")
    private LocalDateTime lastEditedDate;

}
