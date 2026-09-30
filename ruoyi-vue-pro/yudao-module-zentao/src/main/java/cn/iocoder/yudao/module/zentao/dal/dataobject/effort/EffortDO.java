package cn.iocoder.yudao.module.zentao.dal.dataobject.effort;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工时明细 DO
 *
 * 对应禅道 {@code zt_effort}。这是「谁在什么时候、为哪个对象、花了多少小时」的流水表，
 * 禅道用它一张表同时记录任务/需求/缺陷的工时（{@code objectType} + {@code objectID} 区分）。
 * 本实现只落任务工时，但字段保留 {@code objectType} 以便日后扩展。
 *
 * <h3>最关键的一个字段：left</h3>
 * 每条工时都会顺手声明「这之后还剩多少」（{@link #left}）。任务的剩余工时**以最后一条工时的
 * left 为准**，不是 estimate - consumed 算出来的。所以：
 * <pre>
 *   task.consumed = SUM(effort.consumed)        // 加总
 *   task.left     = 最后一条 effort.left        // 以最后一次声明为准
 * </pre>
 * 见 {@code EffortServiceImpl#recomputeTask}。
 *
 * 注意 {@code date}、{@code left}、{@code begin}、{@code end}、{@code order} 都是关键字/保留字，必须加反引号。
 */
@TableName("zt_effort")
@KeySequence("zt_effort_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class EffortDO extends BaseDO {

    @TableId
    private Long id;

    // ==================== 归属对象 ====================

    /**
     * 对象类型，本实现固定为 task
     */
    @TableField("objectType")
    private String objectType;

    /**
     * 对象编号（任务编号）
     */
    @TableField("objectID")
    private Long objectID;

    /**
     * 所属产品（禅道原字段，逗号列表；本实现取需求所属产品）
     */
    private String product;
    private Long project;
    private Long execution;

    // ==================== 内容 ====================

    /**
     * 报工时的账号
     */
    private String account;

    /**
     * 做了什么
     */
    private String work;

    /**
     * 工作日期。列名 date 是 MySQL 关键字
     */
    @TableField("`date`")
    private LocalDate date;

    /**
     * **这之后还剩多少工时**。列名 left 是 MySQL 保留字（LEFT 函数）
     */
    @TableField("`left`")
    private BigDecimal left;

    /**
     * 本次消耗工时
     */
    private BigDecimal consumed;

    /**
     * 开始时间，四位 HHMM。列名 begin 是 JSqlParser 保留字
     */
    @TableField("`begin`")
    private String begin;

    /**
     * 结束时间，四位 HHMM。列名 end 是 JSqlParser 保留字
     */
    @TableField("`end`")
    private String end;

    private String extra;

    /**
     * 排序，用于「上移/下移」调整工时显示顺序。列名 order 是 MySQL 保留字
     */
    @TableField("`order`")
    private Integer order;

}
