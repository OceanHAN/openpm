package cn.iocoder.yudao.module.zentao.controller.admin.caselib;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.caselib.vo.CaseLibPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.caselib.vo.CaseLibRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.caselib.vo.CaseLibSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CasePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseSaveReqVO;
import cn.iocoder.yudao.module.zentao.service.caselib.CaseLibService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 用例库 Controller。
 *
 * <p>权限标识 {@code zentao:caselib:xxx}；用例库与用例集共用 {@code zt_testsuite}、
 * 与产品用例共用 {@code zt_case}，所以这里的接口都强制带 type/lib 区分条件，见 README 3.32。
 */
@Tag(name = "管理后台 - 用例库")
@RestController
@RequestMapping("/zentao/caselib")
@Validated
public class CaseLibController {

    @Resource
    private CaseLibService caseLibService;

    // ==================== 用例库 ====================

    @PostMapping("/create")
    @Operation(summary = "新建用例库", description = "禅道 caselib/create：名称全局唯一（zt_testsuite.deleted=0）")
    @PreAuthorize("@ss.hasPermission('zentao:caselib:create')")
    public CommonResult<Long> createLib(@Valid @RequestBody CaseLibSaveReqVO reqVO) {
        return success(caseLibService.createLib(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改用例库")
    @PreAuthorize("@ss.hasPermission('zentao:caselib:update')")
    public CommonResult<Boolean> updateLib(@Valid @RequestBody CaseLibSaveReqVO reqVO) {
        caseLibService.updateLib(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除用例库", description = "库内还有用例时拒绝删除（本实现加的保护，禅道会直接软删）")
    @Parameter(name = "id", description = "用例库编号", required = true, example = "95301")
    @PreAuthorize("@ss.hasPermission('zentao:caselib:delete')")
    public CommonResult<Boolean> deleteLib(@RequestParam("id") Long id) {
        caseLibService.deleteLib(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得用例库")
    @Parameter(name = "id", description = "用例库编号", required = true, example = "95301")
    @PreAuthorize("@ss.hasPermission('zentao:caselib:query')")
    public CommonResult<CaseLibRespVO> getLib(@RequestParam("id") Long id) {
        return success(caseLibService.getLib(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得用例库分页")
    @PreAuthorize("@ss.hasPermission('zentao:caselib:query')")
    public CommonResult<PageResult<CaseLibRespVO>> getLibPage(@Valid CaseLibPageReqVO reqVO) {
        return success(caseLibService.getLibPage(reqVO));
    }

    @GetMapping("/list")
    @Operation(summary = "获得全部用例库（下拉用）")
    @PreAuthorize("@ss.hasPermission('zentao:caselib:query')")
    public CommonResult<List<CaseLibRespVO>> getLibList() {
        return success(caseLibService.getLibList());
    }

    // ==================== 库内用例 ====================

    @GetMapping("/case-page")
    @Operation(summary = "获得用例库内的用例分页",
            description = "库内用例 = zt_case 里 (product=0, lib=用例库编号)；会带上「源用例已更新」标记")
    @Parameter(name = "libId", description = "用例库编号", required = true, example = "95301")
    @PreAuthorize("@ss.hasPermission('zentao:caselib:query')")
    public CommonResult<PageResult<CaseRespVO>> getLibCasePage(@RequestParam("libId") Long libId,
                                                              @Valid CasePageReqVO reqVO) {
        return success(caseLibService.getLibCasePage(libId, reqVO));
    }

    @GetMapping("/case-get")
    @Operation(summary = "获得库内用例详情（带步骤）")
    @Parameter(name = "id", description = "用例编号", required = true, example = "95401")
    @PreAuthorize("@ss.hasPermission('zentao:caselib:query')")
    public CommonResult<CaseRespVO> getLibCase(@RequestParam("id") Long id) {
        return success(caseLibService.getLibCase(id));
    }

    @PostMapping("/create-case")
    @Operation(summary = "在用例库里新建用例",
            description = "product 固定 0、lib 取自路径参数；版本/步骤/评审规则完全复用 testcase 模块")
    @Parameter(name = "libId", description = "用例库编号", required = true, example = "95301")
    @PreAuthorize("@ss.hasPermission('zentao:caselib:create')")
    public CommonResult<Long> createLibCase(@RequestParam("libId") Long libId,
                                            @Valid @RequestBody CaseSaveReqVO reqVO) {
        return success(caseLibService.createLibCase(libId, reqVO));
    }

    // ==================== 产品用例 → 用例库 ====================

    @GetMapping("/can-import-case-page")
    @Operation(summary = "获得可以导入该用例库的产品用例（排除已导入的）",
            description = "禅道 testcase/getCanImportCases：按 fromCaseID 排除已经导入过这个库的用例")
    @Parameter(name = "libId", description = "用例库编号", required = true, example = "95301")
    @Parameter(name = "product", description = "产品编号", required = true, example = "1")
    @Parameter(name = "title", description = "标题模糊匹配", example = "登录")
    @PreAuthorize("@ss.hasPermission('zentao:caselib:query')")
    public CommonResult<PageResult<CaseRespVO>> getCanImportCasePage(@RequestParam("libId") Long libId,
                                                                     @RequestParam("product") Long product,
                                                                     @RequestParam(value = "title", required = false) String title,
                                                                     @Valid PageParam pageParam) {
        return success(caseLibService.getCanImportCasePage(libId, product, title, pageParam));
    }

    @PostMapping("/import-to-lib")
    @Operation(summary = "把产品用例导入用例库",
            description = "复制用例与步骤，记下来源编号与来源版本（fromCaseID / fromCaseVersion），"
                    + "并把来源模块同步到库的模块树下")
    @Parameter(name = "libId", description = "用例库编号", required = true, example = "95301")
    @PreAuthorize("@ss.hasPermission('zentao:caselib:create')")
    public CommonResult<List<Long>> importToLib(@RequestParam("libId") Long libId,
                                               @RequestBody List<Long> caseIds) {
        return success(caseLibService.importToLib(libId, caseIds));
    }

}
