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

/**
 * 看板分组 DO（zt_kanbangroup）
 *
 * <p>泳道与列都挂在一个分组下（{@code lane.group} / {@code column.group}）：
 * 同一个分组里的泳道共享同一批列。一个区域默认建一个分组，所以「默认布局」里
 * 泳道和列是通过 group 关联起来的。
 *
 * <p><b>这张表没有 {@code deleted} 列</b>（禅道原样），分组随区域一起删除。
 */
@TableName("zt_kanbangroup")
@Data
public class KanbanGroupDO {

    @TableId
    private Long id;

    private Long kanban;

    private Long region;

    @TableField("`order`")
    private Integer order;

}
