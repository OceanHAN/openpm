package cn.iocoder.yudao.module.zentao.dal.dataobject.burn;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 执行燃尽图快照（禅道 {@code zt_burn}）。
 *
 * <p>一行 = 某个执行在某一天的「原计划工时 / 剩余工时 / 已消耗工时 / 需求规模」。
 * 燃尽图的折线就是这些快照连起来的，**不是每次打开页面实时算的** ——
 * 任务会被改、会被删、完成后 estimate 会被改写，只有落库的历史值才留得住。
 *
 * <h3>两个注意点</h3>
 * <ol>
 *   <li><b>不继承 BaseDO</b>：禅道原表没有 {@code deleted} 列。快照是只增不改的事实数据，
 *       {@code computeBurn} 对当天那一行做 REPLACE（唯一键 {@code execution + date + task}）；</li>
 *   <li>列名 {@code date} 加反引号（与 {@code zt_action.date} 同一处理）。</li>
 * </ol>
 */
@TableName("zt_burn")
@Data
public class BurnDO {

    @TableId
    private Long id;

    /** 执行编号（zt_project 里 type=sprint/stage 的行） */
    private Long execution;

    /** 产品编号（禅道保留字段，本实现恒为 0） */
    private Long product;

    /** 任务编号（禅道多任务燃尽图用，本实现恒为 0） */
    private Long task;

    /** 快照日期。列名 date 是 MySQL 关键字，必须加反引号 */
    @TableField("`date`")
    private LocalDate date;

    /** 原计划工时（扣掉已完成任务的预计） */
    private BigDecimal estimate;

    /** 剩余工时（扣掉已关闭任务的剩余）。列名 left 是 MySQL 保留字，必须加反引号（坑位 #10） */
    @TableField("`left`")
    private BigDecimal left;

    /** 已消耗工时 */
    private BigDecimal consumed;

    /** 需求规模合计。列名是驼峰 storyPoint（禅道原样），不加 @TableField 会被 MP 转成 story_point（坑位 #1） */
    @TableField("storyPoint")
    private BigDecimal storyPoint;

}
