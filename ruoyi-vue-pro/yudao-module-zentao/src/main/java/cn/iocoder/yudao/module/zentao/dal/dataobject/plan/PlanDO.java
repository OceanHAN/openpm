package cn.iocoder.yudao.module.zentao.dal.dataobject.plan;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 产品计划 DO
 *
 * <p>对应禅道 {@code zt_productplan}。
 *
 * <h3>三个特殊之处（详见 deploy/sql/17-zt_productplan.sql 的注释）</h3>
 * <ol>
 *   <li>{@code branch} 是 <b>varchar(255) 的逗号列表</b>，一个计划可以覆盖多个分支/平台
 *       （需求/缺陷上的 branch 是单值 bigint，别照抄）</li>
 *   <li>{@code parent} 三态：0 独立 / &gt;0 子计划 / <b>-1 该计划有子计划</b></li>
 *   <li>{@code order} 列是 text，存的是「计划内需求的排序数据」，不是排序数字，
 *       所以 DO 里叫 {@code planOrder}，避免和别的表的 int order 混淆</li>
 * </ol>
 *
 * <p>关键字：{@code desc} / {@code order} 需要反引号。
 */
@TableName("zt_productplan")
@KeySequence("zt_productplan_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PlanDO extends BaseDO {

    /**
     * 有子计划的父计划标记。禅道约定 parent = -1 表示「我下面有子计划」
     */
    public static final Long PARENT_HAS_CHILDREN = -1L;

    /**
     * 独立计划（既没有父计划、也没有子计划）
     */
    public static final Long PARENT_NONE = 0L;

    /**
     * 「待定」日期哨兵值。禅道：$config->productplan->future = '2030-01-01'
     */
    public static final LocalDate FUTURE_DATE = LocalDate.of(2030, 1, 1);

    @TableId
    private Long id;

    /**
     * 所属产品
     */
    private Long product;

    /**
     * 分支/平台，多个用逗号分隔；{@code 0} 表示主干
     */
    private String branch;

    /**
     * 0 独立 / &gt;0 父计划编号 / -1 该计划有子计划
     */
    private Long parent;

    /**
     * 计划名称
     */
    private String title;

    /**
     * 状态。枚举 {@link cn.iocoder.yudao.module.zentao.enums.plan.PlanStatusEnum}
     */
    private String status;

    /**
     * 描述。列名 {@code desc} 是保留字
     */
    @TableField("`desc`")
    private String desc;

    /**
     * 开始日期。待定计划为 {@link #FUTURE_DATE}
     */
    private LocalDate begin;

    /**
     * 结束日期。待定计划为 {@link #FUTURE_DATE}
     */
    private LocalDate end;

    /**
     * 完成时间。驼峰列名，必须显式声明（MyBatis-Plus 默认会转成 finished_date）
     */
    @TableField("finishedDate")
    private LocalDateTime finishedDate;

    /**
     * 关闭时间。驼峰列名
     */
    @TableField("closedDate")
    private LocalDateTime closedDate;

    /**
     * 计划内需求的排序数据。列名 {@code order} 是保留字，且类型是 text
     */
    @TableField("`order`")
    private String planOrder;

    /**
     * 关闭原因：done / cancel。驼峰列名
     */
    @TableField("closedReason")
    private String closedReason;

    /**
     * 创建人。驼峰列名
     */
    @TableField("createdBy")
    private String createdBy;

    /**
     * 创建时间（禅道 createdDate，与 yudao 的 createTime 并存）。驼峰列名
     */
    @TableField("createdDate")
    private LocalDateTime createdDate;

}
