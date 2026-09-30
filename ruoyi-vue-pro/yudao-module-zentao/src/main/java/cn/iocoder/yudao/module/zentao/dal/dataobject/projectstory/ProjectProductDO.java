package cn.iocoder.yudao.module.zentao.dal.dataobject.projectstory;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 项目关联产品 DO
 *
 * <p>对应禅道 {@code zt_projectproduct}：项目关联了哪些产品，是「项目需求」候选范围的来源。
 * {@code plan} 是逗号列表（项目可以只吃某个计划下的需求），{@code branch} 是单值。
 *
 * <p>不继承 BaseDO：关系数据，删除即物理删除。
 */
@TableName("zt_projectproduct")
@Data
public class ProjectProductDO {

    @TableId
    private Long id;

    /**
     * 项目/执行编号
     */
    private Long project;

    /**
     * 产品编号
     */
    private Long product;

    /**
     * 分支/平台（单值，0 主干）
     */
    private Long branch;

    /**
     * 关联的计划，逗号列表
     */
    private String plan;

    /**
     * 关联的路线图（禅道原字段）
     */
    private String roadmap;

}
