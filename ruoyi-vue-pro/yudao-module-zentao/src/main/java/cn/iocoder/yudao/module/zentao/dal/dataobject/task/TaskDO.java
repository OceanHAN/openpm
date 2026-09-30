package cn.iocoder.yudao.module.zentao.dal.dataobject.task;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.zentao.enums.task.TaskStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务 DO
 *
 * 对应禅道 {@code zt_task}。字段命名保持禅道风格，驼峰列名用 {@link TableField} 显式声明。
 *
 * <h3>工时模型（禅道任务的精髓）</h3>
 * <pre>
 *   estimate  预计工时
 *   consumed  已消耗工时
 *   left      剩余工时
 * </pre>
 * 三者互相牵制：开始任务时可以登记消耗，完成任务时 {@code left} 应归零。
 * 项目的进度、燃尽图、成员工时统计全部由这三个数字推导，是本模块最不能出错的部分。
 *
 * 注意 {@code desc} 是 MySQL 关键字，必须加反引号。
 */
@TableName("zt_task")
@KeySequence("zt_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskDO extends BaseDO {

    @TableId
    private Long id;

    // ==================== 归属 ====================

    private Long project;
    private Long execution;
    private Long module;
    /**
     * 关联需求
     */
    private Long story;

    /**
     * 建任务时需求的版本（冻结）。驼峰列名
     */
    @TableField("storyVersion")
    private Integer storyVersion;
    /**
     * 来源缺陷（由 Bug 转任务时记录）
     */
    @TableField("fromBug")
    private Long fromBug;

    // ==================== 内容 ====================

    private String name;
    private String type;
    private Integer pri;
    /**
     * 预计工时
     */
    private BigDecimal estimate;
    /**
     * 已消耗工时
     */
    private BigDecimal consumed;
    /**
     * 剩余工时。列名 left 是 MySQL 保留字（LEFT 函数），必须加反引号
     */
    @TableField("`left`")
    private BigDecimal left;
    private LocalDate deadline;
    private String keywords;
    /**
     * 任务描述。列名 desc 是 MySQL 关键字
     */
    @TableField("`desc`")
    private String desc;
    private Integer version;

    // ==================== 状态机 ====================

    /**
     * 状态
     *
     * 枚举 {@link TaskStatusEnum}
     */
    private String status;

    // ==================== 生命周期 ====================

    @TableField("openedBy")
    private String openedBy;
    @TableField("openedDate")
    private LocalDateTime openedDate;
    @TableField("assignedTo")
    private String assignedTo;
    @TableField("assignedDate")
    private LocalDateTime assignedDate;
    @TableField("estStarted")
    private LocalDate estStarted;
    @TableField("realStarted")
    private LocalDateTime realStarted;
    @TableField("finishedBy")
    private String finishedBy;
    @TableField("finishedDate")
    private LocalDateTime finishedDate;
    @TableField("canceledBy")
    private String canceledBy;
    @TableField("canceledDate")
    private LocalDateTime canceledDate;
    @TableField("closedBy")
    private String closedBy;
    @TableField("closedDate")
    private LocalDateTime closedDate;
    @TableField("closedReason")
    private String closedReason;
    @TableField("lastEditedBy")
    private String lastEditedBy;
    @TableField("lastEditedDate")
    private LocalDateTime lastEditedDate;
    @TableField("activatedDate")
    private LocalDateTime activatedDate;

}
