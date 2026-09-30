package cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 测试单新增/修改 Request VO")
@Data
public class TestTaskSaveReqVO {

    @Schema(description = "测试单编号（修改时必填）", example = "94101")
    private Long id;

    @Schema(description = "所属产品", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属产品不能为空")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "90001")
    private Long execution;

    @Schema(description = "所属构建（测的是哪个包）", example = "1")
    private Long build;

    @Schema(description = "测试单名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "V1.0 冒烟测试")
    @NotBlank(message = "测试单名称不能为空")
    @Size(max = 255, message = "名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "类型，逗号列表", example = "feature,interface")
    private String type;

    @Schema(description = "负责人", example = "admin")
    private String owner;

    @Schema(description = "优先级", example = "1")
    private Integer pri;

    @Schema(description = "计划开始日期", example = "2026-03-01")
    private LocalDate begin;

    @Schema(description = "计划结束日期", example = "2026-03-10")
    private LocalDate end;

    @Schema(description = "描述", example = "覆盖登录与下单主流程")
    private String desc;

}
