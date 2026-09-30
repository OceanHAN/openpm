package cn.iocoder.yudao.module.zentao.controller.admin.project;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.service.project.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 项目 Controller
 *
 * 项目是主干链（产品 → 项目 → 执行 → 任务）的中间层。
 */
@Tag(name = "管理后台 - 项目")
@RestController
@RequestMapping("/zentao/project")
@Validated
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @PostMapping("/create")
    @Operation(summary = "创建项目")
    @PreAuthorize("@ss.hasPermission('zentao:project:create')")
    public CommonResult<Long> createProject(@Valid @RequestBody ProjectSaveReqVO createReqVO) {
        return success(projectService.createProject(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改项目")
    @PreAuthorize("@ss.hasPermission('zentao:project:update')")
    public CommonResult<Boolean> updateProject(@Valid @RequestBody ProjectSaveReqVO updateReqVO) {
        projectService.updateProject(updateReqVO);
        return success(true);
    }

    @PutMapping("/start")
    @Operation(summary = "开始项目", description = "未开始/已挂起 → 进行中")
    @Parameter(name = "id", description = "项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:project:update')")
    public CommonResult<Boolean> startProject(@RequestParam("id") Long id) {
        projectService.startProject(id);
        return success(true);
    }

    @PutMapping("/suspend")
    @Operation(summary = "挂起项目", description = "进行中 → 已挂起")
    @Parameter(name = "id", description = "项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:project:update')")
    public CommonResult<Boolean> suspendProject(@RequestParam("id") Long id) {
        projectService.suspendProject(id);
        return success(true);
    }

    @PutMapping("/activate")
    @Operation(summary = "激活项目", description = "已挂起 → 进行中")
    @Parameter(name = "id", description = "项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:project:update')")
    public CommonResult<Boolean> activateProject(@RequestParam("id") Long id) {
        projectService.activateProject(id);
        return success(true);
    }

    @PutMapping("/close")
    @Operation(summary = "关闭项目",
            description = "multiple=0 时连带关闭其执行；hasProduct=0 时连带关闭自动创建的产品")
    @Parameter(name = "id", description = "项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:project:update')")
    public CommonResult<Boolean> closeProject(@RequestParam("id") Long id,
                                              @RequestParam(value = "reason", required = false) String reason) {
        projectService.closeProject(id, reason);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除项目", description = "项目下还有执行时不允许删除")
    @Parameter(name = "id", description = "项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:project:delete')")
    public CommonResult<Boolean> deleteProject(@RequestParam("id") Long id) {
        projectService.deleteProject(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除项目")
    @Parameter(name = "ids", description = "项目编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:project:delete')")
    public CommonResult<Boolean> deleteProjectList(@RequestParam("ids") List<Long> ids) {
        projectService.deleteProjectList(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得项目")
    @Parameter(name = "id", description = "项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:project:query')")
    public CommonResult<ProjectRespVO> getProject(@RequestParam("id") Long id) {
        ProjectDO project = projectService.validateProjectExists(id);
        // 团队人数以 zt_team 为准（禅道是在读取时统计的）
        projectService.fillTeamCount(List.of(project));
        return success(BeanUtils.toBean(project, ProjectRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得项目分页")
    @PreAuthorize("@ss.hasPermission('zentao:project:query')")
    public CommonResult<PageResult<ProjectRespVO>> getProjectPage(@Valid ProjectPageReqVO pageReqVO) {
        PageResult<ProjectDO> pageResult = projectService.getProjectPage(pageReqVO);
        projectService.fillTeamCount(pageResult.getList());
        return success(BeanUtils.toBean(pageResult, ProjectRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得未关闭的项目列表", description = "用于任务等页面的项目下拉")
    @PreAuthorize("@ss.hasPermission('zentao:task:query')")
    public CommonResult<List<ProjectRespVO>> getProjectSimpleList() {
        List<ProjectDO> list = projectService.getProjectSimpleList();
        return success(BeanUtils.toBean(list, ProjectRespVO.class));
    }

    @GetMapping("/list-by-parent")
    @Operation(summary = "获得某个项目集下的项目", description = "项目靠 parent 指向所属项目集")
    @Parameter(name = "parent", description = "父项目编号", required = true, example = "0")
    @PreAuthorize("@ss.hasPermission('zentao:project:query')")
    public CommonResult<List<ProjectRespVO>> getProjectListByParent(@RequestParam("parent") Long parent) {
        List<ProjectDO> list = projectService.getProjectListByParent(parent);
        return success(BeanUtils.toBean(list, ProjectRespVO.class));
    }

}
