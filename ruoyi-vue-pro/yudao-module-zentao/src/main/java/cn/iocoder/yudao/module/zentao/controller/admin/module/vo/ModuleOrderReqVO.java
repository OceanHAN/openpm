package cn.iocoder.yudao.module.zentao.controller.admin.module.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 模块排序 Request VO
 *
 * <p>禅道 {@code updateOrder()} 接收「模块编号 → 排序值」的映射。
 */
@Schema(description = "管理后台 - 模块排序 Request VO")
@Data
public class ModuleOrderReqVO {

    @Schema(description = "排序项列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "排序项不能为空")
    private List<Item> items;

    @Schema(description = "单个排序项")
    @Data
    public static class Item {

        @Schema(description = "模块编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
        @NotNull(message = "模块编号不能为空")
        private Long id;

        @Schema(description = "排序值", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
        @NotNull(message = "排序值不能为空")
        private Integer order;

    }

}
