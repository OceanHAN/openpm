package cn.iocoder.yudao.module.zentao.controller.admin.dimension;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo.DimensionCurrentRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo.DimensionDropMenuRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo.DimensionRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo.DimensionVisibilityRespVO;
import cn.iocoder.yudao.module.zentao.service.dimension.DimensionService;
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
 * 维度（禅道 {@code module/dimension}）。
 *
 * <p>禅道这个模块只有 2 个 action（{@code ajaxGetDropMenu} / {@code ajaxGetOldDropMenu}），
 * 但「当前维度」的读取链路（{@code dimensionModel::getDimension/saveState}）是它的核心。
 * 两个 action 与本实现的对应：
 * <pre>
 *   ajaxGetDropMenu     → GET /zentao/dimension/drop-menu（data/link/labelMap 原样结构）
 *   ajaxGetOldDropMenu  → 不做：它是 v20 之前的老页面（view 直接渲染 dimensions 列表），
 *                          与 drop-menu 同一份数据，只差一个老模板
 *   —（model 侧）        → GET /zentao/dimension/list、/get、/get-dimension
 * </pre>
 *
 * <p><b>没有写接口是有意的</b>：禅道开源版不含维度 CRUD / 管理界面（全库只有
 * {@code upgrade/model.php:6971} 直接 INSERT），所以这里只有查询与切换（切换 = 写末次记录）。
 */
@Tag(name = "管理后台 - 禅道维度")
@RestController
@RequestMapping("/zentao/dimension")
@Validated
public class ZentaoDimensionController {

    @Resource
    private DimensionService dimensionService;

    @GetMapping("/list")
    @Operation(summary = "可见维度列表",
            description = "禅道 dimensionModel::getList()：先过 biModel::getViewableObject('dimension') 的可见性"
                    + "（acl='open' 或 createdBy=自己 或 whitelist 命中，超管直通），再按 id 取维度")
    @PreAuthorize("@ss.hasPermission('zentao:dimension:query')")
    public CommonResult<List<DimensionRespVO>> getList() {
        return success(BeanUtils.toBean(dimensionService.getList(), DimensionRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得指定维度",
            description = "禅道 dimensionModel::getByID()。比禅道多一层可见性校验：不可见与不存在都返回"
                    + "「维度不存在」，免得私有维度被 id 遍历探到")
    @Parameter(name = "id", description = "维度编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:dimension:query')")
    public CommonResult<DimensionRespVO> getById(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(dimensionService.getById(id), DimensionRespVO.class));
    }

    @GetMapping("/get-dimension")
    @Operation(summary = "当前维度（含末次维度四级兜底链）",
            description = "禅道 dimensionModel::getDimension()：配置 → 会话 → 可见性校验 → 取第一条；"
                    + "随后把结果写回末次记录。返回值比禅道多一个 source，说明这次命中的是哪一级")
    @Parameter(name = "dimensionID", description = "期望的维度编号（不传 = 走兜底）", example = "2")
    @Parameter(name = "tab", description = "标签页（禅道 app->tab，默认 bi）", example = "bi")
    @PreAuthorize("@ss.hasPermission('zentao:dimension:query')")
    public CommonResult<DimensionCurrentRespVO> getDimension(
            @RequestParam(value = "dimensionID", required = false) Long dimensionID,
            @RequestParam(value = "tab", required = false) String tab) {
        return success(dimensionService.getCurrentDimension(dimensionID, tab));
    }

    @GetMapping("/drop-menu")
    @Operation(summary = "1.5 级导航下拉",
            description = "禅道 dimension::ajaxGetDropMenu()：data/searchHint/link/labelMap/expandName/itemType "
                    + "六个键原样。两处参数例外照抄：module=pivot 且 method=design → 改写成 browse；"
                    + "tab=bi 且 module=tree 且 method=browsegroup → 参数追加 groupID=0&type={viewType}")
    @Parameter(name = "dimensionID", description = "当前维度编号", example = "1")
    @Parameter(name = "module", description = "目标模块（screen/pivot/chart/tree）", required = true, example = "pivot")
    @Parameter(name = "method", description = "目标方法", required = true, example = "design")
    @Parameter(name = "viewType", description = "视图类型（tree-browsegroup 用）", example = "pivot")
    @Parameter(name = "tab", description = "标签页（禅道 app->tab，默认 bi）", example = "bi")
    @PreAuthorize("@ss.hasPermission('zentao:dimension:query')")
    public CommonResult<DimensionDropMenuRespVO> getDropMenu(
            @RequestParam(value = "dimensionID", required = false) Long dimensionID,
            @RequestParam("module") String module,
            @RequestParam("method") String method,
            @RequestParam(value = "viewType", required = false) String viewType,
            @RequestParam(value = "tab", required = false) String tab) {
        return success(dimensionService.getDropMenu(dimensionID, module, method, viewType, tab));
    }

    @GetMapping("/visibility")
    @Operation(summary = "可见性口径自检（只读诊断）",
            description = "把 biModel::getViewableObject('dimension') 的三句判据逐行复算出来。"
                    + "存在的意义：admin 是超管、走「直通全部」短路，用 admin 调任何接口都验证不了 "
                    + "acl/createdBy/whitelist 这三句")
    @Parameter(name = "account", description = "被检查的账号（不传 = 当前登录账号）", example = "admin")
    @PreAuthorize("@ss.hasPermission('zentao:dimension:query')")
    public CommonResult<DimensionVisibilityRespVO> getVisibility(
            @RequestParam(value = "account", required = false) String account) {
        return success(dimensionService.getVisibility(account));
    }

}
