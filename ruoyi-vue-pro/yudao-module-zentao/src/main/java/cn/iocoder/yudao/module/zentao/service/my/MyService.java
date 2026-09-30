package cn.iocoder.yudao.module.zentao.service.my;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.bug.vo.BugRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.effort.vo.EffortRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.execution.vo.ExecutionPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.my.vo.MyOverviewRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.my.vo.MyTeamRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CasePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testtask.vo.TestTaskRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.task.vo.TaskRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.todo.vo.TodoRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.action.ActionDO;

import java.util.List;
import java.util.Map;

/**
 * 我的地盘 Service
 *
 * 对应禅道 {@code module/my/}。它是**查询层**：自己不产生数据，
 * 只是把「指派给我的 / 我参与的 / 我记录的」聚合出来。
 *
 * <p>禅道 my 里还有评审、风险、会议、MR、审批等聚合，它们依赖尚未迁移的模块
 * （risk / reviewissue / meeting / mr / approval），本实现只做当前已迁移的部分。
 */
public interface MyService {

    /**
     * 概览统计。account 为空时取当前登录账号
     */
    MyOverviewRespVO getOverview(String account);

    /** 指派给我的任务 */
    PageResult<TaskRespVO> getMyTaskPage(TaskPageReqVO reqVO, String account);

    /** 指派给我的缺陷 */
    PageResult<BugRespVO> getMyBugPage(BugPageReqVO reqVO, String account);

    /** 指派给我的需求 */
    PageResult<StoryRespVO> getMyStoryPage(StoryPageReqVO reqVO, String account);

    /** 我登记的工时 */
    PageResult<EffortRespVO> getMyEffortPage(EffortPageReqVO reqVO, String account);

    /** 我的待办（禅道口径：assignedTo/finishedBy/closedBy 命中我） */
    List<TodoRespVO> getMyTodoList(TodoPageReqVO reqVO, String account);

    /** 我最近的动态 */
    List<ActionDO> getMyActionList(String account, int limit);

    // ==================== 我的地盘：第二组（我参与的对象） ====================

    /** 我参与的项目（四个负责人字段 + 团队成员任一命中） */
    PageResult<ProjectRespVO> getMyProjectPage(ProjectPageReqVO reqVO, String account);

    /** 我参与的执行 */
    PageResult<ProjectRespVO> getMyExecutionPage(ExecutionPageReqVO reqVO, String account);

    /** 我的团队：我在哪些项目/执行里、什么角色、可用工时（zt_team 里 account=我） */
    List<MyTeamRespVO> getMyTeamList(String account);

    /** 我参与的测试单（负责人或创建人命中） */
    PageResult<TestTaskRespVO> getMyTestTaskPage(TestTaskPageReqVO reqVO, String account);

    /** 我的用例（我创建的 / 我评审过的） */
    PageResult<CaseRespVO> getMyCasePage(CasePageReqVO reqVO, String account);

    /** 我的文档（创建人 / 指派给 / 最后修改人命中） */
    PageResult<DocRespVO> getMyDocPage(DocPageReqVO reqVO, String account);

    /**
     * 我的日历：把「我的待办 / 我的任务 / 我参与的测试单」按日期归集到一个月的每一天。
     *
     * <p>返回 {@code [{date, items: [{type, id, name, status, statusName, extra}]}]}，
     * 前端按日期渲染即可（禅道有月历视图，本实现先提供数据，视图留作后续）。
     *
     * @param month YYYY-MM，不传取当月
     */
    List<Map<String, Object>> getMyCalendar(String month, String account);

}
