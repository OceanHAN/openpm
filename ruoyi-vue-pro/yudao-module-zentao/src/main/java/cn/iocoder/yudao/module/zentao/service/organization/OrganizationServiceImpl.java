package cn.iocoder.yudao.module.zentao.service.organization;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgDeptRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgMappingRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgPermissionRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgRoleRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgUserRespVO;
import cn.iocoder.yudao.module.zentao.dal.mysql.organization.SystemOrgMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 组织与权限视图 Service 实现
 *
 * <p>把 yudao RBAC 的数据翻译成禅道视角：权限串 {@code zentao:story:create}
 * 拆成禅道的 {@code (module=story, method=create)}，与 {@code zt_grouppriv} 的两级结构一一对应。
 */
@Slf4j
@Service
public class OrganizationServiceImpl implements OrganizationService {

    /**
     * 禅道模块名 → 中文名。只覆盖本项目已迁移的模块，未知模块直接回显原名
     */
    private static final Map<String, String> MODULE_NAMES = Map.ofEntries(
            Map.entry("product", "产品"),
            Map.entry("story", "需求"),
            Map.entry("plan", "计划"),
            Map.entry("project", "项目"),
            Map.entry("execution", "执行"),
            Map.entry("projectstory", "项目需求"),
            Map.entry("stage", "阶段"),
            Map.entry("task", "任务"),
            Map.entry("bug", "缺陷"),
            Map.entry("build", "构建"),
            Map.entry("release", "发布"),
            Map.entry("branch", "分支/平台"),
            Map.entry("module", "模块树"),
            Map.entry("action", "操作日志"),
            Map.entry("organization", "组织与权限"));

    @Resource
    private SystemOrgMapper systemOrgMapper;

    @Resource
    private AdminUserApi adminUserApi;

    @Resource
    private DeptApi deptApi;

    // ==================== 用户 ====================

