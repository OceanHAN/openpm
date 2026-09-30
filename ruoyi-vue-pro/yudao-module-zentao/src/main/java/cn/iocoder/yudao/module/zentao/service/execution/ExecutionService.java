package cn.iocoder.yudao.module.zentao.service.execution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.execution.vo.ExecutionPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.execution.vo.ExecutionSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;

import java.util.List;

/**
 * 执行 Service 接口
 *
 * <h3>为什么这个接口和 ProjectService 长得很像</h3>
 * 因为禅道的执行和项目**就是同一张表**（{@code TABLE_EXECUTION = zt_project}），
 * 靠 {@code type} 区分：项目是 {@code type='project'}，执行是
 * {@code type IN ('sprint','stage','kanban')}。
 *
 * 所以这里的每个方法本质上都是「带上 type 过滤的项目查询」。
 * 入参曾直接复用 {@code ProjectSaveReqVO}，但它的 {@code model} 是必填，
 * 而执行没有自己的模型（从所属项目继承），调用方被迫传无意义的值，
 * 所以改为独立的 {@link ExecutionSaveReqVO}。
 */
public interface ExecutionService {

    /**
     * 创建执行。必须指定所属项目，且 type 必须属于执行类型
     *
     * @param createReqVO 执行信息
     * @return 执行编号
     */
    Long createExecution(ExecutionSaveReqVO createReqVO);

    /**
     * 修改执行
     */
    void updateExecution(ExecutionSaveReqVO updateReqVO);

    /**
     * 开始执行：wait/suspended → doing
     */
    void startExecution(Long id);

    /**
     * 挂起执行：doing → suspended
     */
    void suspendExecution(Long id);

    /**
     * 激活执行：suspended → doing
     */
    void activateExecution(Long id);

    /**
     * 关闭执行
     */
    void closeExecution(Long id, String reason);

    /**
     * 删除执行
     */
    void deleteExecution(Long id);

    /**
     * 批量删除
     */
    void deleteExecutionList(List<Long> ids);

    /**
     * 获得执行。若 id 对应的不是执行（而是项目），抛异常
     */
    ProjectDO getExecution(Long id);

    /**
     * 校验存在且确实是执行
     */
    ProjectDO validateExecutionExists(Long id);

    /**
     * 获得执行分页
     */
    PageResult<ProjectDO> getExecutionPage(ExecutionPageReqVO reqVO);

    /**
     * 获得某个项目下的全部执行
     */
    List<ProjectDO> getExecutionListByProject(Long project);

    /**
     * 统计某个项目下的执行数量
     */
    Long countExecutionByProject(Long project);

}
