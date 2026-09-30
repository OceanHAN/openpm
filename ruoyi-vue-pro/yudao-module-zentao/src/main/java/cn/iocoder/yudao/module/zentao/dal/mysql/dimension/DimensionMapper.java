package cn.iocoder.yudao.module.zentao.dal.mysql.dimension;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.dimension.DimensionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 维度 Mapper。
 *
 * <p>可见性过滤用**原生 SQL** 写，理由是禅道那段逻辑就是一句三选一的条件
 * （{@code module/bi/model.php:41-65}），翻译成 MyBatis-Plus 的嵌套 {@code and(or(...))}
 * 反而会踩到「{@code LambdaQueryWrapperX} 的继承方法把表达式类型退回父类」的坑（坑位 #1 变体）。
 * 原生 SQL 同时保证 {@code FIND_IN_SET} 原样下发（坑位 #18：逗号列表一律用 FIND_IN_SET）。
 *
 * <p>注意：原生 {@code @Select} **不会**被 {@code @TableLogic} 改写，所以每条都显式写 {@code deleted = 0}；
 * 它也不会被租户拦截器改写，但表本身必须登记进 {@code yudao.tenant.ignore-tables}，否则 MyBatis-Plus
 * 生成的查询会带 {@code tenant_id}（坑位 #2/#3）。
 */
@Mapper
public interface DimensionMapper extends BaseMapperX<DimensionDO> {

    /**
     * 全部维度（禅道 {@code biModel::getViewableObject} 里「超管直通」的那条路，按 id 升序）。
     *
     * <p>禅道这条 SQL 没写 {@code ORDER BY}（InnoDB 实际按主键返回）；本实现显式排序，
     * 好让「取可见的第一条」（{@code getFirst()}）的结果稳定可断言。
     */
    @Select("SELECT * FROM zt_dimension WHERE deleted = 0 ORDER BY id")
    List<DimensionDO> selectAllOrdered();

    /**
     * 某账号**可见**的维度，完全照抄 {@code biModel::getViewableObject('dimension')} 的三选一：
     * {@code acl = 'open' OR createdBy = 账号 OR FIND_IN_SET(账号, whitelist)}。
     *
     * <p>禅道是 {@code explode(',', whitelist) + in_array(account)}；{@code FIND_IN_SET} 等价，
     * 而且 {@code whitelist} 为 NULL 时整个表达式为 NULL（不命中），与禅道 {@code in_array(..., [])} 一致。
     */
    @Select("SELECT * FROM zt_dimension WHERE deleted = 0 "
            + "AND (acl = 'open' OR createdBy = #{account} OR FIND_IN_SET(#{account}, whitelist)) "
            + "ORDER BY id")
    List<DimensionDO> selectViewableOrdered(@Param("account") String account);

}
