package cn.iocoder.yudao.module.zentao.controller.admin.metric.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 度量项分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MetricPageReqVO extends PageParam {

    @Schema(description = "度量目的：scale/qc/hour/cost/rate/time", example = "scale")
    private String purpose;

    @Schema(description = "度量范围：system/program/project/product/execution/user", example = "product")
    private String scope;

    @Schema(description = "度量对象：story/bug/case/task/effort/...", example = "story")
    private String object;

    @Schema(description = "状态：wait 未发布 / released 已发布 / delisted 已下架", example = "released")
    private String stage;

    @Schema(description = "只看已迁移口径（Java 里实现了计算的）", example = "true")
    private Boolean onlyImplemented;

    @Schema(description = "名称/别名/代码 关键词", example = "需求")
    private String keyword;

}
