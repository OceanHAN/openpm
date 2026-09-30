package cn.iocoder.yudao.module.zentao.dal.dataobject.testreport;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 测试报告 DO
 *
 * <p>对应禅道 {@code zt_testreport}。报告自己**只存条件与结论**：
 * {@code tasks}（汇总哪些测试单）、{@code builds}、{@code begin}/{@code end}（时间范围）、
 * {@code report}（人写的结论），以及生成时算出来留档的 {@code cases}/{@code stories}/{@code bugs} 清单。
 *
 * <p>「用例数 / 通过 / 失败」这些数字是**读的时候现算**的（见 ReportService#getSummary），
 * 所以报告不会因为数据变化而过期失真。
 *
 * <p>保留字：{@code begin} / {@code end}（JSqlParser）。
 */
@TableName("zt_testreport")
@KeySequence("zt_testreport_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TestReportDO extends BaseDO {

    @TableId
    private Long id;

    private Long project;

    private Long product;

    private Long execution;

    /**
     * 汇总的测试单，逗号列表
     */
    private String tasks;

    /**
     * 涉及的构建，逗号列表
     */
    private String builds;

    private String title;

    /**
     * 统计开始日期。begin 是 JSqlParser 保留字
     */
    @TableField("`begin`")
    private LocalDate begin;

    /**
     * 统计结束日期。end 是 JSqlParser 保留字
     */
    @TableField("`end`")
    private LocalDate end;

    private String owner;

    /**
     * 涉及的需求，逗号列表
     */
    private String stories;

    /**
     * 涉及的缺陷，逗号列表
     */
    private String bugs;

    /**
     * 涉及的用例，逗号列表
     */
    private String cases;

    /**
     * 结论 / 人工总结
     */
    private String report;

    /**
     * 创建人。驼峰列名
     */
    @TableField("createdBy")
    private String createdBy;

    /**
     * 创建时间。驼峰列名
     */
    @TableField("createdDate")
    private LocalDateTime createdDate;

}
