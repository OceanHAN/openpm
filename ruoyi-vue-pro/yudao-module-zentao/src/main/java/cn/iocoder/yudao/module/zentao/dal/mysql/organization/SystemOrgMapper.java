package cn.iocoder.yudao.module.zentao.dal.mysql.organization;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 组织与权限视图 Mapper（**读 yudao 自己的 system_* 表**）
 *
 * <h3>为什么会有这么一个「跨模块读表」的 Mapper</h3>
 * 禅道的组织权限模型（{@code zt_user} / {@code zt_dept} / {@code zt_group} / {@code zt_grouppriv}）
 * **不迁移**，而是映射到 yudao 已有的 RBAC 上：
 * <pre>
 *   zt_user         → system_users
 *   zt_dept         → system_dept
 *   zt_group        → system_role
 *   zt_grouppriv    → system_role_menu + system_menu.permission
 * </pre>
 * 理由是 yudao 的 RBAC 已经实现了「用户-角色-菜单-权限」的全套能力（含数据权限 data_scope），
 * 再搬一套禅道的权限包只会得到两套并行的权限系统。这里要做的不是搬运，
 * 而是**把 yudao 的权限数据翻译成禅道的视角**（模块/方法两级权限），
 * 这样迁移评审时能逐条对照「禅道的这个权限包，在新系统里对应哪些菜单权限」。
 *
 * <p>yudao 没有提供「角色 → 权限串」的跨模块 API（只有 {@code RoleApi} / {@code PermissionApi.hasAnyPermissions}），
 * 所以这里直接读表。**这是有意的妥协**：视图层只读、不写，且 SQL 都限定在 system_* 的稳定列上。
 * 如果后续 yudao 提供角色权限查询 API，这个 Mapper 应当被替换掉。
 *
 * <p>这些表带 {@code tenant_id}，原生 SQL 不会被租户拦截器改写，
 * 所以每个查询都显式传 {@code tenantId}（由 Service 从 TenantContextHolder 取）。
 */
@Mapper
public interface SystemOrgMapper {

    /**
     * 用户列表（禅道 zt_user 的视角）：账号、姓名、部门、角色、联系方式、最后登录
     */
    @Select("""
            <script>
            SELECT u.id, u.username, u.nickname, u.dept_id, u.email, u.mobile, u.sex, u.status,
                   u.login_ip, u.login_date, d.name AS deptName,
                   (SELECT GROUP_CONCAT(r.name ORDER BY r.id)
                      FROM system_user_role ur JOIN system_role r ON r.id = ur.role_id AND r.deleted = 0
                     WHERE ur.user_id = u.id AND ur.deleted = 0) AS roleNames,
                   (SELECT GROUP_CONCAT(r.code ORDER BY r.id)
                      FROM system_user_role ur JOIN system_role r ON r.id = ur.role_id AND r.deleted = 0
                     WHERE ur.user_id = u.id AND ur.deleted = 0) AS roleCodes
              FROM system_users u
              LEFT JOIN system_dept d ON d.id = u.dept_id AND d.deleted = 0
             WHERE u.deleted = 0 AND u.tenant_id = #{tenantId}
               <if test="keyword != null and keyword != ''">
                 AND (u.username LIKE CONCAT('%', #{keyword}, '%') OR u.nickname LIKE CONCAT('%', #{keyword}, '%'))
               </if>
               <if test="deptId != null"> AND u.dept_id = #{deptId} </if>
               <if test="status != null"> AND u.status = #{status} </if>
             ORDER BY u.id
            </script>
            """)
    List<Map<String, Object>> selectUserList(@Param("tenantId") Long tenantId,
                                             @Param("keyword") String keyword,
                                             @Param("deptId") Long deptId,
                                             @Param("status") Integer status);

