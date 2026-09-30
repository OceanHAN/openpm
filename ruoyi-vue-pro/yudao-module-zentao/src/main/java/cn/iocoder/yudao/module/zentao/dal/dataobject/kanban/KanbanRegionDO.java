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
 * 看板区域 DO（zt_kanbanregion）
 *
 * <p>一个看板可以有多个区域，每个区域是一块独立的板（自己的分组、泳道、列）。
 * 区域名在同一个 (kanban, space) 下唯一（禅道 createRegion 的 unique 校验）。
 *
 * <p>保留字：{@code order}。
 */
@TableName("zt_kanbanregion")
@KeySequence("zt_kanbanregion_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class KanbanRegionDO extends BaseDO {

    @TableId
    private Long id;

    private Long space;

    private Long kanban;

    private String name;

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

}
