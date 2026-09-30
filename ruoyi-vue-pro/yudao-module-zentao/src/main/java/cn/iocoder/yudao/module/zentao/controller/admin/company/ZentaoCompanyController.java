package cn.iocoder.yudao.module.zentao.controller.admin.company;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.company.vo.CompanyAdminsRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.company.vo.CompanyOptionVO;
import cn.iocoder.yudao.module.zentao.controller.admin.company.vo.CompanyPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.company.vo.CompanyRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.company.vo.CompanySaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.zentao.service.company.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 公司信息（禅道 {@code module/company}，界面上叫「组织视图」）。
 *
 * <p>禅道这个模块有 5 个 action，本实现对应关系：
 * <pre>
 *   view / edit               → 本控制器（zt_company）
 *   browse（按部门看用户）    → 复用 organization 模块：GET /zentao/organization/user-list + /dept-tree
 *   dynamic（组织动态）       → 复用 action 模块：GET /zentao/action/dynamic
 *   index（跳 browse）        → 前端菜单直接指向本页，不需要中转 action
 *   ajaxGetOutsideCompany     → GET /zentao/company/outside-list
 * </pre>
 * 「不重复实现已有查询」是有意的（同 {@code my} 模块的做法）：同一份过滤逻辑只有一处实现，才不会两边跑偏。
 */
@Tag(name = "管理后台 - 禅道公司信息（组织视图）")
@RestController
@RequestMapping("/zentao/company")
@Validated
public class ZentaoCompanyController {

    @Resource
    private CompanyService companyService;

    @GetMapping("/page")
    @Operation(summary = "公司分页")
    @PreAuthorize("@ss.hasPermission('zentao:company:query')")
    public CommonResult<PageResult<CompanyRespVO>> getPage(@Valid CompanyPageReqVO pageReqVO) {
        return success(BeanUtils.toBean(companyService.getPage(pageReqVO), CompanyRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "公司列表（全部，多公司时用）")
    @PreAuthorize("@ss.hasPermission('zentao:company:query')")
    public CommonResult<List<CompanyRespVO>> getList() {
        return success(BeanUtils.toBean(companyService.getList(), CompanyRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得指定公司")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:company:query')")
    public CommonResult<CompanyRespVO> getCompany(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(companyService.getCompany(id), CompanyRespVO.class));
    }

    @GetMapping("/get-first")
    @Operation(summary = "获得本公司", description = "禅道 company::getFirst()：id 最小的一条")
    @PreAuthorize("@ss.hasPermission('zentao:company:query')")
    public CommonResult<CompanyRespVO> getFirstCompany() {
        return success(BeanUtils.toBean(companyService.getFirstCompany(), CompanyRespVO.class));
    }

    @GetMapping("/outside-list")
    @Operation(summary = "外部公司下拉",
            description = "禅道 company::getOutsideCompanies()：id != 1。返回 {text,value,keys} 结构，"
                    + "给「外部干系人的所属公司」下拉用")
    @PreAuthorize("@ss.hasPermission('zentao:company:query')")
    public CommonResult<List<CompanyOptionVO>> getOutsideList() {
        return success(companyService.getOutsideOptions());
    }

    @PostMapping("/create")
    @Operation(summary = "新建公司",
            description = "禅道没有独立的「新建公司」入口，它是在加外部干系人时顺手 insert 的；"
                    + "这里显式提供，配套 /outside-list 给干系人表单用")
    @PreAuthorize("@ss.hasPermission('zentao:company:create')")
    public CommonResult<Long> create(@Valid @RequestBody CompanySaveReqVO reqVO) {
        return success(companyService.create(BeanUtils.toBean(reqVO, CompanyDO.class)));
    }

    @PutMapping("/update")
    @Operation(summary = "修改公司信息",
            description = "name 必填且唯一；website/backyard 只填 http:// 会被清空（禅道原样）。**没有 admins 字段** —— "
                    + "禅道的公司编辑表单里也没有，超管口径见 /admins")
    @PreAuthorize("@ss.hasPermission('zentao:company:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody CompanySaveReqVO reqVO) {
        companyService.update(BeanUtils.toBean(reqVO, CompanyDO.class));
        return success(true);
    }

    @GetMapping("/admins")
    @Operation(summary = "超管口径对照",
            description = "禅道看 zt_company.admins 逗号串，yudao 看 super_admin 角色（硬编码放行、不查权限表）—— "
                    + "这个接口把两边的差异列出来")
    @PreAuthorize("@ss.hasPermission('zentao:company:query')")
    public CommonResult<CompanyAdminsRespVO> getAdmins() {
        return success(companyService.getAdminsMapping());
    }

}
