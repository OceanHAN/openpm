package cn.iocoder.yudao.module.zentao.controller.admin.organization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 禅道视角的权限包（角色） Response VO")
@Data
public class OrgRoleRespVO {

    @Schema(description = "角色编号（yudao system_role.id）", example = "1")
    private Long id;

    @Schema(description = "权限包名称。对应禅道 zt_group.name", example = "研发负责人")
    private String name;

    @Schema(description = "角色编码", example = "rd_leader")
    private String code;

    @Schema(description = "数据范围：1 全部 2 本部门 3 本部门及以下 4 仅本人 5 自定义。对应禅道权限包里的数据权限", example = "1")
    private Integer dataScope;

    @Schema(description = "数据范围文案", example = "全部数据")
    private String dataScopeName;

    @Schema(description = "状态：0 开启 1 停用", example = "0")
    private Integer status;

    @Schema(description = "成员数量", example = "2")
    private Long userCount;

    @Schema(description = "该权限包覆盖的禅道权限条目数", example = "40")
    private Long zentaoPermissionCount;

    @Schema(description = "备注", example = "可以管理需求与任务")
    private String remark;

    @Schema(description = "权限明细：模块 → 方法列表", example = "{\"story\":[\"query\",\"create\"]}")
    private List<OrgPermissionRespVO> permissions;

}
