package cn.iocoder.yudao.module.zentao.dal.dataobject.repo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 通用关系表（禅道 {@code zt_relation}）。
 *
 * <p>禅道用它表达「A 与 B 有关系」，本实现目前只用于代码提交：
 * {@code AType='revision', AID=zt_repohistory.id, relation='commit', BType=story|bug|task, BID=对象编号}。
 * 之所以照搬这张表而不是另建 `zt_repocommit`：禅道里它还被大量别的关系复用，
 * 保持同名同结构，将来加别的关系时不用再动表。
 *
 * <p>列名是 AType/AID/BType/BID 这种大小写混排（禅道原样），
 * 不加 {@code @TableField} 会被 MP 按驼峰转成 a_type（坑位 #1）。
 */
@TableName("zt_relation")
@Data
public class RelationDO {

    @TableId
    private Long id;

    @TableField("AType")
    private String aType;

    @TableField("AID")
    private Long aId;

    /** 关系名，提交关系里恒为 commit */
    private String relation;

    @TableField("BType")
    private String bType;

    @TableField("BID")
    private Long bId;

    private Long product;

}
