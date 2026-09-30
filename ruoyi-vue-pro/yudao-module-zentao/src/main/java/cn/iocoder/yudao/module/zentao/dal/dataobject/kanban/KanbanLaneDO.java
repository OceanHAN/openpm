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
 * 看板泳道 DO（zt_kanbanlane）
 *
 * <p>泳道是「横向」的维度：{@code type=common} 是普通泳道，story/bug/task 是
 * 「按对象分泳道」（研发看板会用）。{@code groupby}/{@code extra} 记录分组方式。
 *
 * <p>保留字：{@code group} / {@code order}。
 */
@TableName("zt_kanbanlane")
@KeySequence("zt_kanbanlane_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class KanbanLaneDO extends BaseDO {

    @TableId
    private Long id;

    /** 所属执行（研发看板才用） */
    private Long execution;

    /** common 普通 / story 需求 / bug 缺陷 / task 任务 */
    private String type;

    private Long region;

    @TableField("`group`")
    private Long groupId;

    private String groupby;

    private String extra;

    private String name;

    private String color;

    @TableField("`order`")
    private Integer order;

    @TableField("lastEditedTime")
    private LocalDateTime lastEditedTime;

}
