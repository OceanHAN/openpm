package cn.iocoder.yudao.module.zentao.dal.dataobject.score;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水（禅道 {@code zt_score}）。
 *
 * <h3>它记的是「一次计分」</h3>
 * 一条 = 谁（account）、因为哪个模块的哪个动作（module + method）、得了多少分（score）、
 * 计分前后的总分快照（before / after）、时间（time）。
 *
 * <h3>两个不能照抄的地方（都写在 README 里）</h3>
 * <ol>
 *   <li><b>总分不再冗余在用户表上</b>：禅道 saveScore 会顺手
 *       {@code UPDATE zt_user SET score = score + N, scoreLevel = scoreLevel + N}。
 *       本项目没迁 {@code zt_user}，所以总分 = {@code SUM(zt_score.score)}，
 *       before/after 在插入时按「当前总分」算快照落库（保持这两个字段的语义）。</li>
 *   <li><b>没有 {@code deleted} 列</b>（禅道原样）：积分流水是只增不改的事实数据，不继承 BaseDO。</li>
 * </ol>
 *
 * <p>保留字：{@code desc} 是 MySQL 关键字；{@code before} 也在保留字名单里（BEFORE 用于触发器），
 * 一律加反引号。
 */
@TableName("zt_score")
@Data
public class ScoreDO {

    @TableId
    private Long id;

    /** 账号 */
    private String account;

    /** 模块（task/story/bug/execution/user/ajax……） */
    private String module;

    /** 动作（create/finish/close/resolve/confirm/login……） */
    private String method;

    /** 描述。列名 desc 是关键字 */
    @TableField("`desc`")
    private String desc;

    /** 计分前的总分 */
    @TableField("`before`")
    private Integer before;

    /** 这次得的分（可正可负，禅道里是 int unsigned，本实现允许负数以便后续做扣分） */
    private Integer score;

    /** 计分后的总分 */
    @TableField("`after`")
    private Integer after;

    /** 计分时间 */
    private LocalDateTime time;

}
