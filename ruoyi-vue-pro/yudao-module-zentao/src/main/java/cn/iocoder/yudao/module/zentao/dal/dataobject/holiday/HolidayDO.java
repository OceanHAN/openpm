package cn.iocoder.yudao.module.zentao.dal.dataobject.holiday;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 节假日 / 补班（禅道 {@code zt_holiday}）。
 *
 * <p>一张表两种记录：{@code type='holiday'} 是假期（这段日期不算工作日）、
 * {@code type='working'} 是补班（这段日期算工作日，即调休）。
 *
 * <p>列名 {@code desc} 是 MySQL 关键字（坑位 #10），{@code begin}/{@code end} 也是（坑位 #11）。
 */
@TableName("zt_holiday")
@Data
public class HolidayDO extends BaseDO {

    @TableId
    private Long id;

    /** 名称 */
    private String name;

    /** 类型：holiday 假期 / working 补班 */
    private String type;

    /** 描述。列名 desc 是关键字 */
    @TableField("`desc`")
    private String desc;

    /** 年份 */
    private String year;

    /** 开始日期（含）。列名 begin 是 JSqlParser 的关键字 */
    @TableField("`begin`")
    private LocalDate begin;

    /** 结束日期（含）。列名 end 是关键字 */
    @TableField("`end`")
    private LocalDate end;

}
