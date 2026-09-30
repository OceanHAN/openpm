package cn.iocoder.yudao.module.zentao.controller.admin.organization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;


@Schema(description = "管理后台 - 禅道视角的用户 Response VO")
@Data
public class OrgUserRespVO {

    @Schema(description = "用户编号（yudao system_users.id）", example = "1")
    private Long id;

    @Schema(description = "账号。对应禅道 zt_user.account", example = "admin")
    private String account;

    @Schema(description = "姓名。对应禅道 zt_user.realname", example = "管理员")
    private String realname;

    @Schema(description = "部门编号", example = "100")
    private Long deptId;

    @Schema(description = "部门名称", example = "研发部")
    private String deptName;

    @Schema(description = "角色名称，逗号拼接。对应禅道的权限包（zt_group）", example = "超级管理员")
    private String roleNames;

    @Schema(description = "角色编码", example = "super_admin")
    private String roleCodes;

    @Schema(description = "邮箱。对应禅道 zt_user.email", example = "admin@example.com")
    private String email;

    @Schema(description = "手机。对应禅道 zt_user.mobile", example = "13800000000")
    private String mobile;

    @Schema(description = "性别：1 男 2 女。对应禅道 zt_user.gender", example = "1")
    private Integer gender;

    @Schema(description = "状态：0 开启 1 停用。对应禅道 zt_user.status 的 active/closed", example = "0")
    private Integer status;

    @Schema(description = "最后登录 IP", example = "127.0.0.1")
    private String loginIp;

    @Schema(description = "最后登录时间")
    private LocalDateTime loginDate;

    @Schema(description = "持有的权限串数量（禅道视角的权限条目数）", example = "161")
    private Integer permissionCount;

    @Schema(description = "他能访问的禅道模块（去重后的 module 列表）", example = "[\"story\",\"task\"]")
    private List<String> modules;

}
