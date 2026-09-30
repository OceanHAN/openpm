package cn.iocoder.yudao.module.zentao.dal.dataobject.testtask;

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
 * 测试单 DO
 *
 * <p>对应禅道 {@code zt_testtask}。一个测试单就是「一次测试任务」：
 * 属于哪个产品/项目/执行/构建，谁负责，什么时候开始、什么时候完成。
 *
 * <p>它和 {@code zt_testrun} 一起回答「这个包测过没有、哪些用例跑过、结果如何」，
 * 所以用例上的「最近执行结果」三个字段其实是**测试单执行时回写**的。
 *
 * <p>保留字：{@code desc}（MySQL）、{@code begin} / {@code end}（JSqlParser）都要加反引号。
 */
@TableName("zt_testtask")
@KeySequence("zt_testtask_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class TestTaskDO extends BaseDO {

    @TableId
    private Long id;

    private Long project;

    private Long execution;

    private Long product;

    /**
     * 所属构建（测的是哪个包）
     */
    private Long build;

    private String name;

    /**
     * 类型，逗号列表
     */
    private String type;

    /**
     * 负责人
     */
    private String owner;

    private Integer pri;

    /**
     * 计划开始日期。begin 是 JSqlParser 保留字
     */
    @TableField("`begin`")
    private LocalDate begin;

    /**
     * 计划结束日期。end 是 JSqlParser 保留字
     */
    @TableField("`end`")
    private LocalDate end;

    /**
     * 实际开始日期。驼峰列名
     */
    @TableField("realBegan")
    private LocalDate realBegan;

    /**
     * 实际完成时间。驼峰列名
     */
    @TableField("realFinishedDate")
    private LocalDateTime realFinishedDate;

    /**
     * 描述。desc 是 MySQL 保留字
     */
    @TableField("`desc`")
    private String desc;

    /**
     * 测试总结
     */
    private String report;

    /**
     * 状态：wait 未开始 / doing 进行中 / done 已关闭 / blocked 被阻塞
     */
    private String status;

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
