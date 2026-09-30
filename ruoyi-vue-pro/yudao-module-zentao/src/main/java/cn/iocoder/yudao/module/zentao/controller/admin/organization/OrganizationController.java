package cn.iocoder.yudao.module.zentao.controller.admin.organization;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgDeptRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgMappingRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgPermissionRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgRoleRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.organization.vo.OrgUserRespVO;
import cn.iocoder.yudao.module.zentao.service.organization.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 组织与权限 Controller（禅道视角的只读视图）
 *
 * <p>禅道的 {@code zt_user} / {@code zt_dept} / {@code zt_group} / {@code zt_grouppriv}
 * **不迁移**，映射到 yudao 的 {@code system_users} / {@code system_dept} /
 * {@code system_role} / {@code system_role_menu}。这里提供的是把 yudao 权限数据
 * **翻译回禅道视角**的只读接口，用于迁移评审时逐条对照。
 */
@Tag(name = "管理后台 - 禅道组织与权限视图")
@RestController
@RequestMapping("/zentao/organization")
@Validated
public class OrganizationController {

    @Resource
    private OrganizationService organizationService;

    @GetMapping("/user-list")
    @Operation(summary = "用户列表（禅道 zt_user 视角）",
            description = "含部门、角色（权限包）、联系方式，以及他能访问的禅道模块")
    @Parameter(name = "keyword", description = "按账号或姓名模糊匹配")
    @Parameter(name = "deptId", description = "按部门过滤")
    @Parameter(name = "status", description = "状态：0 开启 1 停用")
    @PreAuthorize("@ss.hasPermission('zentao:organization:query')")
    public CommonResult<List<OrgUserRespVO>> getUserList(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "status", required = false) Integer status) {
        return success(organizationService.getUserList(keyword, deptId, status));
    }

    @GetMapping("/user-permissions")
    @Operation(summary = "某个用户实际拥有的禅道权限",
            description = "跨他的所有角色求并集，按禅道模块聚合，等价于 zt_grouppriv 按人查询的结果")
    @Parameter(name = "userId", description = "用户编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:organization:query')")
    public CommonResult<List<OrgPermissionRespVO>> getUserPermissions(@RequestParam("userId") Long userId) {
        return success(organizationService.getUserPermissions(userId));
    }

    @GetMapping("/role-list")
    @Operation(summary = "权限包（角色）列表", description = "对应禅道 zt_group；可带每个角色的权限明细")
    @Parameter(name = "withPermissions", description = "是否带上权限明细", example = "true")
    @PreAuthorize("@ss.hasPermission('zentao:organization:query')")
    public CommonResult<List<OrgRoleRespVO>> getRoleList(
            @RequestParam(value = "withPermissions", required = false, defaultValue = "true") Boolean withPermissions) {
        return success(organizationService.getRoleList(Boolean.TRUE.equals(withPermissions)));
    }

    @GetMapping("/role-permissions")
    @Operation(summary = "某个权限包在禅道模块上的权限明细",
            description = "把 yudao 的菜单权限翻译回禅道的 (module, method) 两级结构")
    @Parameter(name = "roleId", description = "角色编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:organization:query')")
    public CommonResult<List<OrgPermissionRespVO>> getRolePermissions(@RequestParam("roleId") Long roleId) {
        return success(organizationService.getRolePermissions(roleId));
    }

    @GetMapping("/dept-tree")
    @Operation(summary = "部门树（禅道 zt_dept 视角）",
            description = "yudao 用邻接表存部门，这里补出禅道风格的 grade 与逗号 path")
    @PreAuthorize("@ss.hasPermission('zentao:organization:query')")
    public CommonResult<List<OrgDeptRespVO>> getDeptTree() {
        return success(organizationService.getDeptTree());
    }

    @GetMapping("/mapping")
    @Operation(summary = "禅道 → yudao 的组织权限映射对照表", description = "供迁移评审逐条核对")
    @PreAuthorize("@ss.hasPermission('zentao:organization:query')")
    public CommonResult<List<OrgMappingRespVO>> getMapping() {
        return success(organizationService.getMapping());
    }

}
