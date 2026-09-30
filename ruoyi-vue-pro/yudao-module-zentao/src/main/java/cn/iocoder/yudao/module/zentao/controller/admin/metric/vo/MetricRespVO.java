package cn.iocoder.yudao.module.zentao.controller.admin.metric.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 度量项 Response VO")
@Data
public class MetricRespVO {

    @Schema(description = "度量项编号", example = "1")
    private Long id;

    @Schema(description = "目的", example = "scale")
    private String purpose;

    @Schema(description = "目的名", example = "规模估算")
    private String purposeName;

    @Schema(description = "范围", example = "product")
    private String scope;

    @Schema(description = "范围名", example = "产品")
    private String scopeName;

    @Schema(description = "对象", example = "story")
    private String object;

    @Schema(description = "对象名", example = "故事")
    private String objectName;

    @Schema(description = "状态", example = "released")
    private String stage;

    @Schema(description = "实现方式", example = "php")
    private String type;

    @Schema(description = "度量名称", example = "按产品统计的研发需求总数")
    private String name;

    @Schema(description = "别名", example = "研发需求总数")
    private String alias;

    @Schema(description = "度量项代码", example = "count_of_story_in_product")
    private String code;

    @Schema(description = "单位", example = "个")
    private String unit;

    @Schema(description = "单位名", example = "个")
    private String unitName;

    @Schema(description = "时间维度", example = "nodate")
    private String dateType;

    @Schema(description = "时间维度名", example = "快照")
    private String dateTypeName;

    @Schema(description = "描述")
    private String desc;

    @Schema(description = "口径定义")
    private String definition;

    @Schema(description = "是否内置", example = "true")
    private Boolean builtin;

    @Schema(description = "排序", example = "1")
    private Integer order;

    @Schema(description = "上次计算出的记录数", example = "3")
    private Integer lastCalcRows;

    @Schema(description = "上次计算时间")
    private LocalDateTime lastCalcTime;

    @Schema(description = "库里已有的数据条数", example = "3")
    private Long dataCount;

    @Schema(description = "口径是否已迁移（Java 里能算）", example = "true")
    private Boolean implemented;

}
