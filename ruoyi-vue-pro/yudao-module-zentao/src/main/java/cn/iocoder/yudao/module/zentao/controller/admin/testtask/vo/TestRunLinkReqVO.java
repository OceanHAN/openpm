package cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 测试单关联用例 Request VO")
@Data
public class TestRunLinkReqVO {

    @Schema(description = "测试单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "94101")
    @NotNull(message = "测试单编号不能为空")
    private Long taskId;

    @Schema(description = "用例编号列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[93101,93102]")
    @NotEmpty(message = "至少要选择一条用例")
    private List<Long> caseIds;

    @Schema(description = "指派给（可选，缺省不指派）", example = "admin")
    private String assignedTo;

}
