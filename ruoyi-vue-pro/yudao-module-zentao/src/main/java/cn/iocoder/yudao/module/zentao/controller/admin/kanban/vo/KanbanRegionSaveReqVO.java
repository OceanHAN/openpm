package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 看板区域新增/修改 Request VO")
@Data
public class KanbanRegionSaveReqVO {

    @Schema(description = "区域编号（修改时必填）", example = "96201")
    private Long id;

    @Schema(description = "所属看板", requiredMode = Schema.RequiredMode.REQUIRED, example = "96101")
    @NotNull(message = "所属看板不能为空")
    private Long kanban;

    @Schema(description = "区域名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "默认区域")
    @NotBlank(message = "区域名称不能为空")
    @Size(max = 255, message = "区域名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "排序", example = "1")
    private Integer order;

}
