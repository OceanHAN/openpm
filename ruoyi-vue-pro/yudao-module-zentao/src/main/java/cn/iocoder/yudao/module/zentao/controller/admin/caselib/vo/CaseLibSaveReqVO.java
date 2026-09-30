package cn.iocoder.yudao.module.zentao.controller.admin.caselib.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 用例库新增/修改 Request VO")
@Data
public class CaseLibSaveReqVO {

    @Schema(description = "用例库编号（修改时必填）", example = "95301")
    private Long id;

    @Schema(description = "用例库名称（全局唯一，禅道 caselib/create 的 check('name','unique','deleted=0')）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "公共用例库")
    @NotBlank(message = "用例库名称不能为空")
    @Size(max = 255, message = "用例库名称长度不能超过 255 个字符")
    private String name;

    @Schema(description = "描述", example = "跨产品复用的基础用例")
    private String desc;

    @Schema(description = "排序", example = "1")
    private Integer order;

}
