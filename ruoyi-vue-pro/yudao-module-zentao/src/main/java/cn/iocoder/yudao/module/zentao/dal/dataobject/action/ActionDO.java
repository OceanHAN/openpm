package cn.iocoder.yudao.module.zentao.dal.dataobject.action;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 操作日志 DO
 *
 * 对应禅道 {@code zt_action}：一次操作一行，回答「谁、在什么时候、对哪个对象、做了什么」。
 * 字段级的变更明细挂在 {@link HistoryDO} 上。
 *
 * 注意 {@code read} 是 MySQL 保留字，列名必须加反引号，因此 Java 字段命名为 readFlag。
 */
@TableName("zt_action")
@KeySequence("zt_action_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ActionDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 对象类型，如 story
     */
    @TableField("objectType")
    private String objectType;

    /**
     * 对象编号
     */
    @TableField("objectID")
    private Long objectID;

    /**
     * 所属产品，多个逗号分隔
     */
    private String product;

    /**
     * 所属项目
     */
    private Long project;

    /**
     * 所属执行
     */
    private Long execution;

    /**
     * 操作人账号
     */
    private String actor;

    /**
     * 动作
     *
     * 枚举 {@link cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum}
     */
    private String action;

    /**
     * 操作时间。列名是 MySQL 关键字，显式指定
     */
    @TableField("`date`")
    private LocalDateTime date;

    /**
     * 备注
     */
    private String comment;

    /**
     * 附件
     */
    private String files;

    /**
     * 额外信息
     */
    private String extra;

    /**
     * 是否已读。列名 read 是 MySQL 保留字
     */
    @TableField("`read`")
    private Integer readFlag;

    /**
     * 是否已登记工时
     */
    private Integer efforted;

    /**
     * 视图
     */
    private String vision;

}
