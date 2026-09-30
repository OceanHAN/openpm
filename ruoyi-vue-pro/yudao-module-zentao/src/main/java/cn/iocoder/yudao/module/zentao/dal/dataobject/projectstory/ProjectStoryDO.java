package cn.iocoder.yudao.module.zentao.dal.dataobject.projectstory;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 项目/执行关联需求 DO
 *
 * <p>对应禅道 {@code zt_projectstory}。注意 {@code project} 列存的是**执行编号** ——
 * 项目和执行共用 {@code zt_project} 表，关联需求时写的是当前执行（或项目自身）的 id。
 *
 * <h3>version 是最容易被忽略的一列</h3>
 * 它记录的是**关联时需求的版本**，不是需求的当前版本。需求后来正式变更（version+1）后，
 * 项目这边仍然按当初规划的版本执行，列表里要标出「版本已变更」。
 * 这就是「计划冻结」：已排期的内容不会被需求的后续变更悄悄改掉。
 *
 * <p>这张表不继承 BaseDO —— 禅道原表只有 id + 业务列，关系数据删除即物理删除。
 */
@TableName("zt_projectstory")
@Data
public class ProjectStoryDO {

    @TableId
    private Long id;

    /**
     * 项目/执行编号
     */
    private Long project;

    /**
     * 需求所属产品
     */
    private Long product;

    /**
     * 需求所属分支
     */
    private Long branch;

    /**
     * 需求编号
     */
    private Long story;

    /**
     * 关联时的需求版本（不是当前版本）
     */
    private Integer version;

    /**
     * 排序。列名 {@code order} 是保留字
     */
    @TableField("`order`")
    private Integer order;

}
