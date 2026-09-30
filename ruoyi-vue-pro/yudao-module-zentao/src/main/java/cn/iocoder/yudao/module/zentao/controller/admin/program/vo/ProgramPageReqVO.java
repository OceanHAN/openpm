package cn.iocoder.yudao.module.zentao.controller.admin.program.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 项目集分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProgramPageReqVO extends PageParam {

    @Schema(description = "名称，模糊匹配", example = "迁移")
    private String name;

    @Schema(description = "代号，模糊匹配", example = "ZENTAO")
    private String code;

    @Schema(description = "状态：wait/doing/suspended/closed", example = "doing")
    private String status;

    @Schema(description = "上级项目集，0 表示只看顶级", example = "0")
    private Long parent;

    @Schema(description = "负责人", example = "admin")
    private String PM;

}
