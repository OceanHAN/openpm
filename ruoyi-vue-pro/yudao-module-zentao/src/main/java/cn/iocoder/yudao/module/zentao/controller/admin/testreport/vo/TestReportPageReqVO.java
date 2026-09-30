package cn.iocoder.yudao.module.zentao.controller.admin.testreport.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 测试报告分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class TestReportPageReqVO extends PageParam {

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "90001")
    private Long execution;

    @Schema(description = "标题（模糊）", example = "V1.0")
    private String title;

}
