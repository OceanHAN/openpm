package cn.iocoder.yudao.module.zentao.controller.admin.build.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Schema(description = "管理后台 - 构建 Response VO")
@Data
public class BuildRespVO {

    @Schema(description = "构建编号", example = "1")
    private Long id;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "产品名称", example = "禅道研发管理平台")
    private String productName;

    @Schema(description = "分支/平台，逗号列表", example = "0")
    private String branch;

    @Schema(description = "分支/平台名称", example = "主干")
    private String branchName;

    @Schema(description = "所属执行", example = "90001")
    private Long execution;

    @Schema(description = "执行名称", example = "V1.0 迭代")
    private String executionName;

    @Schema(description = "集成构建包含的子构建，逗号列表", example = "1,2")
    private String builds;

    @Schema(description = "子构建名称列表", example = "[\"V1.0-beta1\",\"V1.0-beta2\"]")
    private List<String> buildNames;

    @Schema(description = "是否是集成构建", example = "true")
    private Boolean integrated;

    @Schema(description = "构建名称", example = "V1.0-beta1")
    private String name;

    @Schema(description = "打包日期", example = "2026-01-20")
    private LocalDate date;

    @Schema(description = "构建者", example = "admin")
    private String builder;

    @Schema(description = "源代码地址")
    private String scmPath;

    @Schema(description = "下载地址")
    private String filePath;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "本次完成的需求，逗号列表", example = "1,2")
    private String stories;

    @Schema(description = "本次解决的 Bug，逗号列表（集成构建时含子构建的）", example = "1,2")
    private String bugs;

    @Schema(description = "需求数量（集成构建含子构建）", example = "2")
    private Long storyCount;

    @Schema(description = "Bug 数量（集成构建含子构建）", example = "1")
    private Long bugCount;

    @Schema(description = "是否已被集成构建/发布引用。被引用时不能改产品、执行、子构建", example = "false")
    private Boolean child;

    @Schema(description = "创建人", example = "admin")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdDate;

}
