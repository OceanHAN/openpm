package cn.iocoder.yudao.module.zentao.controller.admin.release.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 发布关联需求/Bug Request VO
 *
 * <p>对应禅道 {@code linkStory} / {@code linkBug}。Bug 分两类：
 * {@code bug} 是本次解决的，{@code leftBug} 是**遗留**的（带着上线的已知问题）。
 */
@Schema(description = "管理后台 - 发布关联需求/Bug Request VO")
@Data
public class ReleaseLinkReqVO {

    @Schema(description = "发布编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long release;

    @Schema(description = "要关联的需求/Bug 编号列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1,2]")
    @NotEmpty(message = "请至少选择一条数据")
    private List<Long> ids;

    @Schema(description = "Bug 类型：bug 本次解决 / leftBug 遗留。仅 link-bug 接口使用", example = "bug")
    private String type;

}
