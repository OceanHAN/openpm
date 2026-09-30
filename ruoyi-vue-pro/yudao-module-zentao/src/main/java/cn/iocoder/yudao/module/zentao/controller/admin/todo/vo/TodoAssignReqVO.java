package cn.iocoder.yudao.module.zentao.controller.admin.todo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 待办指派 Request VO")
@Data
public class TodoAssignReqVO {

    @Schema(description = "待办编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "97103")
    @NotNull(message = "待办编号不能为空")
    private Long id;

    @Schema(description = "指派给", requiredMode = Schema.RequiredMode.REQUIRED, example = "tester")
    @NotBlank(message = "指派人不能为空")
    private String assignedTo;

}
