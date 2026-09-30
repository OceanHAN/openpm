package cn.iocoder.yudao.module.zentao.controller.admin.plan.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 产品计划分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class PlanPageReqVO extends PageParam {

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "分支/平台。计划可以覆盖多个分支，匹配规则是「包含」", example = "1")
    private Long branch;

    @Schema(description = "计划名称，模糊匹配", example = "V1.0")
    private String title;

    @Schema(description = "状态：wait/doing/done/closed", example = "doing")
    private String status;

    @Schema(description = "父计划编号。传 0 只查独立计划，-1 查有子计划的父计划", example = "0")
    private Long parent;

}
