package cn.iocoder.yudao.module.zentao.controller.admin.company.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 公司分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CompanyPageReqVO extends PageParam {

    @Schema(description = "公司名称（模糊）", example = "禅道")
    private String name;

    @Schema(description = "是否允许匿名登录", example = "0")
    private Integer guest;

}
