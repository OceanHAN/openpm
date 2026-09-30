package cn.iocoder.yudao.module.zentao.controller.admin.story.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 需求创建/修改 Request VO")
@Data
public class StorySaveReqVO {

    @Schema(description = "需求编号，新建时为空", example = "1024")
    private Long id;

    @Schema(description = "所属产品", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "所属产品不能为空")
    private Long product;

    @Schema(description = "所属模块", example = "0")
    private Long module;

    @Schema(description = "所属计划，多个用逗号分隔", example = "1,2")
    private String plan;

    @Schema(description = "所属分支", example = "0")
    private Long branch;

    @Schema(description = "父需求（新建子需求时用；0 表示一级需求）", example = "4")
    private Long parent;

    @Schema(description = "需求标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "支持需求批量导入")
    @NotBlank(message = "需求标题不能为空")
    @Size(max = 255, message = "需求标题长度不能超过 255 个字符")
    private String title;

    @Schema(description = "关键词", example = "导入,批量")
    @Size(max = 255, message = "关键词长度不能超过 255 个字符")
    private String keywords;

    @Schema(description = "需求描述", example = "作为产品经理，我希望……")
    private String spec;

    @Schema(description = "验收标准", example = "1. 支持 Excel 导入；2. 校验必填项")
    private String verify;

    @Schema(description = "需求类型", example = "story")
    private String type;

    @Schema(description = "需求分类，参见 StoryCategoryEnum", example = "feature")
    private String category;

    @Schema(description = "优先级，1~4，越小越高", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    @NotNull(message = "优先级不能为空")
    @Min(value = 1, message = "优先级最小为 1")
    @Max(value = 4, message = "优先级最大为 4")
    private Integer pri;

    @Schema(description = "预计工时", example = "8.00")
    private BigDecimal estimate;

    @Schema(description = "需求来源", example = "customer")
    private String source;

    @Schema(description = "来源备注", example = "来自 XX 客户反馈")
    @Size(max = 255, message = "来源备注长度不能超过 255 个字符")
    private String sourceNote;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

}
