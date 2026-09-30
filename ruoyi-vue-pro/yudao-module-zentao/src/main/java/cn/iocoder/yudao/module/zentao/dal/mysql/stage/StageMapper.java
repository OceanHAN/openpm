package cn.iocoder.yudao.module.zentao.dal.mysql.stage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.stage.StageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 阶段模板 Mapper
 */
@Mapper
public interface StageMapper extends BaseMapperX<StageDO> {

    /**
     * 某个流程模板组下的阶段（按 order 升序）
     */
    default List<StageDO> selectListByGroup(Long workflowGroup) {
        return selectList(new LambdaQueryWrapperX<StageDO>()
                .eq(StageDO::getWorkflowGroup, workflowGroup)
                .orderByAsc(StageDO::getOrder)
                .orderByAsc(StageDO::getId));
    }

    /**
     * 某类项目流程可用的模板组（取第一套）
     */
    default List<StageDO> selectListByProjectType(String projectType) {
        return selectList(new LambdaQueryWrapperX<StageDO>()
                .eq(StageDO::getProjectType, projectType)
                .orderByAsc(StageDO::getOrder)
                .orderByAsc(StageDO::getId));
    }

    /**
     * 同组内按名称查重（排除 excludeId）
     */
    default StageDO selectByName(Long workflowGroup, String name, Long excludeId) {
        return selectOne(new LambdaQueryWrapperX<StageDO>()
                .eq(StageDO::getWorkflowGroup, workflowGroup)
                .eq(StageDO::getName, name)
                .neIfPresent(StageDO::getId, excludeId)
                .last("LIMIT 1"));
    }

    /**
     * 同组内已有的最大排序值
     */
    default Integer selectMaxOrder(Long workflowGroup) {
        StageDO last = selectOne(new LambdaQueryWrapperX<StageDO>()
                .eq(StageDO::getWorkflowGroup, workflowGroup)
                .orderByDesc(StageDO::getOrder)
                .last("LIMIT 1"));
        return last == null || last.getOrder() == null ? 0 : last.getOrder();
    }

}
