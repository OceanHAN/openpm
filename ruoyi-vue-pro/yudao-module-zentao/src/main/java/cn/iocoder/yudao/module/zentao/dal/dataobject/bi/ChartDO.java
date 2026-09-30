package cn.iocoder.yudao.module.zentao.dal.dataobject.bi;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * BI 图表 DO（zt_chart）。
 *
 * <p>查询设置放在 {@code settings} JSON 里：{@code {dimensionField, metricField, agg, limit, sort}}
 * —— 即「按哪个维度分组、对哪个字段做什么聚合」；禅道的 {@code dimension} 是指向
 * {@code zt_dimension} 的编号，本实现不使用（用 settings 里的字段名，见 README 3.35）。
 *
 * <p>保留字：{@code group} / {@code desc}。
 */
@TableName("zt_chart")
@KeySequence("zt_chart_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ChartDO extends BaseDO {

    @TableId
    private Long id;

    private String name;

    private String code;

    private String driver;

    /** builder / sql */
    private String mode;

    /** 禅道指向 zt_dimension；本实现用 settings.dimensionField */
    private Long dimension;

    /** 数据视图代码（本实现新增：图表引用哪个数据视图） */
    @TableField("viewCode")
    private String viewCode;

    /** pie / line / cluBarX / stackedBar / ... */
    private String type;

    @TableField("`group`")
    private String group;

    @TableField("`desc`")
    private String desc;

    private String acl;

    private String whitelist;

    /** 查询设置 JSON */
    private String settings;

    /** 过滤器 JSON */
    private String filters;

    private Integer step;

    private String fields;

    private String langs;

    @TableField("`sql`")
    private String sql;

    private String version;

    /** draft 草稿 / published 已发布 */
    private String stage;

    private Integer builtin;

    private String objects;

    @TableField("createdBy")
    private String createdBy;

    @TableField("createdDate")
    private LocalDateTime createdDate;

    @TableField("editedBy")
    private String editedBy;

    @TableField("editedDate")
    private LocalDateTime editedDate;

}
