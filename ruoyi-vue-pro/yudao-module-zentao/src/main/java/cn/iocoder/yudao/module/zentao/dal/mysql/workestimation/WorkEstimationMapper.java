package cn.iocoder.yudao.module.zentao.dal.mysql.workestimation;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.workestimation.WorkEstimationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目工作量估算 Mapper
 */
@Mapper
public interface WorkEstimationMapper extends BaseMapperX<WorkEstimationDO> {

    /** 一个项目一条（禅道的 getBudget 就是按 project 查单条） */
    default WorkEstimationDO selectByProject(Long project) {
        return selectOne(new LambdaQueryWrapperX<WorkEstimationDO>()
                .eq(WorkEstimationDO::getProject, project)
                .orderByDesc(WorkEstimationDO::getId)
                .last("LIMIT 1"));
    }

}
