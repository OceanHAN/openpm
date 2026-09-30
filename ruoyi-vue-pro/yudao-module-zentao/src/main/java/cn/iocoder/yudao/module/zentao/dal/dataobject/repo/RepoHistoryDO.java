package cn.iocoder.yudao.module.zentao.dal.dataobject.repo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代码库提交记录（禅道 {@code zt_repohistory}）。
 *
 * <p>一条 commit 一行。{@code commit} 是**自增序号**（禅道用它做「第几次提交」），
 * 不是 git 的序号 —— 同步时按顺序编号，`revision` 才是 sha。
 *
 * <p>本表没有 {@code deleted} 列（禅道原样）：提交记录是只增不改的事实数据，
 * 所以**不继承 BaseDO**。
 */
@TableName("zt_repohistory")
@Data
public class RepoHistoryDO {

    @TableId
    private Long id;

    /** 代码库编号 */
    private Long repo;

    /** commit sha */
    private String revision;

    /** 自增序号 */
    private Integer commit;

    /** 提交说明 */
    private String comment;

    /** 提交者 */
    private String committer;

    /** 提交时间。列名 time 在 MySQL 里不是保留字，但保持与禅道一致 */
    @TableField("`time`")
    private LocalDateTime time;

}
