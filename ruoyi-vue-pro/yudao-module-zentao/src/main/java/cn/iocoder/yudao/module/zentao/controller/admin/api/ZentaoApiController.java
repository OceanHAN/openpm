package cn.iocoder.yudao.module.zentao.controller.admin.api;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiDetailRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiLibRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiReleaseRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiReleaseSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiStructPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiStructRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiStructSaveReqVO;
import cn.iocoder.yudao.module.zentao.service.api.ApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 接口文档库（禅道 {@code module/api}）。
 *
 * <h3>先说清楚它不是什么</h3>
 * 它不是「对外 REST 接口管理」，而是**接口文档库**：库 → 目录 → 接口 → 可复用结构 → 发布版本。
 * 禅道 {@code module/api} 有 32 个 action，本实现覆盖的是它的业务主干（12 个端点）：
 * <pre>
 *   index / ajaxGetLibApiList / ajaxGetApi  → GET  /zentao/api/page       接口分页（按库/目录/发布版本）
 *   view                                    → GET  /zentao/api/get        详情（支持 version / releaseID 回溯）
 *   create                                  → POST /zentao/api/create
 *   edit                                    → PUT  /zentao/api/update
 *   delete                                  → DEL  /zentao/api/delete     （带引用保护）
 *   struct                                  → GET  /zentao/api/struct-page
 *   ajaxGetRefInfo                          → GET  /zentao/api/struct-get 结构详情（按 name+version）
 *   createStruct                            → POST /zentao/api/struct-create
 *   releases                                → GET  /zentao/api/release-list
 *   createRelease                           → POST /zentao/api/release-create（打快照）
 *   deleteRelease                           → DEL  /zentao/api/release-delete
 *   （库下拉）                              → GET  /zentao/api/lib-list    （库在 zt_doclib，只读）
 * </pre>
 *
 * <h3>没做的（详见交付说明）</h3>
 * <ul>
 *   <li><b>OpenAPI/Swagger 导入导出</b>：禅道自己就把这三个 action 放在付费扩展里
 *       （{@code control.php:594} {@code if(edition == 'open') return editionLimited}，
 *       然后 {@code loadExtension('openapiimport')} —— 而该扩展在开源包里根本不存在，
 *       {@code export/exportOpenApi/importOpenApi} 连方法都没有）。
 *       本实现不做，页面里明确提示「属付费扩展」。</li>
 *   <li>{@code getModel}/{@code sql}/{@code debug}：禅道的在线调试入口，
 *       被 {@code config/config.php:157-158} 默认关闭（{@code apiGetModel=false} / {@code apiSQL=false}），
 *       本质是「用 PHP 反射调用任意 model + 执行任意 SQL」，搬到 Java 就是给自己开后门，不做。</li>
 *   <li>库的增删改（createLib/editLib/deleteLib）：库是 {@code zt_doclib} 的记录，
 *       本模块只读；而 doc 模块的建库校验只认 product/project/execution/custom 四种类型
 *       （{@code DocLibTypeEnum} 不含 api），所以接口库目前只能用 SQL 建。</li>
 *   <li>目录 CRUD 与排序（editCatalog/deleteCatalog/sortCatalog）：复用
 *       {@code /zentao/module/*}（{@code type='api'}）。</li>
 * </ul>
 */
@Tag(name = "管理后台 - 禅道接口文档库")
@RestController
@RequestMapping("/zentao/api")
@Validated
public class ZentaoApiController {

    @Resource
    private ApiService apiService;

    // ==================== 接口 ====================

    @GetMapping("/page")
    @Operation(summary = "接口分页",
            description = "按库/目录/名称/路径/方式/状态过滤；module 会连带查出子目录下的接口；"
                    + "releaseID = 按发布快照浏览冻结版本")
    @PreAuthorize("@ss.hasPermission('zentao:api:query')")
    public CommonResult<PageResult<ApiRespVO>> getPage(@Valid ApiPageReqVO pageReqVO) {
        return success(apiService.getApiPage(pageReqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "接口详情",
            description = "禅道 api::getByID(id, version, releaseID)：不传版本给当前值，传 version 给历史版本，"
                    + "传 releaseID 先从发布快照里取该接口被冻结的版本号")
    @Parameter(name = "id", description = "接口编号", required = true, example = "92711")
    @Parameter(name = "version", description = "版本号（看历史版本时传）", example = "1")
    @Parameter(name = "releaseID", description = "发布版本编号（按发布回溯时传）", example = "92761")
    @PreAuthorize("@ss.hasPermission('zentao:api:query')")
    public CommonResult<ApiDetailRespVO> getApi(@RequestParam("id") Long id,
                                               @RequestParam(value = "version", required = false) Integer version,
                                               @RequestParam(value = "releaseID", required = false) Long releaseID) {
        return success(apiService.getApiDetail(id, version, releaseID));
    }

    @GetMapping("/lib-list")
    @Operation(summary = "接口库列表",
            description = "接口库是 zt_doclib 里 type='api' 的记录（禅道没有 zt_apilib），附带接口/结构计数")
    @PreAuthorize("@ss.hasPermission('zentao:api:query')")
    public CommonResult<List<ApiLibRespVO>> getLibList() {
        return success(apiService.getLibList());
    }

    @PostMapping("/create")
    @Operation(summary = "新建接口",
            description = "title 在 (lib,module) 内唯一、path 在 (lib,module,method) 内唯一（且不过滤已删除的行）；"
                    + "同时写一条 v1 的 zt_apispec")
    @PreAuthorize("@ss.hasPermission('zentao:api:create')")
    public CommonResult<Long> createApi(@Valid @RequestBody ApiSaveReqVO reqVO) {
        return success(apiService.createApi(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改接口",
            description = "真有字段变更才 version+1，并原地重写该版本的 spec；editedDate 是乐观锁；"
                    + "responseType/commonParams/lib 不在禅道编辑表单里，传了也不会改")
    @PreAuthorize("@ss.hasPermission('zentao:api:update')")
    public CommonResult<Boolean> updateApi(@Valid @RequestBody ApiSaveReqVO reqVO) {
        apiService.updateApi(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除接口",
            description = "被发布版本冻结、或被数据结构引用时拒绝删除（禅道没有这道检查，是本实现的加固）；"
                    + "删除只软删 zt_api 头部，zt_apispec 的历史版本保留")
    @Parameter(name = "id", description = "接口编号", required = true, example = "92711")
    @PreAuthorize("@ss.hasPermission('zentao:api:delete')")
    public CommonResult<Boolean> deleteApi(@RequestParam("id") Long id) {
        apiService.deleteApi(id);
        return success(true);
    }

    // ==================== 数据结构 ====================

    @GetMapping("/struct-page")
    @Operation(summary = "数据结构分页",
            description = "禅道 api::struct(libID, releaseID)：只按库过滤；releaseID 非空时按快照里的 (id,version) 回溯")
    @PreAuthorize("@ss.hasPermission('zentao:api:query')")
    public CommonResult<PageResult<ApiStructRespVO>> getStructPage(@Valid ApiStructPageReqVO pageReqVO) {
        return success(apiService.getStructPage(pageReqVO));
    }

    @GetMapping("/struct-get")
    @Operation(summary = "数据结构详情",
            description = "version 非空时按『结构名 + 版本』取历史版本（zt_apistruct_spec 只按 name 关联，禅道原样）")
    @Parameter(name = "id", description = "结构编号", required = true, example = "92731")
    @Parameter(name = "version", description = "版本号（看历史版本时传）", example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:api:query')")
    public CommonResult<ApiStructRespVO> getStruct(@RequestParam("id") Long id,
                                                   @RequestParam(value = "version", required = false) Integer version) {
        return success(apiService.getStructDetail(id, version));
    }

    @PostMapping("/struct-create")
    @Operation(summary = "新建数据结构",
            description = "attribute 是可嵌套字段树 JSON；同时写一条 v1 的 zt_apistruct_spec")
    @PreAuthorize("@ss.hasPermission('zentao:api:create')")
    public CommonResult<Long> createStruct(@Valid @RequestBody ApiStructSaveReqVO reqVO) {
        return success(apiService.createStruct(reqVO));
    }

    // ==================== 发布版本 ====================

    @GetMapping("/release-list")
    @Operation(summary = "发布版本列表",
            description = "返回每个发布冻结了多少目录/接口/结构，以及快照里的 (id, version) 清单")
    @Parameter(name = "libID", description = "接口库编号（不传=全部库）", example = "92751")
    @PreAuthorize("@ss.hasPermission('zentao:api:query')")
    public CommonResult<List<ApiReleaseRespVO>> getReleaseList(
            @RequestParam(value = "libID", required = false) Long libID) {
        return success(apiService.getReleaseList(libID));
    }

    @PostMapping("/release-create")
    @Operation(summary = "发布接口库",
            description = "禅道 api::publishLib：把当前库的 modules/apis/structs 打成 snap JSON 快照冻结"
                    + "（apis/structs 只存 id+version，内容仍在 spec 表里）；同库版本号不能重复")
    @PreAuthorize("@ss.hasPermission('zentao:api:create')")
    public CommonResult<Long> createRelease(@Valid @RequestBody ApiReleaseSaveReqVO reqVO) {
        return success(apiService.publishLib(reqVO));
    }

    @DeleteMapping("/release-delete")
    @Operation(summary = "删除发布版本",
            description = "物理删除（禅道 deleteRelease 原样）；删掉之后被它冻结的接口才能删除")
    @Parameter(name = "id", description = "发布版本编号", required = true, example = "92761")
    @PreAuthorize("@ss.hasPermission('zentao:api:delete')")
    public CommonResult<Boolean> deleteRelease(@RequestParam("id") Long id) {
        apiService.deleteRelease(id);
        return success(true);
    }

}
