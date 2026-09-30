package cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 测试用例分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CasePageReqVO extends PageParam {

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属用例库（用例库用例：lib>0、product=0）。不传时只查产品用例",
            example = "95301")
    private Long lib;

    @Schema(description = "所属分支/平台", example = "0")
    private Long branch;

    @Schema(description = "所属模块（**含子孙模块**）", example = "93301")
    private Long module;

    @Schema(description = "关联需求", example = "1")
    private Long story;

    @Schema(description = "标题（模糊）", example = "登录")
    private String title;

    @Schema(description = "关键词（模糊）", example = "冒烟")
    private String keywords;

    @Schema(description = "类型", example = "feature")
    private String type;

    @Schema(description = "测试环节（逗号列表里包含它即命中）", example = "smoke")
    private String stage;

    @Schema(description = "状态", example = "normal")
    private String status;

    @Schema(description = "优先级", example = "1")
    private Integer pri;

    @Schema(description = "只看「待确认」：关联需求已升版且需求仍激活（禅道的 needconfirm）", example = "false")
    private Boolean needConfirm;

    @Schema(description = "创建人（openedBy）")
    private String openedBy;

    @Schema(description = "评审人（reviewedBy，逗号分隔字段，按 FIND_IN_SET 匹配）")
    private String reviewedBy;

}
