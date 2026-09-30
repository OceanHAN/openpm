package cn.iocoder.yudao.module.zentao.dal.mysql.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.task.TaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 任务 Mapper
 */
@Mapper
public interface TaskMapper extends BaseMapperX<TaskDO> {

    default PageResult<TaskDO> selectPage(TaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TaskDO>()
                .eqIfPresent(TaskDO::getProject, reqVO.getProject())
                .eqIfPresent(TaskDO::getExecution, reqVO.getExecution())
                .eqIfPresent(TaskDO::getStory, reqVO.getStory())
                .eqIfPresent(TaskDO::getModule, reqVO.getModule())
                .inIfPresent(TaskDO::getModule, reqVO.getModuleIds())
                .eqIfPresent(TaskDO::getStatus, reqVO.getStatus())
                .eqIfPresent(TaskDO::getPri, reqVO.getPri())
                .eqIfPresent(TaskDO::getAssignedTo, reqVO.getAssignedTo())
                .likeIfPresent(TaskDO::getName, reqVO.getName())
                .betweenIfPresent(TaskDO::getOpenedDate, reqVO.getOpenedDate())
                .orderByDesc(TaskDO::getId));
    }

    /** 指派给某人的任务（未删除，最新在前）——「我的日历」用 */
    default List<TaskDO> selectListByAssignedTo(String assignedTo) {
        return selectList(new LambdaQueryWrapperX<TaskDO>()
                .eq(TaskDO::getAssignedTo, assignedTo)
                .orderByDesc(TaskDO::getId));
    }

    default List<TaskDO> selectListByStory(Long story) {
        return selectList(new LambdaQueryWrapperX<TaskDO>()
                .eq(TaskDO::getStory, story)
                .orderByDesc(TaskDO::getId));
    }

}