    /**
     * 部门树（禅道 zt_dept 的视角）。
     * 注意 yudao 的部门是 {@code parent_id} 邻接表，禅道 zt_dept 则带 {@code path}/{@code grade}，
     * 树形由 Service 在内存里拼（部门数量很小）。
     */
    @Select("""
            SELECT d.id, d.name, d.parent_id, d.sort, d.leader_user_id, d.status, d.email, d.phone,
                   (SELECT COUNT(*) FROM system_users u WHERE u.dept_id = d.id AND u.deleted = 0 AND u.status = 0) AS userCount
              FROM system_dept d
             WHERE d.deleted = 0 AND d.tenant_id = #{tenantId}
             ORDER BY d.sort, d.id
            """)
    List<Map<String, Object>> selectDeptList(@Param("tenantId") Long tenantId);

    /**
     * 角色列表（禅道 zt_group 权限包的视角）
     */
    @Select("""
            SELECT r.id, r.name, r.code, r.sort, r.data_scope, r.status, r.remark, r.type,
                   (SELECT COUNT(*) FROM system_user_role ur WHERE ur.role_id = r.id AND ur.deleted = 0) AS userCount,
                   (SELECT COUNT(*) FROM system_role_menu rm JOIN system_menu m ON m.id = rm.menu_id
                     WHERE rm.role_id = r.id AND rm.deleted = 0 AND m.deleted = 0
                       AND m.permission LIKE 'zentao:%') AS zentaoPermissionCount
              FROM system_role r
             WHERE r.deleted = 0 AND r.tenant_id = #{tenantId}
             ORDER BY r.sort, r.id
            """)
    List<Map<String, Object>> selectRoleList(@Param("tenantId") Long tenantId);

    /**
     * 某个角色在禅道模块上的权限串（等价于禅道 {@code zt_grouppriv} 里 group=? 的记录）
     */
    @Select("""
            SELECT DISTINCT m.permission
              FROM system_role_menu rm
              JOIN system_menu m ON m.id = rm.menu_id AND m.deleted = 0
             WHERE rm.deleted = 0 AND rm.role_id = #{roleId}
               AND m.status = 0 AND m.permission LIKE 'zentao:%'
             ORDER BY m.permission
            """)
    List<String> selectRolePermissions(@Param("roleId") Long roleId);

    /**
     * 系统里存在的全部禅道权限串。
     *
     * <p>用于「超级管理员」的等价展开：yudao 的 super_admin 是**硬编码放行**
     * （{@code PermissionServiceImpl} 里直接返回 true），根本不会往 system_role_menu 写记录。
     * 所以按表查 super_admin 的权限永远是 0 条 —— 必须在这里显式展开成「全部权限」，
     * 否则迁移评审时看到的超管权限是空的，会得出完全错误的结论。
     */
    @Select("SELECT DISTINCT permission FROM system_menu "
            + "WHERE deleted = 0 AND status = 0 AND permission LIKE 'zentao:%' ORDER BY permission")
    List<String> selectAllZentaoPermissions();

    /**
     * 某个用户是否持有超级管理员角色
     */
    @Select("SELECT COUNT(*) FROM system_user_role ur JOIN system_role r ON r.id = ur.role_id AND r.deleted = 0 "
            + "WHERE ur.deleted = 0 AND ur.user_id = #{userId} AND r.code = 'super_admin'")
    Long countSuperAdminRoleByUser(@Param("userId") Long userId);

    /**
     * 某个用户实际拥有的禅道权限串（跨他所有角色求并集）
     */
    @Select("""
            SELECT DISTINCT m.permission
              FROM system_user_role ur
              JOIN system_role_menu rm ON rm.role_id = ur.role_id AND rm.deleted = 0
              JOIN system_menu m ON m.id = rm.menu_id AND m.deleted = 0
             WHERE ur.deleted = 0 AND ur.user_id = #{userId}
               AND m.status = 0 AND m.permission LIKE 'zentao:%'
             ORDER BY m.permission
            """)
    List<String> selectUserPermissions(@Param("userId") Long userId);

}
