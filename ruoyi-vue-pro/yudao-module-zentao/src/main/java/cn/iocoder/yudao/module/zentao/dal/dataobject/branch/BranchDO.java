package cn.iocoder.yudao.module.zentao.dal.dataobject.branch;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 分支/平台 DO
 *
 * <p>对应禅道 {@code zt_branch}。它是**产品维度**的分支表：同一套结构，
 * 产品类型是 branch 时叫「分支」，是 platform 时叫「平台」。
 *
 * <h3>虚拟主干（branchID = 0）</h3>
 * 禅道约定 id = 0 表示「主干」，**这一行不在表里**：
 * <pre>
 *   $lang->branch->main = '主干';
 *   getByID(0) → 直接返回「主干」，不查库
 * </pre>
 * 需求/缺陷/模块的 {@code branch} 字段为 0 就表示挂在主干上。
 * 所以本 DO 里没有 id=0 的数据，主干由 Service 在列表/下拉里补出来。
 *
 * <h3>为什么没有 (product, name) 唯一键</h3>
 * 禅道用应用层 {@code unique} 校验，没建唯一索引。yudao 的 {@code BaseDO.deleted}
 * 带 {@code @TableLogic}，逻辑删除下唯一键会导致「删掉后重建同名分支」冲突
 * （同 {@code zt_storyspec} 踩过的坑），因此保持应用层校验。
 *
 * <p>MySQL 保留字：{@code default}、{@code desc}、{@code order} 都要加反引号。
 * 已用 JSqlParser 4.5 逐列验证过这三个列名加反引号后可以被正确解析。
 */
@TableName("zt_branch")
@KeySequence("zt_branch_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class BranchDO extends BaseDO {

    /**
     * 主干分支的固定 id。禅道用常量 BRANCH_MAIN = 0，这一行不落库
     */
    public static final Long MAIN_BRANCH_ID = 0L;

    /**
     * 主干分支的展示名
     */
    public static final String MAIN_BRANCH_NAME = "主干";

    @TableId
    private Long id;

    /**
     * 所属产品
     */
    private Long product;

    /**
     * 分支/平台名称
     */
    private String name;

    /**
     * 是否默认分支。列名 {@code default} 是 MySQL 保留字
     */
    @TableField("`default`")
    private Integer defaultFlag;

    /**
     * 状态：active / closed。枚举 {@link cn.iocoder.yudao.module.zentao.enums.branch.BranchStatusEnum}
     */
    private String status;

    /**
     * 描述。列名 {@code desc} 是 MySQL 保留字
     */
    @TableField("`desc`")
    private String desc;

    /**
     * 创建时间（禅道 createdDate，与 yudao 的 createTime 并存）
     */
    @TableField("createdDate")
    private LocalDateTime createdDate;

    /**
     * 关闭时间
     */
    @TableField("closedDate")
    private LocalDateTime closedDate;

    /**
     * 排序。列名 {@code order} 是 MySQL 保留字
     */
    @TableField("`order`")
    private Integer order;

}
