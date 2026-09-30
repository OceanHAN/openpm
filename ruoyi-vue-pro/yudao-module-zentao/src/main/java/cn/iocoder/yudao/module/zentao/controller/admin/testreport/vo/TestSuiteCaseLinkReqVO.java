package cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 用例集关联用例 Request VO")
@Data
public class TestSuiteCaseLinkReqVO {

    @Schema(description = "用例集编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "95101")
    @NotNull(message = "用例集编号不能为空")
    private Long suiteId;

    @Schema(description = "用例编号列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[93101,93102]")
    @NotEmpty(message = "至少要选择一条用例")
    private List<Long> caseIds;

}
