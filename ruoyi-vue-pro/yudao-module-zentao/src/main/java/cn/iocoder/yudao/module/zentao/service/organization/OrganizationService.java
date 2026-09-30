package cn.iocoder.yudao.module.zentao.service.organization;

import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgDeptRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgMappingRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgPermissionRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgRoleRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgUserRespVO;

import java.util.List;

/**
 * 组织与权限视图 Service
 *
 * <h3>这一块为什么是「视图」而不是「迁移」</h3>
 * 禅道的组织权限是四张表：{@code zt_user} / {@code zt_dept} / {@code zt_group} / {@code zt_grouppriv}。
 * 本项目的决策是**不搬运**，而是映射到 yudao 已有的 RBAC：
 * <pre>
 *   zt_user      → system_users
 *   zt_dept      → system_dept
 *   zt_group     → system_role
 *   zt_grouppriv → system_role_menu + system_menu.permission
 * </pre>
 * 原因：yudao 的 RBAC 已经覆盖「用户-角色-菜单-权限 + 数据范围」，
 * 再搬一套权限包会得到两套并行的权限系统，后续每次改权限都要同步两边 —— 这是负收益。
 *
 * <p>所以这里只提供**把 yudao 的权限数据翻译成禅道视角**的只读接口：
 * 禅道评审时最常问的两个问题是「这个人在新系统里能干什么」与
 * 「这个权限包对应新系统的哪些菜单权限」，下面的接口就是为回答它们而存在的。
 */
public interface OrganizationService {

    /**
     * 用户列表（禅道 zt_user 视角），可按账号/姓名、部门、状态过滤
     */
    List<OrgUserRespVO> getUserList(String keyword, Long deptId, Integer status);

    /**
     * 某个用户实际拥有的禅道权限（跨其所有角色求并集），按模块聚合
     */
    List<OrgPermissionRespVO> getUserPermissions(Long userId);

    /**
     * 权限包（角色）列表，含权限明细
     *
     * @param withPermissions 是否带上每个角色的权限明细（列表页可以关掉以省查询）
     */
    List<OrgRoleRespVO> getRoleList(boolean withPermissions);

    /**
     * 某个权限包在禅道模块上的权限明细
     */
    List<OrgPermissionRespVO> getRolePermissions(Long roleId);

    /**
     * 部门树（禅道 zt_dept 视角）
     */
    List<OrgDeptRespVO> getDeptTree();

    /**
     * 禅道 → yudao 的组织权限映射对照表
     */
    List<OrgMappingRespVO> getMapping();

}
