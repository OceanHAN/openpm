package cn.iocoder.yudao.module.zentao.dal.mysql.story;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 需求 Mapper
 */
@Mapper
public interface StoryMapper extends BaseMapperX<StoryDO> {

    default PageResult<StoryDO> selectPage(StoryPageReqVO reqVO) {
        LambdaQueryWrapperX<StoryDO> wrapper = new LambdaQueryWrapperX<StoryDO>()
                .eqIfPresent(StoryDO::getProduct, reqVO.getProduct())
                .eqIfPresent(StoryDO::getModule, reqVO.getModule())
                .eqIfPresent(StoryDO::getBranch, reqVO.getBranch())
                // moduleIds 由 Service 把 module 展开成「自己 + 全部子孙」，对齐禅道行为
                .inIfPresent(StoryDO::getModule, reqVO.getModuleIds())
                .eqIfPresent(StoryDO::getStatus, reqVO.getStatus())
                .eqIfPresent(StoryDO::getStage, reqVO.getStage())
                .eqIfPresent(StoryDO::getCategory, reqVO.getCategory())
                .eqIfPresent(StoryDO::getPri, reqVO.getPri())
                .eqIfPresent(StoryDO::getAssignedTo, reqVO.getAssignedTo())
                .likeIfPresent(StoryDO::getTitle, reqVO.getTitle())
                .betweenIfPresent(StoryDO::getOpenedDate, reqVO.getOpenedDate())
                .orderByDesc(StoryDO::getId);
        // 需求分层：type 单选（业务需求/用户需求/研发需求），types 多选
        // 注意 orderByDesc 已经在上面调过一次，这里只加类型条件，不能再链 orderBy
        wrapper.eqIfPresent(StoryDO::getType, reqVO.getType());
        wrapper.inIfPresent(StoryDO::getType, reqVO.getTypes());
        // 需求侧的 plan 是逗号列表（一条需求可以同时挂多个计划），所以用 FIND_IN_SET 而不是 =
        if (reqVO.getPlan() != null) {
            wrapper.apply("FIND_IN_SET({0}, plan)", reqVO.getPlan());
        }
        return selectPage(reqVO, wrapper);
    }

    /**
     * 某需求分解出来的子需求
     */
    default List<StoryDO> selectListByParent(Long parent) {
        return selectList(new LambdaQueryWrapperX<StoryDO>()
                .eq(StoryDO::getParent, parent)
                .orderByAsc(StoryDO::getPath));
    }

    /**
     * 整棵树（含自己），用 path 前缀一次取出，不递归
     */
    default List<StoryDO> selectListByRoot(Long root) {
        return selectList(new LambdaQueryWrapperX<StoryDO>()
                .eq(StoryDO::getRoot, root)
                .orderByAsc(StoryDO::getPath));
    }

    /**
     * 子需求数（用于「父需求还有子需求时不能删」）
     */
    default Long countByParent(Long parent) {
        return selectCount(new LambdaQueryWrapperX<StoryDO>()
                .eq(StoryDO::getParent, parent));
    }

    default List<StoryDO> selectListByProduct(Long product) {
        return selectList(new LambdaQueryWrapperX<StoryDO>()
                .eq(StoryDO::getProduct, product)
                .orderByDesc(StoryDO::getId));
    }

    /**
     * 某产品下按需求分层类型统计数量（需求池的「全部/业务需求/用户需求/研发需求」页签角标）
     *
     * <p>走数据库 GROUP BY，而不是把整个产品的需求捞出来在内存里数 —— 需求池动辄上千条。
     */
    @Select("SELECT type, COUNT(*) AS cnt FROM zt_story WHERE deleted = 0 AND product = #{product} GROUP BY type")
    List<Map<String, Object>> countGroupByType(@Param("product") Long product);

    default StoryDO selectByTitle(String title) {
        return selectOne(StoryDO::getTitle, title);
    }

    // ==================== 计划关联（zt_story.plan 是逗号列表） ====================

    /**
     * 某个计划下的需求。
     *
     * <p>需求侧的 {@code plan} 是**逗号列表**（需求可以同时挂在多个计划上），
     * 所以必须用 {@code FIND_IN_SET}，写成 {@code plan = ?} 只能匹配到「只挂了一个计划」的行。
     */
    @Select("SELECT * FROM zt_story WHERE deleted = 0 AND FIND_IN_SET(#{planId}, plan) ORDER BY id DESC")
    List<StoryDO> selectListByPlan(@Param("planId") Long planId);

    /**
     * 还没有关联任何计划的需求（关联抽屉里的候选）。
     *
     * @param branchList 计划的 branch 逗号串；用 FIND_IN_SET 做「需求的单个分支是否在计划的多个分支里」
     */
    @Select("SELECT * FROM zt_story WHERE deleted = 0 AND product = #{product} "
            + "AND (plan IS NULL OR plan = '') AND FIND_IN_SET(branch, #{branchList}) "
            + "ORDER BY id DESC LIMIT 200")
    List<StoryDO> selectUnlinkedByProduct(@Param("product") Long product,
                                          @Param("branchList") String branchList);

}
