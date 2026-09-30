package cn.iocoder.yudao.module.zentao.dal.mysql.effort;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.effort.EffortDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 工时明细 Mapper
 */
@Mapper
public interface EffortMapper extends BaseMapperX<EffortDO> {

    /**
     * 某个任务的全部工时，按日期正序（同一天按登记先后）。
     * 「最后一条」= 这个列表的最后一项，任务的剩余工时由它决定。
     */
    default List<EffortDO> selectListByTask(Long taskId) {
        return selectList(new LambdaQueryWrapperX<EffortDO>()
                .eq(EffortDO::getObjectType, "task")
                .eq(EffortDO::getObjectID, taskId)
                .orderByAsc(EffortDO::getDate)
                .orderByAsc(EffortDO::getId));
    }

    default PageResult<EffortDO> selectPage(EffortPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EffortDO>()
                .eq(EffortDO::getObjectType, "task")
                .eqIfPresent(EffortDO::getObjectID, reqVO.getTaskId())
                .eqIfPresent(EffortDO::getAccount, reqVO.getAccount())
                .eqIfPresent(EffortDO::getProject, reqVO.getProject())
                .eqIfPresent(EffortDO::getExecution, reqVO.getExecution())
                .betweenIfPresent(EffortDO::getDate, reqVO.getDate())
                .orderByDesc(EffortDO::getDate)
                .orderByDesc(EffortDO::getId));
    }

    /**
     * 汇总用：按条件取出全部工时（不分页）。
     * 工时量级不大（一个人一天几条），在内存里聚合比写 GROUP BY 的 XML 更好维护。
     */
    default List<EffortDO> selectListByCondition(EffortPageReqVO reqVO) {
        return selectList(new LambdaQueryWrapperX<EffortDO>()
                .eq(EffortDO::getObjectType, "task")
                .eqIfPresent(EffortDO::getObjectID, reqVO.getTaskId())
                .eqIfPresent(EffortDO::getAccount, reqVO.getAccount())
                .eqIfPresent(EffortDO::getProject, reqVO.getProject())
                .eqIfPresent(EffortDO::getExecution, reqVO.getExecution())
                .betweenIfPresent(EffortDO::getDate, reqVO.getDate()));
    }

}
