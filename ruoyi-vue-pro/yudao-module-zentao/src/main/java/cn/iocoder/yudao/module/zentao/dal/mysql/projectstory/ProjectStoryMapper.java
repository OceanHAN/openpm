package cn.iocoder.yudao.module.zentao.dal.mysql.projectstory;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.projectstory.ProjectStoryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 项目/执行关联需求 Mapper
 *
 * <p>关系表不带 {@code deleted} 列，删除就是物理删除，不存在逻辑删除的坑。
 */
@Mapper
public interface ProjectStoryMapper extends BaseMapperX<ProjectStoryDO> {

    /**
     * 某个项目/执行已关联的全部需求关系（按 order 升序）
     */
    default List<ProjectStoryDO> selectListByProject(Long project) {
        return selectList(new LambdaQueryWrapperX<ProjectStoryDO>()
                .eq(ProjectStoryDO::getProject, project)
                .orderByAsc(ProjectStoryDO::getOrder)
                .orderByAsc(ProjectStoryDO::getId));
    }

    /**
     * 某个需求被哪些项目/执行关联了
     */
    default List<ProjectStoryDO> selectListByStory(Long story) {
        return selectList(new LambdaQueryWrapperX<ProjectStoryDO>()
                .eq(ProjectStoryDO::getStory, story));
    }

    /**
     * 某个项目集合里是否有该需求（用于「子执行已关联该需求」判断）。
     */
    @Select("<script>SELECT COUNT(*) FROM zt_projectstory WHERE story = #{story} AND project IN "
            + "<foreach collection='projects' item='p' open='(' separator=',' close=')'>#{p}</foreach></script>")
    Long countByStoryAndProjects(@Param("story") Long story, @Param("projects") List<Long> projects);

    /**
     * 批量取多个需求的关系（用于一次性算出「这条需求还被哪些项目关联」）
     */
    @Select("<script>SELECT * FROM zt_projectstory WHERE story IN "
            + "<foreach collection='storyIds' item='s' open='(' separator=',' close=')'>#{s}</foreach></script>")
    List<ProjectStoryDO> selectListByStories(@Param("storyIds") List<Long> storyIds);

    /**
     * 某个项目/执行下关联的需求数量（按产品统计用）
     */
    default Long countByProjectAndProduct(Long project, Long product) {
        return selectCount(new LambdaQueryWrapperX<ProjectStoryDO>()
                .eq(ProjectStoryDO::getProject, project)
                .eq(ProjectStoryDO::getProduct, product));
    }

}
