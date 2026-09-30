package cn.iocoder.yudao.module.zentao.dal.mysql.plan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.plan.vo.PlanPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.plan.PlanDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产品计划 Mapper
 *
 * <p>两个查询要点：
 * <ul>
 *   <li><b>按分支过滤用 FIND_IN_SET</b>：计划的 branch 是逗号列表（一个计划可能覆盖多个分支），
 *       用 {@code branch = 1} 是查不出来的，必须 {@code FIND_IN_SET(1, branch)}。</li>
 *   <li><b>统计需求数也要 FIND_IN_SET</b>：需求侧的 {@code zt_story.plan} 同样是逗号列表
 *       （需求可以同时挂多个计划），单值比较会漏掉。</li>
 * </ul>
 * 原生 SQL 不会被 {@code @TableLogic} 改写，所以 {@code deleted = 0} 都要自己写。
 */
@Mapper
public interface PlanMapper extends BaseMapperX<PlanDO> {

    /**
     * 分页查询计划
     */
    default PageResult<PlanDO> selectPage(PlanPageReqVO reqVO) {
        LambdaQueryWrapperX<PlanDO> wrapper = new LambdaQueryWrapperX<PlanDO>()
                .eqIfPresent(PlanDO::getProduct, reqVO.getProduct())
                .eqIfPresent(PlanDO::getStatus, reqVO.getStatus())
                .likeIfPresent(PlanDO::getTitle, reqVO.getTitle())
                .eqIfPresent(PlanDO::getParent, reqVO.getParent());
        // 分支是逗号列表，只能用 FIND_IN_SET
        if (reqVO.getBranch() != null) {
            wrapper.apply("FIND_IN_SET({0}, branch)", reqVO.getBranch());
        }
        // 计划列表按开始日期倒序（禅道默认 orderBy = begin_desc）
        wrapper.orderByDesc(PlanDO::getBegin).orderByDesc(PlanDO::getId);
        return selectPage(reqVO, wrapper);
    }

    /**
     * 某个产品下的计划列表，可按分支过滤
     */
    default List<PlanDO> selectListByProduct(Long product, Long branch) {
        LambdaQueryWrapperX<PlanDO> wrapper = new LambdaQueryWrapperX<PlanDO>()
                .eq(PlanDO::getProduct, product);
        if (branch != null) {
            wrapper.apply("FIND_IN_SET({0}, branch)", branch);
        }
        return selectList(wrapper.orderByDesc(PlanDO::getBegin).orderByDesc(PlanDO::getId));
    }

    /**
     * 子计划
     */
    default List<PlanDO> selectChildren(Long parentId) {
        return selectList(new LambdaQueryWrapperX<PlanDO>()
                .eq(PlanDO::getParent, parentId)
                .orderByAsc(PlanDO::getBegin)
                .orderByAsc(PlanDO::getId));
    }

    /**
     * 子计划数量
     */
    default Long countChildren(Long parentId) {
        return selectCount(PlanDO::getParent, parentId);
    }

    /**
     * 计划下关联的需求数量。需求侧 plan 是逗号列表，用 FIND_IN_SET 精确匹配单个计划编号
     */
    @Select("SELECT COUNT(*) FROM zt_story WHERE deleted = 0 AND FIND_IN_SET(#{planId}, plan)")
    Long countStoriesByPlan(@Param("planId") Long planId);

    /**
     * 计划下关联的缺陷数量（缺陷侧 plan 是单值）
     */
    @Select("SELECT COUNT(*) FROM zt_bug WHERE deleted = 0 AND plan = #{planId}")
    Long countBugsByPlan(@Param("planId") Long planId);

    /**
     * 直接改状态与三个日期字段。
     *
     * <p>用原生 UPDATE 而不是 {@code updateById}，是因为状态流转需要把
     * {@code finishedDate}/{@code closedDate}/{@code closedReason} **显式置空**，
     * 而 MyBatis-Plus 默认的更新策略会跳过 null 字段 —— 那样「已完成再激活」就清不掉完成时间。
     */
    @Update("UPDATE zt_productplan SET status = #{status}, finishedDate = #{finishedDate}, "
            + "closedDate = #{closedDate}, closedReason = #{closedReason}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int updateStatusFields(@Param("id") Long id, @Param("status") String status,
                           @Param("finishedDate") LocalDateTime finishedDate,
                           @Param("closedDate") LocalDateTime closedDate,
                           @Param("closedReason") String closedReason);

    /**
     * 只改 parent（用于维护「-1 表示有子计划」这个标记）
     */
    @Update("UPDATE zt_productplan SET parent = #{parent}, update_time = NOW() WHERE id = #{id} AND deleted = 0")
    int updateParent(@Param("id") Long id, @Param("parent") Long parent);

    /**
     * 一次性取出多个计划下的需求（只取 id 与 plan 两列），供列表页统计需求数用。
     *
     * <p>计划列表页有「需求数」列，如果每行都查一次就是 N+1；这里一条 SQL 把所有相关需求捞回来，
     * 在 Java 里按逗号列表拆分计数。因为 {@code zt_story.plan} 是逗号列表，
     * 无法用普通的 GROUP BY 直接算出「每个计划多少条」。
     */
    @Select("<script>SELECT id, plan FROM zt_story WHERE deleted = 0 AND plan &lt;&gt; '' AND ("
            + "<foreach collection='planIds' item='pid' separator=' OR '>FIND_IN_SET(#{pid}, plan)</foreach>)</script>")
    List<StoryDO> selectStoriesOfPlans(@Param("planIds") List<Long> planIds);

}
