package cn.iocoder.yudao.module.zentao.dal.mysql.branch;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.branch.vo.BranchPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.branch.BranchDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分支/平台 Mapper
 *
 * <p>{@code zt_branch} 里没有 id=0 的「主干」行，主干由 Service 补出来，
 * 所以这里的查询都是「真实分支」范围。
 */
@Mapper
public interface BranchMapper extends BaseMapperX<BranchDO> {

    /**
     * 分页查询
     */
    default PageResult<BranchDO> selectPage(BranchPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BranchDO>()
                .eqIfPresent(BranchDO::getProduct, reqVO.getProduct())
                .likeIfPresent(BranchDO::getName, reqVO.getName())
                .eqIfPresent(BranchDO::getStatus, reqVO.getStatus())
                .orderByAsc(BranchDO::getOrder)
                .orderByAsc(BranchDO::getId));
    }

    /**
     * 某个产品下的分支列表
     *
     * @param product 产品编号
     * @param status  状态；传 null 表示全部
     */
    default List<BranchDO> selectListByProduct(Long product, String status) {
        return selectList(new LambdaQueryWrapperX<BranchDO>()
                .eq(BranchDO::getProduct, product)
                .eqIfPresent(BranchDO::getStatus, status)
                .orderByAsc(BranchDO::getOrder)
                .orderByAsc(BranchDO::getId));
    }

    /**
     * 按名称在产品内查重。排除 excludeId（修改时传自身 id）
     */
    default BranchDO selectByName(Long product, String name, Long excludeId) {
        return selectOne(new LambdaQueryWrapperX<BranchDO>()
                .eq(BranchDO::getProduct, product)
                .eq(BranchDO::getName, name)
                .neIfPresent(BranchDO::getId, excludeId)
                .last("LIMIT 1"));
    }

    /**
     * 某个产品下已有的最大排序值。新建时 order = max + 1，对齐禅道 create()
     *
     * <p>用原生 SQL 而不是 Wrapper：{@code MAX()} 聚合没有对应的实体字段，
     * 而且这里显式写 {@code deleted = 0} —— 原生 SQL 不会被 {@code @TableLogic} 自动改写，
     * 必须自己带上逻辑删除条件（否则会统计到已删除的分支）。
     */
    @Select("SELECT COALESCE(MAX(`order`), 0) FROM zt_branch WHERE product = #{product} AND deleted = 0")
    Integer selectMaxOrder(@Param("product") Long product);

    /**
     * 统计某个产品下的分支数量（不含虚拟主干）
     */
    default Long countByProduct(Long product) {
        return selectCount(BranchDO::getProduct, product);
    }

    // ==================== 删除保护：分支下是否已有数据 ====================

    /**
     * 该分支下是否已有需求。
     *
     * <p>跨表统计必须用原生 SQL，且 {@code deleted = 0} 要自己写 ——
     * 原生 SQL 不会被 {@code @TableLogic} 自动追加逻辑删除条件。
     */
    @Select("SELECT COUNT(*) FROM zt_story WHERE branch = #{branchId} AND deleted = 0")
    Long countStoryByBranch(@Param("branchId") Long branchId);

    /**
     * 该分支下是否已有缺陷
     */
    @Select("SELECT COUNT(*) FROM zt_bug WHERE branch = #{branchId} AND deleted = 0")
    Long countBugByBranch(@Param("branchId") Long branchId);

    /**
     * 该分支下是否已有模块（禅道 checkBranchData 也检查 zt_module）
     */
    @Select("SELECT COUNT(*) FROM zt_module WHERE branch = #{branchId} AND deleted = 0")
    Long countModuleByBranch(@Param("branchId") Long branchId);

}
