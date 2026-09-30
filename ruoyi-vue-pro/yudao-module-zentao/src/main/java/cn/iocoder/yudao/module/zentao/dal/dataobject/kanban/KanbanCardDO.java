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
 * 看板卡片 DO（zt_kanbancard）
 *
 * <p>卡片有两种来源：看板自建（{@code fromType} 为空）与「从其他对象导入」
 * （{@code fromType=story/task/bug/...} + {@code fromID}）。
 *
 * <p>保留字 / JSqlParser 雷：{@code desc} / {@code group} / {@code order} /
 * {@code begin} / {@code end}。
 */
@TableName("zt_kanbancard")
@KeySequence("zt_kanbancard_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class KanbanCardDO extends BaseDO {

    @TableId
    private Long id;

    private Long kanban;

    private Long region;

    @TableField("`group`")
    private Long groupId;

    @TableField("fromID")
    private Long fromID;

    /** 来源对象类型，空 = 看板自建卡片 */
    @TableField("fromType")
    private String fromType;

    private String name;

    /** doing 进行中 / done 已完成 */
    private String status;

    private Integer pri;

    @TableField("assignedTo")
    private String assignedTo;

    @TableField("`desc`")
    private String desc;

    @TableField("`begin`")
    private LocalDate begin;

    @TableField("`end`")
    private LocalDate end;

    private BigDecimal estimate;

    private BigDecimal progress;

    private String color;

    private String acl;

    private String whitelist;

    @TableField("`order`")
    private Integer order;

    private Integer archived;

    @TableField("createdBy")
    private String createdBy;

    @TableField("createdDate")
    private LocalDateTime createdDate;

    @TableField("lastEditedBy")
    private String lastEditedBy;

    @TableField("lastEditedDate")
    private LocalDateTime lastEditedDate;

    @TableField("archivedBy")
    private String archivedBy;

    @TableField("archivedDate")
    private LocalDateTime archivedDate;

    @TableField("assignedBy")
    private String assignedBy;

    @TableField("assignedDate")
    private LocalDateTime assignedDate;

}
