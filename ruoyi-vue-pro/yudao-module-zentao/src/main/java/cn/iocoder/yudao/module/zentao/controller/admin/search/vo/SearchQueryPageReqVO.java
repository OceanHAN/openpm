package cn.iocoder.yudao.module.zentao.controller.admin.search.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 保存查询分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchQueryPageReqVO extends PageParam {

    @Schema(description = "账号（不传=当前登录账号）", example = "admin")
    private String account;

    @Schema(description = "模块", example = "story")
    private String module;

    @Schema(description = "查询名称（模糊）", example = "激活")
    private String title;

    @Schema(description = "是否快捷方式", example = "1")
    private Integer shortcut;

}
