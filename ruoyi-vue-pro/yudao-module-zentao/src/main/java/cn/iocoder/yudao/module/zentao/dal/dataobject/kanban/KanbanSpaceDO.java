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
 * 看板空间 DO（zt_kanbanspace）
 *
 * <p>看板的容器，三个类型：private 私人空间 / cooperation 协作空间 / public 公共空间。
 * 空间有成员（team）与白名单，看板可以继承空间的访问权限（kanban.acl='extend'）。
 *
 * <p>保留字：{@code desc} / {@code order}。
 */
@TableName("zt_kanbanspace")
@KeySequence("zt_kanbanspace_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class KanbanSpaceDO extends BaseDO {

    @TableId
    private Long id;

    private String name;

    /** private 私人 / cooperation 协作 / public 公共 */
    private String type;

    private String owner;

    /** 团队成员，逗号列表 */
    private String team;

    @TableField("`desc`")
    private String desc;

    /** open 公开 / private 私有 */
    private String acl;

    /** 白名单，逗号列表 */
    private String whitelist;

    /** active 正常 / closed 已关闭 */
    private String status;

    @TableField("`order`")
    private Integer order;

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
