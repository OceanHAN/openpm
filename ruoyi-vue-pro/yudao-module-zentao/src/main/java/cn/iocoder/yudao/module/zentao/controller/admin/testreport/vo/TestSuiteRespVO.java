package cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 用例集 Response VO")
@Data
public class TestSuiteRespVO {

    @Schema(description = "用例集编号", example = "95101")
    private Long id;

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "名称", example = "冒烟用例集")
    private String name;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "类型", example = "public")
    private String type;

    @Schema(description = "排序", example = "1")
    private Integer order;

    @Schema(description = "集合内用例数", example = "2")
    private Long caseCount;

    @Schema(description = "创建人", example = "admin")
    private String addedBy;

    @Schema(description = "创建时间")
    private LocalDateTime addedDate;

    @Schema(description = "最后修改人", example = "admin")
    private String lastEditedBy;

    @Schema(description = "最后修改时间")
    private LocalDateTime lastEditedDate;

}
