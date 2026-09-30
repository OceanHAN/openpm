package cn.iocoder.yudao.module.zentao.dal.dataobject.testtask;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试单用例执行记录 DO
 *
 * <p>对应禅道 {@code zt_testrun}：一个测试单里排了哪些用例，以及每条用例在**这个测试单里**
 * 的最近执行情况。表上有 {@code UNIQUE(task, case)} —— 一个测试单里同一用例只能排一次。
 *
 * <p><b>和 {@code zt_case} 上的同名字段是两回事</b>：
 * {@code zt_testrun.lastRunResult} 是「在这个测试单里」的结果，
 * {@code zt_case.lastRunResult} 是「最近一次（可能在别的测试单里）执行的结果」。
 * 执行一次会把两边都写掉（见 {{@code TestTaskService#runCase}}）。
 *
 * <p>这张表**不继承 BaseDO**：禅道原表没有 {@code deleted} 列，移除用例就是物理删。
 * 保留字：{@code case}。
 */
@TableName("zt_testrun")
@Data
public class TestRunDO {

    @TableId
    private Long id;

    /**
     * 所属测试单
     */
    private Long task;

    /**
     * 用例编号。case 是 MySQL 保留字
     */
    @TableField("`case`")
    private Long caseId;

    /**
     * 排进来时的用例版本。驼峰列名
     */
    @TableField("caseVersion")
    private Integer caseVersion;

    /**
     * 执行轮次（禅道原字段）
     */
    private Integer version;

    /**
     * 指派给。驼峰列名
     */
    @TableField("assignedTo")
    private String assignedTo;

    /**
     * 最近执行人。驼峰列名
     */
    @TableField("lastRunner")
    private String lastRunner;

    /**
     * 最近执行时间。驼峰列名
     */
    @TableField("lastRunDate")
    private LocalDateTime lastRunDate;

    /**
     * 最近执行结果。驼峰列名
     */
    @TableField("lastRunResult")
    private String lastRunResult;

    /**
     * normal 正常 / blocked 被阻塞（由执行结果推导）
     */
    private String status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    private String creator;

    private String updater;

}