    @Override
    public List<OrgUserRespVO> getUserList(String keyword, Long deptId, Integer status) {
        Long tenantId = TenantContextHolder.getTenantId();
        List<Map<String, Object>> rows = systemOrgMapper.selectUserList(tenantId, keyword, deptId, status);
        List<OrgUserRespVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            OrgUserRespVO vo = new OrgUserRespVO();
            Long userId = toLong(row.get("id"));
            vo.setId(userId);
            vo.setAccount(str(row.get("username")));
            vo.setRealname(str(row.get("nickname")));
            vo.setDeptId(toLong(row.get("dept_id")));
            vo.setDeptName(str(row.get("deptName")));
            vo.setRoleNames(str(row.get("roleNames")));
            vo.setRoleCodes(str(row.get("roleCodes")));
            vo.setEmail(str(row.get("email")));
            vo.setMobile(str(row.get("mobile")));
            vo.setGender(toInteger(row.get("sex")));
            vo.setStatus(toInteger(row.get("status")));
            vo.setLoginIp(str(row.get("login_ip")));
            vo.setLoginDate(toDateTime(row.get("login_date")));
            // 权限条数与模块列表：这是禅道评审时最关心的「这个人在新系统能做什么」
            List<String> permissions = resolveUserPermissions(userId);
            vo.setPermissionCount(permissions.size());
            vo.setModules(new ArrayList<>(new LinkedHashSet<>(
                    permissions.stream().map(this::moduleOf).filter(m -> m != null).toList())));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<OrgPermissionRespVO> getUserPermissions(Long userId) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null) {
            return List.of();
        }
        return groupByModule(resolveUserPermissions(userId));
    }

    /**
     * 解析用户的有效权限：超级管理员等价于「全部禅道权限」。
     *
     * <p>原因见 {@code SystemOrgMapper#selectAllZentaoPermissions()}：
     * yudao 的 super_admin 是硬编码放行，不会写 system_role_menu，
     * 直接查表会得到 0 条，必须显式展开。
     */
    private List<String> resolveUserPermissions(Long userId) {
        Long superAdminCount = systemOrgMapper.countSuperAdminRoleByUser(userId);
        if (superAdminCount != null && superAdminCount > 0) {
            return systemOrgMapper.selectAllZentaoPermissions();
        }
        return systemOrgMapper.selectUserPermissions(userId);
    }

    /**
     * 解析角色的权限：超级管理员角色（code=super_admin）同样展开成全部权限
     */
    private List<String> resolveRolePermissions(Long roleId, String roleCode) {
        if ("super_admin".equals(roleCode)) {
            return systemOrgMapper.selectAllZentaoPermissions();
        }
        return systemOrgMapper.selectRolePermissions(roleId);
    }

    // ==================== 权限包（角色） ====================

    @Override
    public List<OrgRoleRespVO> getRoleList(boolean withPermissions) {
        Long tenantId = TenantContextHolder.getTenantId();
        List<OrgRoleRespVO> result = new ArrayList<>();
        for (Map<String, Object> row : systemOrgMapper.selectRoleList(tenantId)) {
            OrgRoleRespVO vo = new OrgRoleRespVO();
            vo.setId(toLong(row.get("id")));
            vo.setName(str(row.get("name")));
            vo.setCode(str(row.get("code")));
            vo.setDataScope(toInteger(row.get("data_scope")));
            vo.setDataScopeName(dataScopeName(toInteger(row.get("data_scope"))));
            vo.setStatus(toInteger(row.get("status")));
            vo.setRemark(str(row.get("remark")));
            vo.setUserCount(toLong(row.get("userCount")));
            vo.setZentaoPermissionCount(toLong(row.get("zentaoPermissionCount")));
            if (withPermissions) {
                List<String> permissions = resolveRolePermissions(vo.getId(), vo.getCode());
                vo.setPermissions(groupByModule(permissions));
                // 超管是展开出来的，条数要按展开后的算，否则列表显示 0 会误导评审
                vo.setZentaoPermissionCount((long) permissions.size());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<OrgPermissionRespVO> getRolePermissions(Long roleId) {
        // 需要知道角色 code 才能判断是不是超管，所以这里按角色列表查一次（角色数量很小）
        String roleCode = getRoleList(false).stream()
                .filter(role -> role.getId().equals(roleId))
                .map(OrgRoleRespVO::getCode)
                .findFirst().orElse(null);
        return groupByModule(resolveRolePermissions(roleId, roleCode));
    }

    // ==================== 部门 ====================

    @Override
    public List<OrgDeptRespVO> getDeptTree() {
        Long tenantId = TenantContextHolder.getTenantId();
        List<Map<String, Object>> rows = systemOrgMapper.selectDeptList(tenantId);
        List<OrgDeptRespVO> flat = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            OrgDeptRespVO vo = new OrgDeptRespVO();
            vo.setId(toLong(row.get("id")));
            vo.setName(str(row.get("name")));
            vo.setParentId(toLong(row.get("parent_id")));
            vo.setLeaderUserId(toLong(row.get("leader_user_id")));
            vo.setSort(toInteger(row.get("sort")));
            vo.setStatus(toInteger(row.get("status")));
            vo.setUserCount(toLong(row.get("userCount")));
            flat.add(vo);
        }
        // 负责人姓名：批量取，避免 N+1
        List<Long> leaderIds = flat.stream().map(OrgDeptRespVO::getLeaderUserId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (!leaderIds.isEmpty()) {
            Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(leaderIds);
            flat.forEach(dept -> {
                AdminUserRespDTO leader = userMap.get(dept.getLeaderUserId());
                if (leader != null) {
                    dept.setLeaderName(leader.getNickname());
                }
            });
        }
        return buildDeptTree(flat);
    }

    @Override
    public List<OrgMappingRespVO> getMapping() {
        return List.of(
                new OrgMappingRespVO("zt_user", "system_users", "不迁移，直接复用 yudao 用户体系",
                        List.of("account → username", "realname → nickname", "email → email",
                                "mobile → mobile", "gender → sex", "status(active/closed) → status(0/1)",
                                "dept → dept_id", "join → create_time", "last → login_date", "visits → 无对应（可另建统计表）"),
                        List.of("禅道一个账号只属于一个部门；yudao 的 system_users.dept_id 也是单值，一致",
                                "禅道的 user.role 是「角色名」字符串，yudao 是 user_role 多对多 —— 迁移时按角色名建角色再授权",
                                "禅道密码是 md5(md5(pass) + salt)，yudao 是 BCrypt：老密码无法直接复用，需重置或登录时升级",
                                "禅道还有 zt_user 上的 60+ 列（如 score/visits/ip 等），本项目只映射业务需要的字段")),
                new OrgMappingRespVO("zt_dept", "system_dept", "不迁移，用 yudao 部门表承载禅道的部门树",
                        List.of("name → name", "parent → parent_id", "manager → leader_user_id", "order → sort"),
                        List.of("禅道 zt_dept 带 path/grade（冗余路径），yudao 是纯邻接表 —— 本视图在内存里补出 path/grade",
                                "禅道部门可以有多级负责人（manager），yudao 只有 leader_user_id 一个",
                                "zt_dept.position 是「职位」（如 研发经理），在 yudao 里对应岗位 system_post.code")),
                new OrgMappingRespVO("zt_group + zt_grouppriv", "system_role + system_role_menu + system_menu.permission",
                        "不迁移，映射到 yudao 的 RBAC",
                        List.of("zt_group.name → system_role.name",
                                "zt_group.desc → system_role.remark",
                                "zt_grouppriv(module, method) → system_menu.permission 的 zentao:module:method 三段",
                                "zt_user 与 zt_group 的关联 → system_user_role（多对多，语义一致）"),
                        List.of("这是本项目唯一「有意不迁移」的核心对象：搬两套权限会长期双写，收益为负",
                                "禅道权限粒度是「模块 + 方法」，yudao 权限粒度是「菜单按钮级权限串」，两者可以一一映射",
                                "禅道的「权限包」可同时用于项目权限（zt_group.project），yudao 的数据权限用 data_scope + 角色实现",
                                "本页提供的 role-permissions 接口就是映射结果：把角色的菜单权限翻译回禅道模块/方法，供逐条评审")),
                new OrgMappingRespVO("zt_company", "（无对应）", "暂不迁移",
                        List.of(),
                        List.of("禅道 zt_company 存公司名称/电话/官网/地址/访客账号等单行配置",
                                "在 yudao 里可放到「参数配置 system_config」或另建单行配置表；本项目暂不实现",
                                "如果只需要「公司名」，用 system_config 的一个 key 即可，不必单独建表")));
    }

    // ==================== 内部 ====================

    /**
     * 把权限串按模块聚合：{@code zentao:story:create} → module=story / method=create。
     *
     * <p>这正是禅道 {@code zt_grouppriv(group, module, method)} 的两级结构。
     */
    private List<OrgPermissionRespVO> groupByModule(List<String> permissions) {
        Map<String, List<String>> moduleMethods = new LinkedHashMap<>();
        for (String permission : permissions) {
            String[] parts = permission.split(":");
            if (parts.length != 3) {
                continue;
            }
            moduleMethods.computeIfAbsent(parts[1], k -> new ArrayList<>()).add(parts[2]);
        }
        List<OrgPermissionRespVO> result = new ArrayList<>();
        moduleMethods.forEach((module, methods) -> {
            List<String> sorted = methods.stream().distinct().sorted().toList();
            result.add(new OrgPermissionRespVO(module,
                    MODULE_NAMES.getOrDefault(module, module), sorted, sorted.size()));
        });
        result.sort((a, b) -> a.getModule().compareTo(b.getModule()));
        return result;
    }

    private String moduleOf(String permission) {
        String[] parts = permission.split(":");
        return parts.length == 3 ? parts[1] : null;
    }

    /**
     * 部门树：yudao 是邻接表，这里补出 grade 与逗号 path（对齐禅道 zt_dept 的写法）
     */
    private List<OrgDeptRespVO> buildDeptTree(List<OrgDeptRespVO> flat) {
        Map<Long, OrgDeptRespVO> map = new LinkedHashMap<>();
        flat.forEach(dept -> map.put(dept.getId(), dept));
        List<OrgDeptRespVO> roots = new ArrayList<>();
        for (OrgDeptRespVO dept : flat) {
            OrgDeptRespVO parent = dept.getParentId() == null ? null : map.get(dept.getParentId());
            if (parent == null || dept.getParentId() == 0L) {
                roots.add(dept);
            } else {
                parent.getChildren().add(dept);
            }
        }
        roots.forEach(root -> fillPath(root, ",", 0));
        return roots;
    }

    private void fillPath(OrgDeptRespVO dept, String parentPath, int parentGrade) {
        dept.setPath(parentPath + dept.getId() + ",");
        dept.setGrade(parentGrade + 1);
        dept.getChildren().forEach(child -> fillPath(child, dept.getPath(), dept.getGrade()));
    }

    private String dataScopeName(Integer dataScope) {
        if (dataScope == null) {
            return "";
        }
        return switch (dataScope) {
            case 1 -> "全部数据";
            case 2 -> "指定部门数据";
            case 3 -> "本部门数据";
            case 4 -> "本部门及以下数据";
            case 5 -> "仅本人数据";
            default -> "未知(" + dataScope + ")";
        };
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private Integer toInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private LocalDateTime toDateTime(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}
