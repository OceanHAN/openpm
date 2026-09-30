package cn.iocoder.yudao.module.zentao.dal.dataobject.testtask;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 测试执行结果 DO
 *
 * <p>对应禅道 {@code zt_testresult}：**一次执行一行**，构成执行历史。
 * 一条 {@code zt_testrun} 可以跑很多次，每次在这里留一行。
 *
 * <p>{@code stepResults} 存步骤级结果：禅道存 PHP {@code serialize()} 字符串，
 * 本实现存 JSON（可读、跨语言），语义一致 —— 都是「这一轮每一步的结果」。
 * 用例级结果 {@code caseResult} 是由步骤结果**算出来**的，不是人填的。
 *
 * <p>保留字：{@code case}、{@code date}。
 */
@TableName("zt_testresult")
@Data
public class TestResultDO {

    @TableId
    private Long id;

    /**
     * 所属执行记录（zt_testrun.id）
     */
    private Long run;

    /**
     * 用例编号。case 是 MySQL 保留字
     */
    @TableField("`case`")
    private Long caseId;

    /**
     * 执行的用例版本
     */
    private Integer version;

    /**
     * 用例级结果：pass/fail/blocked/n-a。驼峰列名
     */
    @TableField("caseResult")
    private String caseResult;

    /**
     * 步骤级结果，JSON 数组。驼峰列名
     */
    @TableField("stepResults")
    private String stepResults;

    /**
     * 执行人。驼峰列名
     */
    @TableField("lastRunner")
    private String lastRunner;

    /**
     * 执行时间。date 是 MySQL 关键字
     */
    @TableField("`date`")
    private LocalDateTime date;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    private String creator;

    private String updater;

}
