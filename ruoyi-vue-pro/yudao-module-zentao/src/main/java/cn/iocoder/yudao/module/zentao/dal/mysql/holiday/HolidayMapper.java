package cn.iocoder.yudao.module.zentao.dal.mysql.holiday;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.holiday.HolidayDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface HolidayMapper extends BaseMapperX<HolidayDO> {

    default List<HolidayDO> selectList(String year, String type) {
        return selectList(new LambdaQueryWrapperX<HolidayDO>()
                .eqIfPresent(HolidayDO::getYear, year)
                .eqIfPresent(HolidayDO::getType, type)
                .orderByAsc(HolidayDO::getBegin));
    }

    /** 与 [begin, end] 有交集的记录（禅道 getHolidays / getWorkingDays 的相同条件） */
    default List<HolidayDO> selectListByRange(String type, LocalDate begin, LocalDate end) {
        return selectList(new LambdaQueryWrapperX<HolidayDO>()
                .eq(HolidayDO::getType, type)
                .le(HolidayDO::getBegin, end)
                .ge(HolidayDO::getEnd, begin)
                .orderByAsc(HolidayDO::getBegin));
    }

    /** 数据里出现过的年份（去重、倒序） */
    @Select("SELECT DISTINCT year FROM zt_holiday WHERE deleted = 0 ORDER BY year DESC")
    List<String> selectYears();

}
