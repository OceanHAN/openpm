package cn.iocoder.yudao.module.zentao.controller.admin.build.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 构建分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class BuildPageReqVO extends PageParam {

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "90001")
    private Long execution;

    @Schema(description = "分支/平台。构建可以覆盖多个分支，匹配规则是「包含」", example = "1")
    private Long branch;

    @Schema(description = "构建名称，模糊匹配", example = "V1.0")
    private String name;

    @Schema(description = "构建者", example = "admin")
    private String builder;

}
