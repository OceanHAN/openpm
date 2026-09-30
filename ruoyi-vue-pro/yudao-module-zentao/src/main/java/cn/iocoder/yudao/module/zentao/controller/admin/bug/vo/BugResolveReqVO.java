package cn.iocoder.yudao.module.zentao.controller.admin.bug.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 缺陷解决 Request VO")
@Data
public class BugResolveReqVO {

    @Schema(description = "缺陷编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "缺陷编号不能为空")
    private Long id;

    /**
     * 禅道的联动校验（见 BugServiceImpl）：
     * duplicate 必须给 duplicateBug 且目标要存在；fixed 必须给 resolvedBuild。
     */
    @Schema(description = "解决方案：bydesign/duplicate/external/fixed/notrepro/postponed/willnotfix/tostory",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "fixed")
    @NotBlank(message = "解决方案不能为空")
    private String resolution;

    @Schema(description = "解决版本，解决方案为 fixed 时必填", example = "v1.1")
    private String resolvedBuild;

    @Schema(description = "重复缺陷编号，解决方案为 duplicate 时必填", example = "2048")
    private Long duplicateBug;

    @Schema(description = "解决说明", example = "修复了空指针")
    private String comment;

}
