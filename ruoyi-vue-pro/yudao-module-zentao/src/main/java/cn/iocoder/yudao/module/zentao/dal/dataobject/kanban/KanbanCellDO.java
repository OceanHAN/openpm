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
 * 看板格子 DO（zt_kanbancell）
 *
 * <p>格子 = 泳道 × 列，{@code cards} 是**逗号列表**（两头带逗号，如 {@code ,96501,}），
 * 卡片在板上的位置与顺序都由它决定。增删都是字符串替换，顺序就是列表顺序。
 *
 * <p><b>这张表没有 {@code deleted} 列</b>（禅道原样）：格子是纯布局数据，
 * 删泳道/删列时整批清理。
 *
 * <p>保留字：{@code column}（MySQL 保留字）。
 */
@TableName("zt_kanbancell")
@Data
public class KanbanCellDO {

    @TableId
    private Long id;

    private Long kanban;

    private Long lane;

    @TableField("`column`")
    private Long column;

    /** 格子类型（跟随泳道 type） */
    private String type;

    /** 卡片编号逗号列表 */
    private String cards;

}
