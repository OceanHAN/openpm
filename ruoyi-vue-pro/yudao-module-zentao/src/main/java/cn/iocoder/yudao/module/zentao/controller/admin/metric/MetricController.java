package cn.iocoder.yudao.module.zentao.controller.admin.metric;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.metric.vo.MetricCalcRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.metric.vo.MetricDataRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.metric.vo.MetricPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.metric.vo.MetricRespVO;
import cn.iocoder.yudao.module.zentao.service.metric.MetricService;
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
 * 度量 Controller。
 *
 * <p>权限标识 {@code zentao:metric:xxx}。度量项定义在 {@code zt_metric}、数据在
 * {@code zt_metriclib}，口径实现见 {@code MetricRegistry}（禅道是 module/metric/calc 下的 calc 类）。
 */
@Tag(name = "管理后台 - 度量")
@RestController
@RequestMapping("/zentao/metric")
@Validated
public class MetricController {

    @Resource
    private MetricService metricService;

    @GetMapping("/dict")
    @Operation(summary = "获得度量字典（目的/范围/对象/单位/时间维度/计算方式）")
    @PreAuthorize("@ss.hasPermission('zentao:metric:query')")
    public CommonResult<Map<String, Object>> getDict() {
        return success(metricService.getDict());
    }

    @GetMapping("/summary")
    @Operation(summary = "获得度量概览（内置数 / 已迁移口径数 / 数据量 / 上次计算时间）")
    @PreAuthorize("@ss.hasPermission('zentao:metric:query')")
    public CommonResult<Map<String, Object>> getSummary() {
        return success(metricService.getSummary());
    }

    @GetMapping("/page")
    @Operation(summary = "获得度量项分页")
    @PreAuthorize("@ss.hasPermission('zentao:metric:query')")
    public CommonResult<PageResult<MetricRespVO>> getMetricPage(@Valid MetricPageReqVO reqVO) {
        return success(metricService.getMetricPage(reqVO));
    }

    @GetMapping("/list")
    @Operation(summary = "获得度量项列表（按目的/范围/对象过滤）")
    @Parameter(name = "purpose", description = "度量目的", example = "scale")
    @Parameter(name = "scope", description = "度量范围", example = "product")
    @Parameter(name = "object", description = "度量对象", example = "story")
    @PreAuthorize("@ss.hasPermission('zentao:metric:query')")
    public CommonResult<List<MetricRespVO>> getMetricList(
            @RequestParam(value = "purpose", required = false) String purpose,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "object", required = false) String object) {
        return success(metricService.getMetricList(purpose, scope, object));
    }

    @GetMapping("/get")
    @Operation(summary = "获得度量项定义（按 code）")
    @Parameter(name = "code", description = "度量项代码", required = true, example = "count_of_story_in_product")
    @PreAuthorize("@ss.hasPermission('zentao:metric:query')")
    public CommonResult<MetricRespVO> getMetric(@RequestParam("code") String code) {
        return success(metricService.getMetric(code));
    }

    @PostMapping("/calc")
    @Operation(summary = "计算一个度量项",
            description = "跑口径 → 按周期清掉旧数据 → 写入 zt_metriclib → 回写 lastCalcRows/lastCalcTime")
    @Parameter(name = "code", description = "度量项代码", required = true, example = "count_of_story_in_product")
    @Parameter(name = "calcType", description = "计算方式：cron 定时 / inference 人工触发", example = "inference")
    @PreAuthorize("@ss.hasPermission('zentao:metric:calc')")
    public CommonResult<MetricCalcRespVO> calcMetric(@RequestParam("code") String code,
                                                     @RequestParam(value = "calcType", required = false) String calcType) {
        return success(metricService.calcMetric(code, calcType));
    }

    @PostMapping("/calc-all")
    @Operation(summary = "计算所有已迁移口径的度量项")
    @Parameter(name = "calcType", description = "计算方式：cron 定时 / inference 人工触发", example = "inference")
    @PreAuthorize("@ss.hasPermission('zentao:metric:calc')")
    public CommonResult<List<MetricCalcRespVO>> calcAll(
            @RequestParam(value = "calcType", required = false) String calcType) {
        return success(metricService.calcAll(calcType));
    }

    @GetMapping("/data")
    @Operation(summary = "获得度量数据",
            description = "按度量项 + 范围查数据；支持按时间维度过滤（传 2026-03-01 这样的日期即可）")
    @Parameter(name = "code", description = "度量项代码", required = true, example = "count_of_story_in_product")
    @Parameter(name = "scope", description = "范围（不传用度量项自己的范围）", example = "product")
    @Parameter(name = "dateBegin", description = "时间起", example = "2026-01-01")
    @Parameter(name = "dateEnd", description = "时间止", example = "2026-12-31")
    @PreAuthorize("@ss.hasPermission('zentao:metric:query')")
    public CommonResult<MetricDataRespVO> getMetricData(
            @RequestParam("code") String code,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "dateBegin", required = false) String dateBegin,
            @RequestParam(value = "dateEnd", required = false) String dateEnd,
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize) {
        return success(metricService.getMetricData(code, scope, dateBegin, dateEnd, pageNo, pageSize));
    }

}
