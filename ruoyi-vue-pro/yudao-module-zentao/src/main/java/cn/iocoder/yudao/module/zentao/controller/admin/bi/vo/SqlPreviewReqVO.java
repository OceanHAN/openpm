package cn.iocoder.yudao.module.zentao.controller.admin.bi.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - SQL 试跑 Request VO")
@Data
public class SqlPreviewReqVO {

    @Schema(description = "待试跑的只读 SQL（单条 SELECT，只能查 zt_* 表）",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "SELECT id, status FROM zt_story WHERE deleted = 0")
    @NotBlank(message = "SQL 不能为空")
    private String sql;

    @Schema(description = "取多少行，默认 20（最多 200）", example = "20")
    private Integer limit;

}
