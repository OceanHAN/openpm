package cn.iocoder.yudao.module.zentao.dal.dataobject.action;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志的字段变更明细 DO
 *
 * 对应禅道 {@code zt_history}：挂在某个 {@link ActionDO} 下，一个字段一行。
 * 长文本字段（需求描述、验收标准等）额外记录 {@code diff}，这就是禅道「变更历史」
 * 能精确显示「哪一行被改成什么」的原因。
 */
@TableName("zt_history")
@KeySequence("zt_history_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HistoryDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 所属操作日志编号
     */
    private Long action;

    /**
     * 字段名
     */
    private String field;

    /**
     * 旧值。列名 old 是 MySQL 关键字，显式指定
     */
    @TableField("`old`")
    private String old;

    /**
     * 旧值的展示文本
     */
    @TableField("oldValue")
    private String oldValue;

    /**
     * 新值。列名 new 是 MySQL 关键字，显式指定
     */
    @TableField("`new`")
    private String new_;

    /**
     * 新值的展示文本
     */
    @TableField("newValue")
    private String newValue;

    /**
     * 长文本差异，纯文本统一 diff 风格（- 表示删除行，+ 表示新增行）
     */
    private String diff;

}
