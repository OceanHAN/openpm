package cn.iocoder.yudao.module.zentao.controller.admin.bug.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Schema(description = "管理后台 - 缺陷分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class BugPageReqVO extends PageParam {

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "1")
    private Long execution;

    @Schema(description = "所属分支/平台", example = "1")
    private Long branch;

    @Schema(description = "所属模块", example = "0")
    private Long module;

    @Schema(hidden = true, description = "模块子树编号，由 Service 展开后填充")
    private List<Long> moduleIds;

    @Schema(description = "关联需求", example = "1")
    private Long story;

    @Schema(description = "状态，参见 BugStatusEnum", example = "active")
    private String status;

    @Schema(description = "严重程度", example = "2")
    private Integer severity;

    @Schema(description = "优先级", example = "3")
    private Integer pri;

    @Schema(description = "缺陷类型", example = "codeerror")
    private String type;

    @Schema(description = "指派给", example = "admin")
    private String assignedTo;

    @Schema(description = "解决人", example = "admin")
    private String resolvedBy;

    @Schema(description = "缺陷标题，模糊匹配", example = "登录")
    private String title;

}
