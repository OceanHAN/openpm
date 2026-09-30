package cn.iocoder.yudao.module.zentao.service.holiday;

import cn.iocoder.yudao.module.zentao.dal.dataobject.holiday.HolidayDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.holiday.HolidayMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 节假日（禅道 {@code module/holiday}）。
 *
 * <h3>这个模块的价值不在「多一张表」，而在「工作日口径」</h3>
 * 禅道里 {@code getActualWorkingDays()} 是**全项目的工作日唯一出口**：
 * 燃尽图/甘特图排期、项目与执行的工期计算、日历都调它。它的判定优先级是：
 *
 * <pre>
 *   补班日（type=working）→ 算工作日（即使是周六周日，即调休）
 *   假期  （type=holiday）→ 不算工作日（即使是周一到周五）
 *   周末  （weekend=2 指周六 + 周日）→ 不算
 *   其它 → 算
 * </pre>
 *
 * <p>本实现在第 36 轮做燃尽图时只按「跳过周末」实现（`noweekend`），这一轮把它接到这里，
 * 于是「国庆假期那几天不占燃尽图的横轴、春节调休的周六要占」这两件事同时成立 ——
 * 这才是禅道的真实口径。
 *
 * <h3>照抄的一个怪癖</h3>
 * 禅道 {@code getActualWorkingDays($begin, $end)} 是**左闭右开**的（循环条件是
 * {@code $currentDay < $end}），只有 {@code $begin == $end} 时才返回那一天。
 * 本实现保持一致，免得燃尽图的点数与禅道对不上。
 */
@Service
@Slf4j
public class HolidayService {

    /** 禅道 {@code config->execution->weekend}：2 表示周末两天都不算工作日 */
    private static final int WEEKEND = 2;

    @Resource
    private HolidayMapper holidayMapper;

    // ==================== 查 ====================

    public List<HolidayDO> getList(String year, String type) {
        return holidayMapper.selectList(year, type);
    }

    public HolidayDO getHoliday(Long id) {
        return holidayMapper.selectById(id);
    }

    /** 可选年份（数据里出现过的年份 + 今年与明年，供下拉用） */
    public List<String> getYears() {
        Set<String> years = new LinkedHashSet<>(holidayMapper.selectYears());
        int current = LocalDate.now().getYear();
        years.add(String.valueOf(current));
        years.add(String.valueOf(current + 1));
        List<String> list = new ArrayList<>(years);
        list.sort((a, b) -> b.compareTo(a));
        return list;
    }

    // ==================== 写 ====================

    public Long create(HolidayDO holiday) {
        fillYear(holiday);
        holidayMapper.insert(holiday);
        return holiday.getId();
    }

    public void update(HolidayDO holiday) {
        fillYear(holiday);
        holidayMapper.updateById(holiday);
    }

    public void delete(Long id) {
        holidayMapper.deleteById(id);
    }

    /** year 由 begin 推出来（禅道也是这么存的：按年筛选时直接用这一列） */
    private void fillYear(HolidayDO holiday) {
        if (holiday.getBegin() != null) {
            holiday.setYear(String.valueOf(holiday.getBegin().getYear()));
        }
    }

    // ==================== 工作日判定（全项目的唯一出口） ====================

    /** 某天是否是假期（禅道 isHoliday） */
    public boolean isHoliday(LocalDate date) {
        return !holidayMapper.selectListByRange("holiday", date, date).isEmpty();
    }

    /** 某天是否是补班（禅道 isWorkingDay） */
    public boolean isWorkingDay(LocalDate date) {
        return !holidayMapper.selectListByRange("working", date, date).isEmpty();
    }

    /**
     * 实际工作日列表（禅道 {@code getActualWorkingDays}）。
     *
     * <p><b>左闭右开</b>：[begin, end)，只有 begin == end 时返回那一天。
     */
    public List<LocalDate> getActualWorkingDays(LocalDate begin, LocalDate end) {
        List<LocalDate> result = new ArrayList<>();
        if (begin == null || end == null || begin.isAfter(end)) {
            return result;
        }
        Set<LocalDate> workingDays = expand("working", begin, end);
        Set<LocalDate> holidays = expand("holiday", begin, end);

        if (begin.equals(end)) {
            if (workingDays.contains(begin)) {
                return List.of(begin);
            }
            if (holidays.contains(begin) || isWeekend(begin)) {
                return result;
            }
            return List.of(begin);
        }
        for (LocalDate date = begin; date.isBefore(end); date = date.plusDays(1)) {
            if (workingDays.contains(date)) {
                result.add(date);
                continue;
            }
            if (holidays.contains(date) || isWeekend(date)) {
                continue;
            }
            result.add(date);
        }
        return result;
    }

    /** 区间内的工作日天数（左闭右开，与上面保持一致） */
    public int countWorkingDays(LocalDate begin, LocalDate end) {
        return getActualWorkingDays(begin, end).size();
    }

    /** 某天是不是工作日（单日判定，含当天；给日历/工期用） */
    public boolean isWorkday(LocalDate date) {
        if (isWorkingDay(date)) {
            return true;
        }
        return !isHoliday(date) && !isWeekend(date);
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek weekDay = date.getDayOfWeek();
        return (WEEKEND == 2 && weekDay == DayOfWeek.SATURDAY) || weekDay == DayOfWeek.SUNDAY;
    }

    /** 把区间内的假期/补班记录展开成逐日集合 */
    private Set<LocalDate> expand(String type, LocalDate begin, LocalDate end) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (HolidayDO record : holidayMapper.selectListByRange(type, begin, end)) {
            if (record.getBegin() == null || record.getEnd() == null) {
                continue;
            }
            LocalDate from = record.getBegin().isBefore(begin) ? begin : record.getBegin();
            LocalDate to = record.getEnd().isAfter(end) ? end : record.getEnd();
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                dates.add(date);
            }
        }
        return dates;
    }

}
