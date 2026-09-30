package cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 步骤执行结果")
@Data
public class TestStepResultVO {

    @Schema(description = "步骤编号（zt_casestep.id）", requiredMode = Schema.RequiredMode.REQUIRED, example = "93154")
    @NotNull(message = "步骤编号不能为空")
    private Long id;

    @Schema(description = "该步骤结果：pass/fail/blocked/n-a", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "pass")
    private String result;

    @Schema(description = "该步骤备注", example = "符合预期")
    private String remark;

}
