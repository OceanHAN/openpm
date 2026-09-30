package cn.iocoder.yudao.module.zentao.controller.admin.branch.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 分支分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class BranchPageReqVO extends PageParam {

    @Schema(description = "所属产品", example = "2")
    private Long product;

    @Schema(description = "分支名称，模糊匹配", example = "企业")
    private String name;

    @Schema(description = "状态：active/closed", example = "active")
    private String status;

}
