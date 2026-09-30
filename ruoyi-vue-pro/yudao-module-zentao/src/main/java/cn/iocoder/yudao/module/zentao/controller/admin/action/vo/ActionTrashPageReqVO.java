package cn.iocoder.yudao.module.zentao.controller.admin.action.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 回收站分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ActionTrashPageReqVO extends PageParam {

    @Schema(description = "对象类型（不传=全部）", example = "story")
    private String objectType;

    @Schema(description = "只看某个人删的", example = "admin")
    private String actor;

}
