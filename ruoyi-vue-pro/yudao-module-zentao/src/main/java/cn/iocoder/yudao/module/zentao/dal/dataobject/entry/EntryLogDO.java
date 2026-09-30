package cn.iocoder.yudao.module.zentao.dal.dataobject.entry;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调用日志（禅道 {@code zt_log}）。
 *
 * <p>禅道这张表是**通用**的：{@code objectType + objectID} 指向任意对象，
 * 目前只有 entry（应用接入调用）与 webhook（回调日志）在写。本实现先给 entry 用。
 *
 * <p>本表没有 {@code deleted} 列（禅道原样）：日志是只增不改的事实数据，**不继承 BaseDO**。
 */
@TableName("zt_log")
@Data
public class EntryLogDO {

    @TableId
    private Long id;

    /** 对象类型，应用接入恒为 entry */
    @TableField("objectType")
    private String objectType;

    /** 对象编号（zt_entry.id） */
    @TableField("objectID")
    private Long objectID;

    /** 动作（禅道保留字段，应用接入未使用） */
    private Integer action;

    /** 调用时间。列名 date 是关键字 */
    @TableField("`date`")
    private LocalDateTime date;

    /** 被调用的 URL */
    private String url;

    /** 内容类型（禅道保留字段） */
    @TableField("contentType")
    private String contentType;

    /** 请求数据（禅道保留字段） */
    private String data;

    /** 结果（本实现记录校验结果摘要） */
    private String result;

}
