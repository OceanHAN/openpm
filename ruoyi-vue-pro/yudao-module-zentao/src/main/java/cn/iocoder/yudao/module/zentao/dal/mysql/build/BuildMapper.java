package cn.iocoder.yudao.module.zentao.dal.mysql.build;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.build.vo.BuildPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.build.BuildDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 构建 Mapper
 *
 * <p>要点：
 * <ul>
 *   <li>构建名在同一 (product, branch) 下唯一 —— 用应用层校验，不建唯一索引
 *       （逻辑删除下唯一索引会让「删掉后重建同名构建」失败）</li>
 *   <li>{@code stories}/{@code bugs}/{@code builds} 都是逗号列表，过滤用 {@code FIND_IN_SET}</li>
 *   <li>「是不是子构建」= 被别的构建的 builds 包含（发布引用检查在 release 模块接入后补）</li>
 * </ul>
 * 原生 SQL 不会被 {@code @TableLogic} 改写，{@code deleted = 0} 都要自己写。
 */
@Mapper
public interface BuildMapper extends BaseMapperX<BuildDO> {

    /**
     * 分页查询构建
     */
    default PageResult<BuildDO> selectPage(BuildPageReqVO reqVO) {
        LambdaQueryWrapperX<BuildDO> wrapper = new LambdaQueryWrapperX<BuildDO>()
                .eqIfPresent(BuildDO::getProduct, reqVO.getProduct())
                .eqIfPresent(BuildDO::getProject, reqVO.getProject())
                .eqIfPresent(BuildDO::getExecution, reqVO.getExecution())
                .likeIfPresent(BuildDO::getName, reqVO.getName())
                .eqIfPresent(BuildDO::getBuilder, reqVO.getBuilder());
        // 分支是逗号列表：一个构建可能同时覆盖多个分支
        if (reqVO.getBranch() != null) {
            wrapper.apply("FIND_IN_SET({0}, branch)", reqVO.getBranch());
        }
        wrapper.orderByDesc(BuildDO::getDate).orderByDesc(BuildDO::getId);
        return selectPage(reqVO, wrapper);
    }

    /**
     * 同一产品同一分支下的同名构建（排除 excludeId）
     */
    default BuildDO selectByName(Long product, String branch, String name, Long excludeId) {
        return selectOne(new LambdaQueryWrapperX<BuildDO>()
                .eq(BuildDO::getProduct, product)
                .eq(BuildDO::getBranch, branch)
                .eq(BuildDO::getName, name)
                .neIfPresent(BuildDO::getId, excludeId)
                .last("LIMIT 1"));
    }

    /**
     * 某个产品下的构建（可按分支过滤），用于缺陷「解决版本」下拉等
     */
    default List<BuildDO> selectListByProduct(Long product, Long branch) {
        LambdaQueryWrapperX<BuildDO> wrapper = new LambdaQueryWrapperX<BuildDO>()
                .eq(BuildDO::getProduct, product);
        if (branch != null) {
            wrapper.apply("FIND_IN_SET({0}, branch)", branch);
        }
        return selectList(wrapper.orderByDesc(BuildDO::getDate).orderByDesc(BuildDO::getId));
    }

    /**
     * 某个执行下的构建
     */
    /**
     * 项目视角的构建：直接挂在项目上的 + 该项目下所有执行的（调用方把执行编号传进来，
     * 因为执行与项目共用 zt_project，用 ProjectMapper#selectExecutionListByProject 取更清楚）
     */
    default List<BuildDO> selectListByProject(Long project, List<Long> executionIds) {
        return selectList(new LambdaQueryWrapperX<BuildDO>()
                .and(w -> {
                    w.eq(BuildDO::getProject, project);
                    if (executionIds != null && !executionIds.isEmpty()) {
                        w.or().in(BuildDO::getExecution, executionIds);
                    }
                })
                .orderByDesc(BuildDO::getDate)
                .orderByDesc(BuildDO::getId));
    }

    default List<BuildDO> selectListByExecution(Long execution) {
        return selectList(new LambdaQueryWrapperX<BuildDO>()
                .eq(BuildDO::getExecution, execution)
                .orderByDesc(BuildDO::getDate)
                .orderByDesc(BuildDO::getId));
    }

    /**
     * 该构建是否被别的构建当作子构建引用（禅道 {@code getByID()} 里的 isChild 判断之一）
     */
    @Select("SELECT COUNT(*) FROM zt_build WHERE deleted = 0 AND FIND_IN_SET(#{buildId}, builds)")
    Long countAsChildBuild(@Param("buildId") Long buildId);

    /**
     * 按编号批量取构建（集成构建读取子构建时用）
     */
    @Select("<script>SELECT * FROM zt_build WHERE deleted = 0 AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<BuildDO> selectListByIds(@Param("ids") List<Long> ids);

}
