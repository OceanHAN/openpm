package cn.iocoder.yudao.module.zentao.controller.admin.holiday;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.holiday.vo.HolidayRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.holiday.vo.HolidaySaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.holiday.HolidayDO;
import cn.iocoder.yudao.module.zentao.service.holiday.HolidayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 节假日（禅道 {@code module/holiday}）。
 *
 * <p><b>类名带 {@code Zentao} 前缀</b>：延续坑位 #47 的纪律（新模块先搜同名类 / 同名注入字段 / 同名 mapper）。
 */
@Tag(name = "管理后台 - 禅道节假日")
@RestController
@RequestMapping("/zentao/holiday")
@Validated
public class ZentaoHolidayController {

    @Resource
    private HolidayService holidayService;

    @GetMapping("/list")
    @Operation(summary = "节假日列表", description = "按年 / 按类型筛选（holiday 假期、working 补班）")
    @Parameter(name = "year", description = "年份", example = "2026")
    @Parameter(name = "type", description = "类型", example = "holiday")
    @PreAuthorize("@ss.hasPermission('zentao:holiday:query')")
    public CommonResult<List<HolidayRespVO>> getList(@RequestParam(value = "year", required = false) String year,
                                                    @RequestParam(value = "type", required = false) String type) {
        return success(BeanUtils.toBean(holidayService.getList(year, type), HolidayRespVO.class));
    }

    @GetMapping("/years")
    @Operation(summary = "可选年份", description = "数据里出现过的年份 + 今年与明年")
    @PreAuthorize("@ss.hasPermission('zentao:holiday:query')")
    public CommonResult<List<String>> getYears() {
        return success(holidayService.getYears());
    }

    @GetMapping("/get")
    @Operation(summary = "获得节假日")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:holiday:query')")
    public CommonResult<HolidayRespVO> getHoliday(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(holidayService.getHoliday(id), HolidayRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "新增节假日/补班", description = "type=holiday 假期（不算工作日）、type=working 补班（算工作日）")
    @PreAuthorize("@ss.hasPermission('zentao:holiday:create')")
    public CommonResult<Long> create(@Valid @RequestBody HolidaySaveReqVO reqVO) {
        return success(holidayService.create(BeanUtils.toBean(reqVO, HolidayDO.class)));
    }

    @PutMapping("/update")
    @Operation(summary = "修改节假日/补班")
    @PreAuthorize("@ss.hasPermission('zentao:holiday:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody HolidaySaveReqVO reqVO) {
        holidayService.update(BeanUtils.toBean(reqVO, HolidayDO.class));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除节假日/补班")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:holiday:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        holidayService.delete(id);
        return success(true);
    }

    @GetMapping("/working-days")
    @Operation(summary = "某区间的实际工作日",
            description = "禅道 getActualWorkingDays 的口径：补班日算、假期不算、周末不算。"
                    + "注意是**左闭右开**（begin == end 时返回那一天）")
    @Parameter(name = "begin", description = "开始日期（含）", required = true, example = "2026-10-01")
    @Parameter(name = "end", description = "结束日期（**不含**）", required = true, example = "2026-10-08")
    @PreAuthorize("@ss.hasPermission('zentao:holiday:query')")
    public CommonResult<Map<String, Object>> getWorkingDays(
            @RequestParam("begin") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @RequestParam("end") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        List<LocalDate> days = holidayService.getActualWorkingDays(begin, end);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("begin", begin.toString());
        result.put("end", end.toString());
        result.put("count", days.size());
        result.put("days", days.stream().map(LocalDate::toString).toList());
        return success(result);
    }

}
