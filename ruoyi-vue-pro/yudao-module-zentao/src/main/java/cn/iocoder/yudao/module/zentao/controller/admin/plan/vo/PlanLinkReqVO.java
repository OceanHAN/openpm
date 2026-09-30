package cn.iocoder.yudao.module.zentao.controller.admin.plan.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 计划关联需求/Bug Request VO
 *
 * <p>对应禅道 {@code linkStory} / {@code linkBug}：一次可以勾选多个对象关联到计划上。
 */
@Schema(description = "管理后台 - 计划关联需求/Bug Request VO")
@Data
public class PlanLinkReqVO {

    @Schema(description = "计划编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long plan;

    @Schema(description = "要关联的需求/Bug 编号列表", requiredMode = Schema.RequiredMode.REQUIRED, example = "[1,2]")
    @NotEmpty(message = "请至少选择一条数据")
    private List<Long> ids;

}
