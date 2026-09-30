package cn.iocoder.yudao.module.zentao.dal.mysql.release;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.release.vo.ReleasePageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.release.ReleaseDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布 Mapper
 *
 * <p>要点：{@code build}/{@code branch}/{@code project}/{@code stories}/{@code bugs}/{@code leftBugs}
 * 都是逗号列表，过滤统一用 {@code FIND_IN_SET}（它能同时兼容 {@code '1,2'} 与 {@code ',1,2,'} 两种写法）。
 */
@Mapper
public interface ReleaseMapper extends BaseMapperX<ReleaseDO> {

    /**
     * 分页查询发布
     */
    default PageResult<ReleaseDO> selectPage(ReleasePageReqVO reqVO) {
        LambdaQueryWrapperX<ReleaseDO> wrapper = new LambdaQueryWrapperX<ReleaseDO>()
                .eqIfPresent(ReleaseDO::getProduct, reqVO.getProduct())
                .eqIfPresent(ReleaseDO::getStatus, reqVO.getStatus())
                .likeIfPresent(ReleaseDO::getName, reqVO.getName());
        if (reqVO.getBranch() != null) {
            wrapper.apply("FIND_IN_SET({0}, branch)", reqVO.getBranch());
        }
        wrapper.orderByDesc(ReleaseDO::getDate).orderByDesc(ReleaseDO::getId);
        return selectPage(reqVO, wrapper);
    }

    /**
     * 产品下的发布列表
     */
    default List<ReleaseDO> selectListByProduct(Long product, Long branch) {
        LambdaQueryWrapperX<ReleaseDO> wrapper = new LambdaQueryWrapperX<ReleaseDO>()
                .eq(ReleaseDO::getProduct, product);
        if (branch != null) {
            wrapper.apply("FIND_IN_SET({0}, branch)", branch);
        }
        return selectList(wrapper.orderByDesc(ReleaseDO::getDate).orderByDesc(ReleaseDO::getId));
    }

    /**
     * 按名称查重。注意禅道是按 {@code system} 查重（system 默认 0 → 等价于全局唯一），
     * 所以这里也**不带 product 条件**。
     */
    /**
     * 找到「包含这个构建」的发布（禅道 module/bug/model.php:2087）：
     * <pre>
     *   WHERE product = ? AND deleted = 0 AND (FIND_IN_SET(buildId, `build`) OR shadow = buildId)
     * </pre>
     * 影子构建也算 —— 发布可以不带构建单独建，那时禅道会自动给它生成一个影子构建，
     * 缺陷解决时填的「解决版本」就可能是那个影子构建的编号。
     */
    default ReleaseDO selectByBuild(Long product, Long buildId) {
        return selectOne(new LambdaQueryWrapperX<ReleaseDO>()
                .eq(ReleaseDO::getProduct, product)
                .and(w -> w.apply("FIND_IN_SET({0}, `build`)", buildId).or().eq(ReleaseDO::getShadow, buildId))
                .orderByAsc(ReleaseDO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 项目相关的发布：{@code zt_release.project} 是逗号列表（可能涉及多个项目），
     * 所以用 FIND_IN_SET 反查（与禅道 projectrelease 的口径一致）
     */
    default List<ReleaseDO> selectListByProject(Long project) {
        return selectList(new LambdaQueryWrapperX<ReleaseDO>()
                .apply("FIND_IN_SET({0}, `project`)", project)
                .orderByDesc(ReleaseDO::getDate)
                .orderByDesc(ReleaseDO::getId));
    }

    default ReleaseDO selectByName(String name, Long excludeId) {
        return selectOne(new LambdaQueryWrapperX<ReleaseDO>()
                .eq(ReleaseDO::getName, name)
                .neIfPresent(ReleaseDO::getId, excludeId)
                .last("LIMIT 1"));
    }

    /**
     * 直接写状态与实际发布日期。
     *
     * <p>用原生 UPDATE 是为了能把 {@code releasedDate} **显式置空** ——
     * MyBatis-Plus 的 {@code updateById} 会跳过 null 字段，
     * 那样「从已发布改回未开始」就清不掉实际发布日期（同 build/plan 踩过的坑）。
     */
    @Update("UPDATE zt_release SET status = #{status}, releasedDate = #{releasedDate}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int updateStatusAndReleasedDate(@Param("id") Long id, @Param("status") String status,
                                    @Param("releasedDate") LocalDateTime releasedDate);

    /**
     * 该发布是否被别的发布包含（子发布）。删除前要拦一下，避免父发布引用到已删除的版本。
     */
    @Select("SELECT COUNT(*) FROM zt_release WHERE deleted = 0 AND FIND_IN_SET(#{releaseId}, releases)")
    Long countAsChildRelease(@Param("releaseId") Long releaseId);

}
