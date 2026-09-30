package cn.iocoder.yudao.module.zentao.dal.dataobject.stakeholder;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 干系人 DO（禅道 {@code zt_stakeholder}）
 *
 * <h3>干系人不是团队成员</h3>
 * <pre>
 *   zt_team        团队成员：要干活的人（有角色、有可用工时）
 *   zt_stakeholder 干系人：需要知情/被影响的人（甲方、领导、外部顾问……）
 * </pre>
 * 两张表、两个模块，字段完全不同 —— 别把它们混成一个"人"的概念。
 *
 * <h3>三个要点</h3>
 * <ol>
 *   <li>{@code type} 由 {@code from} 推导（outside → outside，其余 → inside），不是独立字段；</li>
 *   <li>同一个人不能重复加到同一个对象下（禅道 check 是
 *       {@code user unique(objectID = ? AND deleted = '0')}）；</li>
 *   <li>{@code from = 'outside'} 时禅道会在 zt_user 里建一条 type=outside 的记录；
 *       本实现简化为直接存名字（见 README 已知限制）。</li>
 * </ol>
 *
 * <p>保留字：{@code key}、{@code from} 都要加反引号。
 */
@TableName("zt_stakeholder")
@KeySequence("zt_stakeholder_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class StakeholderDO extends BaseDO {

    @TableId
    private Long id;

    /** 对象编号（项目集/项目） */
    @TableField("objectID")
    private Long objectID;

    /** 对象类型：program / project */
    @TableField("objectType")
    private String objectType;

    /** 账号（from=outside 时是外部人员名字） */
    private String user;

    /** inside / outside，见 StakeholderTypeEnum */
    private String type;

    /** 是否关键干系人。列名 key 是 MySQL 保留字 */
    @TableField("`key`")
    private Integer key;

    /** 来源，见 StakeholderFromEnum。列名 from 是 MySQL 保留字 */
    @TableField("`from`")
    private String from;

    // ==================== 禅道自带的审计列（与 BaseDO 并存） ====================

    @TableField("createdBy")
    private String createdBy;
    @TableField("createdDate")
    private LocalDateTime createdDate;
    @TableField("editedBy")
    private String editedBy;
    @TableField("editedDate")
    private LocalDateTime editedDate;

}
