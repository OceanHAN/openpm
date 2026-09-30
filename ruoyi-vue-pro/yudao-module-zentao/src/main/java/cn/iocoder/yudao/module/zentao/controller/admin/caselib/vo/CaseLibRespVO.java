package cn.iocoder.yudao.module.zentao.controller.admin.caselib.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 用例库 Response VO")
@Data
public class CaseLibRespVO {

    @Schema(description = "用例库编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "95301")
    private Long id;

    @Schema(description = "用例库名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "公共用例库")
    private String name;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "排序", example = "1")
    private Integer order;

    @Schema(description = "类型：固定为 library（与用例集共用 zt_testsuite）", example = "library")
    private String type;

    @Schema(description = "库内用例数量", example = "2")
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
