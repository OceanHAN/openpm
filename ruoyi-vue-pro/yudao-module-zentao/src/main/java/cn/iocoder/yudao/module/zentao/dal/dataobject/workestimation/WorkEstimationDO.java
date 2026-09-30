package cn.iocoder.yudao.module.zentao.dal.dataobject.workestimation;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目工作量估算 DO（禅道 {@code zt_workestimation}）
 *
 * <h3>各字段的关系</h3>
 * <pre>
 *   duration       = scale / productivity      （规模 ÷ 生产率 = 工期）
 *   totalLaborCost = duration × dayHour × unitLaborCost
 * </pre>
 * 禅道开源版里这张表**只有 model（getBudget）没有控制器/视图** ——
 * 界面上用不到它（config 里 linkMap 指向的 workestimation/index 并不存在），
 * 属于 IPD / 企业版的能力。本实现按表结构 + 上面的公式补齐，接口保留。
 */
@TableName("zt_workestimation")
@KeySequence("zt_workestimation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkEstimationDO extends BaseDO {

    @TableId
    private Long id;

    /** 项目编号 */
    private Long project;

    /** 规模（人天） */
    private BigDecimal scale;

    /** 生产率 */
    private BigDecimal productivity;

    /** 工期（天）= scale / productivity */
    private BigDecimal duration;

    /** 单位人工成本（元/人天） */
    @TableField("unitLaborCost")
    private BigDecimal unitLaborCost;

    /** 总人工成本 = duration × dayHour × unitLaborCost */
    @TableField("totalLaborCost")
    private BigDecimal totalLaborCost;

    /** 每天工时 */
    @TableField("dayHour")
    private BigDecimal dayHour;

    @TableField("assignedTo")
    private String assignedTo;
    @TableField("assignedDate")
    private LocalDateTime assignedDate;

    // ==================== 禅道自带的审计列 ====================

    @TableField("createdBy")
    private String createdBy;
    @TableField("createdDate")
    private LocalDateTime createdDate;
    @TableField("editedBy")
    private String editedBy;
    @TableField("editedDate")
    private LocalDateTime editedDate;

}
