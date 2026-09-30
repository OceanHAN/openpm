package cn.iocoder.yudao.module.zentao.service.project;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;

import java.util.List;

/**
 * 项目 Service 接口
 *
 * 项目是主干链的中间层（产品 → 项目 → 执行 → 任务）。
 *
 * <h3>状态机</h3>
 * <pre>
 *   wait ──start──> doing ──close──> closed
 *                    ↑   │
 *              activate  suspend
 *                    │   ↓
 *                suspended
 *   doing ──(已过 end 日期)──> delay
 * </pre>
 *
 * <h3>两个级联规则（对应禅道 project::close）</h3>
 * {@code multiple=0} 时关闭项目连带关闭其执行；
 * {@code hasProduct=0} 时关闭项目连带关闭自动创建的产品。
 */
public interface ProjectService {

    /**
     * 创建项目
     */
    Long createProject(ProjectSaveReqVO createReqVO);

    /**
     * 修改项目。已关闭的项目不允许修改
     */
    void updateProject(ProjectSaveReqVO updateReqVO);

    /**
     * 开始项目：wait → doing，写入实际开始日期
     */
    void startProject(Long id);

    /**
     * 挂起项目：doing → suspended
     */
    void suspendProject(Long id);

    /**
     * 激活项目：suspended → doing
     */
    void activateProject(Long id);

    /**
     * 关闭项目：→ closed。会按 multiple / hasProduct 规则级联
     *
     * @param id     项目编号
     * @param reason 关闭原因
     */
    void closeProject(Long id, String reason);

    /**
     * 删除项目（逻辑删除）。项目下还有执行时不允许删除
     */
    void deleteProject(Long id);

    /**
     * 批量删除
     */
    void deleteProjectList(List<Long> ids);

    /**
     * 获得项目
     */
    ProjectDO getProject(Long id);

    /**
     * 校验项目存在
     */
    ProjectDO validateProjectExists(Long id);

    /**
     * 获得项目分页
     */
    PageResult<ProjectDO> getProjectPage(ProjectPageReqVO reqVO);

    /**
     * 获得未关闭的项目列表，供下拉选择
     */
    List<ProjectDO> getProjectSimpleList();

    /**
     * 获得某个项目集下的项目（项目靠 parent 指向所属项目集）
     */
    List<ProjectDO> getProjectListByParent(Long parent);

    /**
     * 批量填充「团队人数」。禅道的 teamCount 是从 zt_team 统计出来的
     * （module/project/tao.php#fetchMemberCountByIdList），不是 zt_project.team 这个逗号串
     */
    void fillTeamCount(List<ProjectDO> list);

}
