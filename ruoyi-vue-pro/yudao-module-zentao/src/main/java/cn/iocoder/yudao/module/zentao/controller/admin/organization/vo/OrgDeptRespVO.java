package cn.iocoder.yudao.module.zentao.controller.admin.organization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - 禅道视角的部门 Response VO")
@Data
public class OrgDeptRespVO {

    @Schema(description = "部门编号（yudao system_dept.id）", example = "100")
    private Long id;

    @Schema(description = "部门名称。对应禅道 zt_dept.name", example = "研发部")
    private String name;

    @Schema(description = "上级部门。对应禅道 zt_dept.parent", example = "0")
    private Long parentId;

    @Schema(description = "负责人用户编号。对应禅道 zt_dept.manager", example = "1")
    private Long leaderUserId;

    @Schema(description = "负责人姓名", example = "管理员")
    private String leaderName;

    @Schema(description = "排序。对应禅道 zt_dept.order", example = "1")
    private Integer sort;

    @Schema(description = "状态：0 开启 1 停用", example = "0")
    private Integer status;

    @Schema(description = "部门人数", example = "3")
    private Long userCount;

    @Schema(description = "层级（1 为一级部门）。yudao 用邻接表，层级由服务端算出来", example = "2")
    private Integer grade;

    @Schema(description = "层级路径，逗号格式 ,1,2,（对齐禅道 zt_dept.path 的写法）", example = ",1,2,")
    private String path;

    @Schema(description = "子部门")
    private List<OrgDeptRespVO> children = new ArrayList<>();

}
