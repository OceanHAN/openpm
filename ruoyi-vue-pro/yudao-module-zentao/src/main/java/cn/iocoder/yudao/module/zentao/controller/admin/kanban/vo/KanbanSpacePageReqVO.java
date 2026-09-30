package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 看板空间分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class KanbanSpacePageReqVO extends PageParam {

    @Schema(description = "空间名称（模糊）", example = "禅道")
    private String name;

    @Schema(description = "空间类型", example = "cooperation")
    private String type;

    @Schema(description = "状态：active 正常 / closed 已关闭", example = "active")
    private String status;

}
