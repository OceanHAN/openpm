package cn.iocoder.yudao.module.zentao.controller.admin.build.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "管理后台 - 构建创建/修改 Request VO")
@Data
public class BuildSaveReqVO {

    @Schema(description = "构建编号，新建时为空", example = "1")
    private Long id;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属产品。非集成构建必填（项目未关联产品时可不填）", example = "1")
    private Long product;

    @Schema(description = "分支/平台。多分支产品必填，可多选", example = "[1,2]")
    private List<Long> branches;

    @Schema(description = "所属执行。集成构建不用填（后端会置 0）", example = "90001")
    private Long execution;

    @Schema(description = "是否集成构建。集成构建的 branch 由子构建推导，execution 固定为 0", example = "false")
    private Boolean integrated;

    @Schema(description = "集成构建包含的子构建编号", example = "[1,2]")
    private List<Long> builds;

    @Schema(description = "构建名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "V1.0-beta1")
    @NotBlank(message = "构建名称不能为空")
    @Size(max = 150, message = "构建名称长度不能超过 150 个字符")
    private String name;

    @Schema(description = "打包日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-01-20")
    @NotNull(message = "打包日期不能为空")
    private LocalDate date;

    @Schema(description = "构建者", requiredMode = Schema.RequiredMode.REQUIRED, example = "admin")
    @NotBlank(message = "构建者不能为空")
    private String builder;

    @Schema(description = "源代码地址")
    private String scmPath;

    @Schema(description = "下载地址")
    private String filePath;

    @Schema(description = "描述")
    private String desc;

}
