package cn.iocoder.yudao.module.zentao.controller.admin.caselib.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 用例库分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CaseLibPageReqVO extends PageParam {

    @Schema(description = "用例库名称（模糊）", example = "公共")
    private String name;

}
