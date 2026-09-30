package cn.iocoder.yudao.module.zentao.controller.admin.module.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(description = "管理后台 - 模块树类型 Response VO")
@Data
@AllArgsConstructor
public class ModuleTypeRespVO {

    @Schema(description = "类型值", example = "story")
    private String type;

    @Schema(description = "类型名称", example = "需求模块")
    private String name;

}
