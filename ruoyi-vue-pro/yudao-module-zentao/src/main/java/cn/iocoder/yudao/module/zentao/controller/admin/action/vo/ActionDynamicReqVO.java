package cn.iocoder.yudao.module.zentao.controller.admin.action.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 动态（feed）查询 Request VO")
@Data
public class ActionDynamicReqVO {

    @Schema(description = "只看某个人（不传=所有人）", example = "admin")
    private String actor;

    @Schema(description = "周期：today 今天 / yesterday 昨天 / thisWeek 本周 / thisMonth 本月 / all 全部",
            example = "thisWeek")
    private String period;

    @Schema(description = "按产品过滤", example = "1")
    private Long product;

    @Schema(description = "按项目过滤", example = "1")
    private Long project;

    @Schema(description = "按执行过滤", example = "90001")
    private Long execution;

    @Schema(description = "最多取多少条，默认 50（最多 200）", example = "50")
    private Integer limit;

}
