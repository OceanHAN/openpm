package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 看板分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class KanbanPageReqVO extends PageParam {

    @Schema(description = "所属空间", example = "96001")
    private Long space;

    @Schema(description = "看板名称（模糊）", example = "迁移")
    private String name;

    @Schema(description = "状态：active 正常 / closed 已关闭", example = "active")
    private String status;

}
