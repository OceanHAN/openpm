package cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 看板卡片分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class KanbanCardPageReqVO extends PageParam {

    @Schema(description = "所属看板", example = "96101")
    private Long kanban;

    @Schema(description = "卡片标题（模糊）", example = "登录")
    private String name;

    @Schema(description = "归档状态：0 未归档 / 1 已归档；不传=全部", example = "0")
    private Integer archived;

}
