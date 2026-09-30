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
 * BI 数据视图 DO（zt_dataview）—— 数据集/数据表。
 *
 * <p>禅道里 {@code mode='builder'} 是可视化建视图、{@code mode='sql'} 是写一条 SQL；
 * 本实现只做 SQL 模式（见 README 3.35），{@code sql} 必须是**只读 SELECT**，
 * 执行前过 {@code SqlGuard} 白名单。
 *
 * <p>保留字：{@code group} / {@code view} / {@code sql}。
 */
@TableName("zt_dataview")
@KeySequence("zt_dataview_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class DataViewDO extends BaseDO {

    @TableId
    private Long id;

    @TableField("`group`")
    private Long group;

    private String name;

    /** 代码，图表用它引用数据视图 */
    private String code;

    /** builder 可视化建 / sql 写 SQL */
    private String mode;

    private String driver;

    @TableField("`view`")
    private String view;

    @TableField("`sql`")
    private String sql;

    /** 字段定义 JSON */
    private String fields;

    private String langs;

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
