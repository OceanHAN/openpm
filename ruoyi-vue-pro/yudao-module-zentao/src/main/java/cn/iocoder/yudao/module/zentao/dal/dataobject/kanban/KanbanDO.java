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
 * 看板 DO（zt_kanban）
 *
 * <p>一块板的本体：{@code archived} 是「是否启用归档功能」（不是看板本身是否归档），
 * {@code showWIP} 控制是否显示「卡片数/在制品限额」，{@code fluidBoard} 控制流式列宽。
 *
 * <p>保留字：{@code desc} / {@code order}。
 */
@TableName("zt_kanban")
@KeySequence("zt_kanban_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class KanbanDO extends BaseDO {

    @TableId
    private Long id;

    private Long space;

    private String name;

    private String owner;

    private String team;

    @TableField("`desc`")
    private String desc;

    /** open 公开 / private 私有 / extend 继承空间权限 */
    private String acl;

    private String whitelist;

    /** 是否启用归档功能（1 启用） */
    private Integer archived;

    private Integer performable;

    /** active 正常 / closed 已关闭 */
    private String status;

    @TableField("`order`")
    private Integer order;

    /** 卡片显示数量，0=不限 */
    @TableField("displayCards")
    private Integer displayCards;

    /** 是否显示在制品数量 */
    @TableField("showWIP")
    private Integer showWIP;

    /** 是否流式布局 */
    @TableField("fluidBoard")
    private Integer fluidBoard;

    @TableField("colWidth")
    private Integer colWidth;

    @TableField("minColWidth")
    private Integer minColWidth;

    @TableField("maxColWidth")
    private Integer maxColWidth;

    private String object;

    /** center 居中 / left 居左 */
    private String alignment;

    @TableField("createdBy")
    private String createdBy;

    @TableField("createdDate")
    private LocalDateTime createdDate;

    @TableField("lastEditedBy")
    private String lastEditedBy;

    @TableField("lastEditedDate")
    private LocalDateTime lastEditedDate;

    @TableField("closedBy")
    private String closedBy;

    @TableField("closedDate")
    private LocalDateTime closedDate;

    @TableField("activatedBy")
    private String activatedBy;

    @TableField("activatedDate")
    private LocalDateTime activatedDate;

}
