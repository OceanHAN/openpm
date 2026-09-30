package cn.iocoder.yudao.module.zentao.dal.mysql.projectstory;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.projectstory.ProjectProductDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目关联产品 Mapper
 */
@Mapper
public interface ProjectProductMapper extends BaseMapperX<ProjectProductDO> {

    /**
     * 某个项目/执行关联的产品
     */
    default List<ProjectProductDO> selectListByProject(Long project) {
        return selectList(new LambdaQueryWrapperX<ProjectProductDO>()
                .eq(ProjectProductDO::getProject, project)
                .orderByAsc(ProjectProductDO::getId));
    }

    /**
     * 某个项目是否已关联某产品的某分支
     */
    default ProjectProductDO selectByProjectProduct(Long project, Long product, Long branch) {
        return selectOne(new LambdaQueryWrapperX<ProjectProductDO>()
                .eq(ProjectProductDO::getProject, project)
                .eq(ProjectProductDO::getProduct, product)
                .eq(ProjectProductDO::getBranch, branch)
                .last("LIMIT 1"));
    }

    /**
     * 某个产品被关联到哪些项目
     */
    default List<ProjectProductDO> selectListByProduct(Long product) {
        return selectList(new LambdaQueryWrapperX<ProjectProductDO>()
                .eq(ProjectProductDO::getProduct, product));
    }

}
