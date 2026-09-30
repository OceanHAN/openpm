package cn.iocoder.yudao.module.zentao.controller.admin.execution.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 执行分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionPageReqVO extends PageParam {

    @Schema(description = "执行名称，模糊匹配", example = "第 1 迭代")
    private String name;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "状态，参见 ProjectStatusEnum", example = "doing")
    private String status;

    @Schema(description = "执行类型：sprint/stage/kanban", example = "sprint")
    private String type;

    @Schema(description = "项目经理", example = "admin")
    private String PM;

    @Schema(description = "参与者账号：命中 项目经理/质量负责人/研发负责人/产品负责人 之一，或在该执行的团队里"
            + "（「我的地盘」用它筛我参与的执行）", example = "admin")
    private String member;

}
