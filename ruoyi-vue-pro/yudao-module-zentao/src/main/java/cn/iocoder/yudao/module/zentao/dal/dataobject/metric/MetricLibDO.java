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
 * 度量数据 DO（zt_metriclib）
 *
 * <p>一行 = 一个「维度组合」在某个「时间粒度」上的值：
 * <pre>
 *   维度：system / program / project / product / execution / user / dept / code / pipeline / repo
 *   时间：year[/month[/day]] 或 year+week（dateType=nodate 时都不填，靠 date 取当天快照）
 *   值  ：value（字符串，单位看 zt_metric.unit）
 * </pre>
 * 记录的主键逻辑是「度量项 + 维度 + 时间」，重算前按周期清理（禅道 clearOutDatedRecords）。
 *
 * <p>保留字：{@code system} / {@code user} / {@code value} / {@code date}。
 */
@TableName("zt_metriclib")
@KeySequence("zt_metriclib_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MetricLibDO extends BaseDO {

    @TableId
    private Long id;

    @TableField("metricID")
    private Long metricID;

    @TableField("metricCode")
    private String metricCode;

    /** 1 = 系统级度量的那一行 */
    @TableField("`system`")
    private Integer system;

    private Long program;

    private Long project;

    private Long product;

    private Long execution;

    private String code;

    private String pipeline;

    private String repo;

    @TableField("`user`")
    private String user;

    private String dept;

    private String year;

    private String month;

    private String week;

    private String day;

    @TableField("`value`")
    private String value;

    /** cron 定时 / inference 人工触发 */
    @TableField("calcType")
    private String calcType;

    @TableField("calculatedBy")
    private String calculatedBy;

    @TableField("`date`")
    private LocalDateTime date;

}
