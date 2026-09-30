package cn.iocoder.yudao.module.zentao.controller.admin.bi.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 数据视图/图表分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class BiPageReqVO extends PageParam {

    @Schema(description = "名称（模糊）", example = "需求")
    private String name;

    @Schema(description = "图表类型（只有查图表时用得上）", example = "pie")
    private String type;

}
