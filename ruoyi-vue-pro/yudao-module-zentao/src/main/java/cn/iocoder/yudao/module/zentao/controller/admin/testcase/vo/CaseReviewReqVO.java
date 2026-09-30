package cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 用例评审 Request VO")
@Data
public class CaseReviewReqVO {

    @Schema(description = "用例编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "93103")
    @NotNull(message = "用例编号不能为空")
    private Long id;

    @Schema(description = "评审结果：normal 正常 / blocked 被阻塞 / investigate 研究中",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "normal")
    @NotBlank(message = "评审结果不能为空")
    private String result;

    @Schema(description = "评审意见", example = "步骤完整，通过")
    private String comment;

}
