package cn.iocoder.yudao.module.zentao.dal.dataobject.todo;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待办 DO（禅道 {@code zt_todo}）
 *
 * <h3>待办不是任务</h3>
 * {@code zt_task} 是项目里要交付的工作（有执行、有工时、有需求），
 * {@code zt_todo} 是「我今天要干的几件事」——可以完全不挂任何项目（type='custom'），
 * 也可以指向一个已有对象（type='task'/'bug'/'story'/'testtask' + objectID）当快捷入口。
 *
 * <h3>account 与 assignedTo 的区别</h3>
 * <pre>
 *   account    这条待办属于谁的清单（谁的「我的地盘」里能看到）
 *   assignedTo 实际上要谁做（可以被指派给别人）
 * </pre>
 * 禅道「我的待办」的查询条件是
 * {@code assignedTo = 我 OR finishedBy = 我 OR closedBy = 我}
 * （module/todo/tao.php#getListBy），而不是按 account 查。
 * 私有待办（private=1）在别人的列表里只显示「这是私有待办」。
 *
 * 注意 date / begin / end / desc 都是关键字，必须加反引号。
 */
@TableName("zt_todo")
@KeySequence("zt_todo_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TodoDO extends BaseDO {

    @TableId
    private Long id;

    /** 归属账号（谁的待办清单） */
    private String account;

    /** 哪天做。列名 date 是 MySQL 关键字 */
    @TableField("`date`")
    private LocalDate date;

    /** 开始时间 HHMM。列名 begin 是 JSqlParser 保留字 */
    @TableField("`begin`")
    private String begin;

    /** 结束时间 HHMM。列名 end 是 JSqlParser 保留字 */
    @TableField("`end`")
    private String end;

    /** 关联反馈（禅道原字段，本实现不使用） */
    private Long feedback;

    /** 类型，见 TodoTypeEnum */
    private String type;

    /** 是否周期待办 */
    private Integer cycle;

    /** 关联对象编号（type 不是 custom/cycle 时有效）。驼峰列名 */
    @TableField("objectID")
    private Long objectID;

    /** 优先级 1~4 */
    private Integer pri;

    /** 待办名称 */
    private String name;

    /** 描述。列名 desc 是 MySQL 关键字 */
    @TableField("`desc`")
    private String desc;

    /** 状态，见 TodoStatusEnum */
    private String status;

    /** 是否私有（只有自己能看到内容） */
    @TableField("`private`")
    private Integer privateFlag;

    /** 周期配置（禅道原字段） */
    private String config;

    // ==================== 指派与生命周期（都是禅道驼峰列名） ====================

    @TableField("assignedTo")
    private String assignedTo;
    @TableField("assignedBy")
    private String assignedBy;
    @TableField("assignedDate")
    private LocalDateTime assignedDate;
    @TableField("finishedBy")
    private String finishedBy;
    @TableField("finishedDate")
    private LocalDateTime finishedDate;
    @TableField("closedBy")
    private String closedBy;
    @TableField("closedDate")
    private LocalDateTime closedDate;

    private String vision;

}
