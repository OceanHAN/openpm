package cn.iocoder.yudao.module.zentao.controller.admin.repo.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 代码库分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class RepoPageReqVO extends PageParam {

    @Schema(description = "代码库名称（模糊）", example = "zentao")
    private String name;

    @Schema(description = "状态：active/closed", example = "active")
    private String status;

    @Schema(description = "源码管理类型", example = "git")
    private String scmType;

    @Schema(description = "关联产品（模糊匹配逗号列表）", example = "1")
    private String product;

}
