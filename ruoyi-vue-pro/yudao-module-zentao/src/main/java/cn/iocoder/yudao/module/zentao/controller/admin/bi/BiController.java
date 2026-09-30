package cn.iocoder.yudao.module.zentao.controller.admin.bi;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.bi.vo.*;
import cn.iocoder.yudao.module.zentao.service.bi.BiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * BI Controller（数据视图 + 图表）。
 *
 * <p>权限标识 {@code zentao:bi:xxx}。数据源是「数据视图」里的一条**只读 SELECT**
 * （过 {@code SqlGuard} 白名单），图表在其上做「按维度分组 + 聚合指标」。
 * 禅道这一层底下是 DuckDB + Parquet，本实现走 SQL 模式，见 README 3.35。
 */
@Tag(name = "管理后台 - 数据视图/图表")
@RestController
@RequestMapping("/zentao/bi")
@Validated
public class BiController {

    @Resource
    private BiService biService;

    @GetMapping("/dict")
    @Operation(summary = "获得 BI 字典（图表类型 / 聚合方式 / 排序 / 可用数据视图）")
    @PreAuthorize("@ss.hasPermission('zentao:bi:query')")
    public CommonResult<Map<String, Object>> getDict() {
        return success(biService.getDict());
    }

    // ==================== 数据视图 ====================

    @GetMapping("/dataview/page")
    @Operation(summary = "获得数据视图分页")
    @PreAuthorize("@ss.hasPermission('zentao:bi:query')")
    public CommonResult<PageResult<DataViewRespVO>> getDataViewPage(@Valid BiPageReqVO reqVO) {
        return success(biService.getDataViewPage(reqVO));
    }

    @GetMapping("/dataview/list")
    @Operation(summary = "获得数据视图列表")
    @PreAuthorize("@ss.hasPermission('zentao:bi:query')")
    public CommonResult<List<DataViewRespVO>> getDataViewList() {
        return success(biService.getDataViewList());
    }

    @GetMapping("/dataview/get")
    @Operation(summary = "获得数据视图")
    @Parameter(name = "id", required = true, example = "97001")
    @PreAuthorize("@ss.hasPermission('zentao:bi:query')")
    public CommonResult<DataViewRespVO> getDataView(@RequestParam("id") Long id) {
        return success(biService.getDataView(id));
    }

    @PostMapping("/dataview/create")
    @Operation(summary = "新建数据视图",
            description = "SQL 必须是单条只读 SELECT、只能查 zt_* 表；不传字段时按 SQL 自动解析")
    @PreAuthorize("@ss.hasPermission('zentao:bi:create')")
    public CommonResult<Long> createDataView(@Valid @RequestBody DataViewSaveReqVO reqVO) {
        return success(biService.createDataView(reqVO));
    }

    @PutMapping("/dataview/update")
    @Operation(summary = "修改数据视图")
    @PreAuthorize("@ss.hasPermission('zentao:bi:update')")
    public CommonResult<Boolean> updateDataView(@Valid @RequestBody DataViewSaveReqVO reqVO) {
        biService.updateDataView(reqVO);
        return success(true);
    }

    @DeleteMapping("/dataview/delete")
    @Operation(summary = "删除数据视图", description = "被图表引用时拒绝删除")
    @Parameter(name = "id", required = true, example = "97001")
    @PreAuthorize("@ss.hasPermission('zentao:bi:delete')")
    public CommonResult<Boolean> deleteDataView(@RequestParam("id") Long id) {
        biService.deleteDataView(id);
        return success(true);
    }

    @GetMapping("/dataview/preview")
    @Operation(summary = "预览数据视图的数据（最多 200 行）")
    @Parameter(name = "id", required = true, example = "97001")
    @Parameter(name = "limit", description = "取多少行，默认 20", example = "20")
    @PreAuthorize("@ss.hasPermission('zentao:bi:query')")
    public CommonResult<DataViewPreviewRespVO> previewDataView(@RequestParam("id") Long id,
                                                               @RequestParam(value = "limit", required = false) Integer limit) {
        return success(biService.previewDataView(id, limit));
    }

    @PostMapping("/dataview/preview-sql")
    @Operation(summary = "试跑一段 SQL（新建数据视图前先校验 + 看字段与数据）",
            description = "用 JSON body 传 SQL（长 SQL 走表单参数容易被前端 axios 的默认 JSON header 坑到）")
    @PreAuthorize("@ss.hasPermission('zentao:bi:query')")
    public CommonResult<DataViewPreviewRespVO> previewSql(@Valid @RequestBody SqlPreviewReqVO reqVO) {
        return success(biService.previewSql(reqVO.getSql(), reqVO.getLimit()));
    }

    // ==================== 图表 ====================

    @GetMapping("/chart/page")
    @Operation(summary = "获得图表分页")
    @PreAuthorize("@ss.hasPermission('zentao:bi:query')")
    public CommonResult<PageResult<ChartRespVO>> getChartPage(@Valid BiPageReqVO reqVO) {
        return success(biService.getChartPage(reqVO));
    }

    @GetMapping("/chart/list")
    @Operation(summary = "获得图表列表")
    @PreAuthorize("@ss.hasPermission('zentao:bi:query')")
    public CommonResult<List<ChartRespVO>> getChartList() {
        return success(biService.getChartList());
    }

    @GetMapping("/chart/get")
    @Operation(summary = "获得图表定义")
    @Parameter(name = "id", required = true, example = "97101")
    @PreAuthorize("@ss.hasPermission('zentao:bi:query')")
    public CommonResult<ChartRespVO> getChart(@RequestParam("id") Long id) {
        return success(biService.getChart(id));
    }

    @PostMapping("/chart/create")
    @Operation(summary = "新建图表",
            description = "settings = {dimensionField 维度, metricField 指标, agg 聚合, limit, sort}")
    @PreAuthorize("@ss.hasPermission('zentao:bi:create')")
    public CommonResult<Long> createChart(@Valid @RequestBody ChartSaveReqVO reqVO) {
        return success(biService.createChart(reqVO));
    }

    @PutMapping("/chart/update")
    @Operation(summary = "修改图表（版本 +1）")
    @PreAuthorize("@ss.hasPermission('zentao:bi:update')")
    public CommonResult<Boolean> updateChart(@Valid @RequestBody ChartSaveReqVO reqVO) {
        biService.updateChart(reqVO);
        return success(true);
    }

    @DeleteMapping("/chart/delete")
    @Operation(summary = "删除图表")
    @Parameter(name = "id", required = true, example = "97101")
    @PreAuthorize("@ss.hasPermission('zentao:bi:delete')")
    public CommonResult<Boolean> deleteChart(@RequestParam("id") Long id) {
        biService.deleteChart(id);
        return success(true);
    }

    @GetMapping("/chart/data")
    @Operation(summary = "获得图表数据",
            description = "按维度分组、对指标做聚合，返回 [{name, value}]，前端直接喂给 ECharts")
    @Parameter(name = "id", required = true, example = "97101")
    @PreAuthorize("@ss.hasPermission('zentao:bi:query')")
    public CommonResult<ChartDataRespVO> getChartData(@RequestParam("id") Long id) {
        return success(biService.getChartData(id));
    }

}
