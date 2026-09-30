package cn.iocoder.yudao.module.zentao.dal.dataobject.kanban;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.KeySequence;
/**
 * 看板列 DO（zt_kanbancolumn）
 *
 * <p>列是「纵向」的维度，{@code limit} 是**在制品上限（WIP）**：-1 表示不限。
 * {@code parent>0} 是子列（拆列产生的），拆过列的父列 {@code parent=-1}。
 *
 * <p>保留字：{@code group} / {@code limit} / {@code order}。
 */
@TableName("zt_kanbancolumn")
@KeySequence("zt_kanbancolumn_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class KanbanColumnDO extends BaseDO {

    @TableId
    private Long id;

    /** 父列：0 顶层 / >0 子列 / -1 已拆分的父列 */
    private Long parent;

    /** 禅道存 column{自己id} */
    private String type;

    private Long region;

    @TableField("`group`")
    private Long groupId;

    private String name;

    private String color;

    /** 在制品上限，-1 = 不限 */
    @TableField("`limit`")
    private Integer limit;

    @TableField("`order`")
    private Integer order;

    private Integer archived;

}
