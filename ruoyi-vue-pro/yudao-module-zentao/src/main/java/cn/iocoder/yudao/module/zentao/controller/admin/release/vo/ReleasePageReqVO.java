package cn.iocoder.yudao.module.zentao.controller.admin.release.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 发布分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ReleasePageReqVO extends PageParam {

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "分支/平台。发布可以覆盖多个分支，匹配规则是「包含」", example = "0")
    private Long branch;

    @Schema(description = "发布版本号，模糊匹配", example = "V1")
    private String name;

    @Schema(description = "状态：wait/normal/fail/terminate", example = "normal")
    private String status;

}
