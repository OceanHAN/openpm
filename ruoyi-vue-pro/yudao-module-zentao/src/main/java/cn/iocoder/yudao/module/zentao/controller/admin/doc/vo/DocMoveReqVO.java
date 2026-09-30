package cn.iocoder.yudao.module.zentao.controller.admin.doc.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 文档移动 Request VO")
@Data
public class DocMoveReqVO {

    @Schema(description = "文档编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "91013")
    @NotNull(message = "文档编号不能为空")
    private Long id;

    @Schema(description = "目标文档库（不换库时传原库）", requiredMode = Schema.RequiredMode.REQUIRED, example = "91001")
    @NotNull(message = "目标文档库不能为空")
    private Long lib;

    @Schema(description = "目标上级章节（0 表示移到库根）", example = "91012")
    private Long parent;

}
