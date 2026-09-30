package cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 用例集分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class TestSuitePageReqVO extends PageParam {

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "类型", example = "public")
    private String type;

    @Schema(description = "名称（模糊）", example = "冒烟")
    private String name;

}
