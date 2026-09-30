package cn.iocoder.yudao.module.zentao.dal.mysql.module;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.module.ModuleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 模块树 Mapper
 *
 * <p>几个查询刻意用原生 SQL，原因是：
 * <ul>
 *   <li>{@code MAX(order)} 这类聚合没有对应的实体字段</li>
 *   <li>跨表改挂（删模块时把需求/任务/缺陷改挂到父模块）本来就不是单表操作</li>
 * </ul>
 * <b>原生 SQL 不会被 {@code @TableLogic} 改写</b>，所以每一条都必须自己写
 * {@code deleted = 0}，否则会读到/改到已删除的数据。
 */
@Mapper
public interface ModuleMapper extends BaseMapperX<ModuleDO> {

    /**
     * 取一棵树的全部模块（按 parent、order 排序，便于上层拼树）
     *
     * @param root   根对象
     * @param type   树类型
     * @param branch 分支；传 null 表示不限分支
     */
    default List<ModuleDO> selectList(Long root, String type, Long branch) {
        return selectList(new LambdaQueryWrapperX<ModuleDO>()
                .eq(ModuleDO::getRoot, root)
                .eq(ModuleDO::getType, type)
                .eqIfPresent(ModuleDO::getBranch, branch)
                .orderByAsc(ModuleDO::getParent)
                .orderByAsc(ModuleDO::getOrder)
                .orderByAsc(ModuleDO::getId));
    }

    /**
     * 取某个模块的直接子模块
     */
    default List<ModuleDO> selectChildren(Long parent) {
        return selectList(new LambdaQueryWrapperX<ModuleDO>()
                .eq(ModuleDO::getParent, parent)
                .orderByAsc(ModuleDO::getOrder)
                .orderByAsc(ModuleDO::getId));
    }

    /**
     * 取某个模块的全部子孙（含自己）。用 path 前缀匹配，一次查询搞定，不递归。
     *
     * @param pathPrefix 目标模块自身的 path（形如 {@code ,9,}）。
     *                   禅道的 path **已经包含自己的 id**，子孙的 path 必然以它开头，
     *                   所以不要再拼接 id，否则会匹配不到任何子孙。
     */
    @Select("SELECT * FROM zt_module WHERE deleted = 0 AND (id = #{id} OR path LIKE CONCAT(#{pathPrefix}, '%'))")
    List<ModuleDO> selectSelfAndDescendants(@Param("id") Long id, @Param("pathPrefix") String pathPrefix);

    /**
     * 同一父模块下的最大排序值。禅道新建模块时 order = max + 10
     */
    @Select("SELECT COALESCE(MAX(`order`), 0) FROM zt_module WHERE root = #{root} AND type = #{type} "
            + "AND branch = #{branch} AND parent = #{parent} AND deleted = 0")
    Integer selectMaxOrder(@Param("root") Long root, @Param("type") String type,
                           @Param("branch") Long branch, @Param("parent") Long parent);

    /**
     * 同一父模块下是否已有同名模块（排除 excludeId）
     */
    default ModuleDO selectBySiblingName(Long root, String type, Long branch, Long parent,
                                        String name, Long excludeId) {
        return selectOne(new LambdaQueryWrapperX<ModuleDO>()
                .eq(ModuleDO::getRoot, root)
                .eq(ModuleDO::getType, type)
                .eq(ModuleDO::getBranch, branch)
                .eq(ModuleDO::getParent, parent)
                .eq(ModuleDO::getName, name)
                .neIfPresent(ModuleDO::getId, excludeId)
                .last("LIMIT 1"));
    }

    /**
     * 取某棵树的全部模块（含 path/grade），供 fixModulePath 重算
     */
    @Select("SELECT * FROM zt_module WHERE root = #{root} AND type = #{type} AND deleted = 0 ORDER BY parent, `order`, id")
    List<ModuleDO> selectAllForFix(@Param("root") Long root, @Param("type") String type);

    /**
     * 该分支下是否已有模块（分支删除保护用）
     */
    @Select("SELECT COUNT(*) FROM zt_module WHERE branch = #{branchId} AND deleted = 0")
    Long countByBranch(@Param("branchId") Long branchId);

    // ==================== 删除模块时把业务对象改挂到父模块 ====================
    // 复刻禅道 tree::remove()：删模块不删业务数据，统一挂到被删模块的 parent 上。

    @Update("UPDATE zt_story SET module = #{parent} WHERE deleted = 0 AND module IN "
            + "(SELECT id FROM (SELECT id FROM zt_module WHERE deleted = 0 AND (id = #{id} OR path LIKE CONCAT(#{pathPrefix}, '%'))) t)")
    int moveStoriesToParent(@Param("id") Long id, @Param("pathPrefix") String pathPrefix, @Param("parent") Long parent);

    @Update("UPDATE zt_task SET module = #{parent} WHERE deleted = 0 AND module IN "
            + "(SELECT id FROM (SELECT id FROM zt_module WHERE deleted = 0 AND (id = #{id} OR path LIKE CONCAT(#{pathPrefix}, '%'))) t)")
    int moveTasksToParent(@Param("id") Long id, @Param("pathPrefix") String pathPrefix, @Param("parent") Long parent);

    @Update("UPDATE zt_bug SET module = #{parent} WHERE deleted = 0 AND module IN "
            + "(SELECT id FROM (SELECT id FROM zt_module WHERE deleted = 0 AND (id = #{id} OR path LIKE CONCAT(#{pathPrefix}, '%'))) t)")
    int moveBugsToParent(@Param("id") Long id, @Param("pathPrefix") String pathPrefix, @Param("parent") Long parent);

}
