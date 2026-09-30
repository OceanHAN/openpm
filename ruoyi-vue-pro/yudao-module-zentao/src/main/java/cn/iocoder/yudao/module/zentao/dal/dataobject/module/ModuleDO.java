package cn.iocoder.yudao.module.zentao.dal.dataobject.module;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模块树 DO
 *
 * <p>对应禅道 {@code zt_module} —— 一张**通用树表**，用 {@code (root, type, branch)}
 * 定位一棵树，而不是每种对象建一张表。见
 * {@link cn.iocoder.yudao.module.zentao.enums.module.ModuleTypeEnum}。
 *
 * <h3>path / grade 约定</h3>
 * 禅道的 path 是**逗号分隔且以逗号开头**的字符串，root 哨兵是单个逗号：
 * <pre>
 *   root 哨兵：path = ','        grade = 0
 *   一级模块： path = ',5,'      grade = 1
 *   二级模块： path = ',5,6,'    grade = 2
 * </pre>
 * 注意与 {@code zt_project} 的 {@code '/1/2/'} 斜杠格式不同，两套约定不要混用。
 * 删除/移动后由 {@code fixModulePath()} 按 parent 整棵树重算。
 *
 * <p>MySQL/JSqlParser 保留字：{@code order}、{@code from} 必须加反引号。
 */
@TableName("zt_module")
@KeySequence("zt_module_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ModuleDO extends BaseDO {

    /**
     * 树的根哨兵 path / grade，与禅道 fixModulePath() 一致
     */
    public static final String ROOT_PATH = ",";
    public static final int ROOT_GRADE = 0;

    @TableId
    private Long id;

    /**
     * 所属根对象：产品 id / 执行 id / 0（产品线）
     */
    private Long root;

    /**
     * 所属分支/平台，0 表示主干
     */
    private Long branch;

    /**
     * 模块名称
     */
    private String name;

    /**
     * 上级模块，0 为一级
     */
    private Long parent;

    /**
     * 路径，格式 {@code ,5,6,}
     */
    private String path;

    /**
     * 层级，一级模块为 1
     */
    private Integer grade;

    /**
     * 排序。列名 {@code order} 是保留字
     */
    @TableField("`order`")
    private Integer order;

    /**
     * 树类型。枚举 {@link cn.iocoder.yudao.module.zentao.enums.module.ModuleTypeEnum}
     */
    private String type;

    /**
     * 来源模块编号（禅道「复制模块」用）。列名 {@code from} 是 SQL 关键字
     */
    @TableField("`from`")
    private Integer from;

    /**
     * 负责人
     */
    private String owner;

    /**
     * 抄送人（禅道原字段，暂未使用）
     */
    private String collector;

    /**
     * 简称。字段名不能叫 {@code short}（Java 关键字），列名 {@code short} 也需要反引号
     */
    @TableField("`short`")
    private String shortName;

    /**
     * 扩展字段（禅道原字段，暂未使用）
     */
    private String extra;

}
