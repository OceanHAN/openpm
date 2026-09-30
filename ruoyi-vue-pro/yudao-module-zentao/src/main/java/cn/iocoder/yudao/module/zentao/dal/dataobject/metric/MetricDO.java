package cn.iocoder.yudao.module.zentao.dal.dataobject.metric;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 度量项定义 DO（zt_metric）
 *
 * <p>一个度量项 = 目的（为什么量）+ 范围（量谁）+ 对象（量什么）+ 时间维度（多久量一次）+
 * 口径定义（怎么算）。**口径实现在代码里**（禅道是 414 个 calc 类，本迁移是 Java 注册表），
 * {@code code} 是把两边对上的钥匙。
 *
 * <p>保留字：{@code desc} / {@code order} / {@code when}。
 */
@TableName("zt_metric")
@KeySequence("zt_metric_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MetricDO extends BaseDO {

    @TableId
    private Long id;

    /** scale 规模 / qc 质量 / hour 工时 / cost 成本 / rate 效率 / time 工期 */
    private String purpose;

    /** system / program / project / product / execution / user */
    private String scope;

    /** story / bug / case / task / effort / ... */
    private String object;

    /** wait 未发布 / released 已发布 / delisted 已下架 */
    private String stage;

    /** php 代码口径 / sql SQL 口径 */
    private String type;

    private String name;

    private String alias;

    /** 度量项代码，与口径实现一一对应 */
    private String code;

    /** count / measure / hour / day / manday / percentage / times / people / row */
    private String unit;

    /** 时间维度：year / month / week / day / nodate（驼峰列名） */
    @TableField("dateType")
    private String dateType;

    private String collector;

    @TableField("`desc`")
    private String desc;

    private String definition;

    @TableField("`when`")
    private String when;

    private String event;

    @TableField("cronCFG")
    private String cronCFG;

    private String time;

    @TableField("createdBy")
    private String createdBy;

    @TableField("createdDate")
    private LocalDateTime createdDate;

    @TableField("editedBy")
    private String editedBy;

    @TableField("editedDate")
    private LocalDateTime editedDate;

    @TableField("implementedBy")
    private String implementedBy;

    @TableField("implementedDate")
    private LocalDateTime implementedDate;

    @TableField("delistedBy")
    private String delistedBy;

    @TableField("delistedDate")
    private LocalDateTime delistedDate;

    /** 1 = 内置口径（随代码走） */
    private Integer builtin;

    @TableField("fromID")
    private Long fromID;

    @TableField("`order`")
    private Integer order;

    @TableField("lastCalcRows")
    private Integer lastCalcRows;

    @TableField("lastCalcTime")
    private LocalDateTime lastCalcTime;

}
