package cn.iocoder.yudao.module.zentao.controller.admin.projectbuild;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.build.vo.BuildRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.build.BuildDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.build.BuildMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.project.ProjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 项目构建视图 Controller（对应禅道 {@code module/projectbuild/}）
 *
 * 禅道里它也是 redirect（{@code project/build}）。构建与执行的归属关系是
 * {@code zt_build.execution}（执行），所以「项目的构建」= 该项目下所有执行的构建并集，
 * 再加上直接挂在项目上的构建。
 */
@Tag(name = "管理后台 - 项目构建视图")
@RestController
@RequestMapping("/zentao/projectbuild")
@Validated
public class ProjectBuildController {

    @Resource
    private BuildMapper buildMapper;

    @Resource
    private ProjectMapper projectMapper;

    @GetMapping("/build-list")
    @Operation(summary = "项目的构建列表",
            description = "= 项目下所有执行的构建 + 直接挂在项目上的构建（按日期倒序）")
    @Parameter(name = "project", description = "项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:projectbuild:query')")
    public CommonResult<Map<String, Object>> buildList(@RequestParam("project") Long project) {
        ProjectDO projectDO = projectMapper.selectById(project);
        // 执行与项目共用 zt_project：项目的构建 = 直挂项目的 + 项目下所有执行的
        List<Long> executionIds = new ArrayList<>();
        for (ProjectDO execution : projectMapper.selectExecutionListByProject(project)) {
            executionIds.add(execution.getId());
        }
        List<BuildDO> builds = buildMapper.selectListByProject(project, executionIds);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectName", projectDO == null ? null : projectDO.getName());
        result.put("total", builds.size());
        result.put("list", BeanUtils.toBean(builds, BuildRespVO.class));
        return success(result);
    }

}
