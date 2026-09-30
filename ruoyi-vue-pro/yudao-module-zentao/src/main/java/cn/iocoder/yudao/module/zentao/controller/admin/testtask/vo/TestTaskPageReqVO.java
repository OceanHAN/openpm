package cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 测试单分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class TestTaskPageReqVO extends PageParam {

    @Schema(description = "所属产品", example = "1")
    private Long product;

    @Schema(description = "所属项目", example = "1")
    private Long project;

    @Schema(description = "所属执行", example = "90001")
    private Long execution;

    @Schema(description = "所属构建", example = "1")
    private Long build;

    @Schema(description = "负责人", example = "admin")
    private String owner;

    @Schema(description = "参与者账号：负责人或创建人命中（「我的地盘」用它筛我参与的测试单）", example = "admin")
    private String member;

    @Schema(description = "状态：wait/doing/done/blocked", example = "doing")
    private String status;

    @Schema(description = "类型（逗号列表里包含它即命中）", example = "feature")
    private String type;

    @Schema(description = "名称（模糊）", example = "冒烟")
    private String name;

}
