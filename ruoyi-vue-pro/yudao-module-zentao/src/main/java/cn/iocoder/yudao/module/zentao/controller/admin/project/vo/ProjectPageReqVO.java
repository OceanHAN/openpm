package cn.iocoder.yudao.module.zentao.controller.admin.project.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 项目分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPageReqVO extends PageParam {

    @Schema(description = "项目名称，模糊匹配", example = "登录")
    private String name;

    @Schema(description = "项目代号，模糊匹配", example = "PRJ")
    private String code;

    @Schema(description = "状态，参见 ProjectStatusEnum", example = "doing")
    private String status;

    @Schema(description = "模型，参见 ProjectModelEnum", example = "scrum")
    private String model;

    @Schema(description = "所属项目集", example = "9001")
    private Long parent;

    @Schema(description = "项目经理", example = "admin")
    private String PM;

    @Schema(description = "参与者账号：命中 项目经理/质量负责人/研发负责人/产品负责人 之一，或在该项目的团队里"
            + "（「我的地盘」用它筛我参与的项目）", example = "admin")
    private String member;

}
