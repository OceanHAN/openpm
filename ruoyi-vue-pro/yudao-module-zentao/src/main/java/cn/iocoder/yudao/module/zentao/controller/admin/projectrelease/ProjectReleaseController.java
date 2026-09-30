package cn.iocoder.yudao.module.zentao.controller.admin.projectrelease;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.release.vo.ReleaseRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.release.ReleaseDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.release.ReleaseMapper;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 项目发布视图 Controller（对应禅道 {@code module/projectrelease/}）
 *
 * 发布带 {@code project} 列（逗号列表，可能来自多个项目）：
 * 所以「项目的发布」= {@code FIND_IN_SET(项目编号, zt_release.project)}。
 */
@Tag(name = "管理后台 - 项目发布视图")
@RestController
@RequestMapping("/zentao/projectrelease")
@Validated
public class ProjectReleaseController {

    @Resource
    private ReleaseMapper releaseMapper;

    @GetMapping("/release-list")
    @Operation(summary = "项目相关的发布列表", description = "发布用 project 逗号列表记录涉及的项目，这里按 FIND_IN_SET 反查")
    @Parameter(name = "project", description = "项目编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:projectrelease:query')")
    public CommonResult<Map<String, Object>> releaseList(@RequestParam("project") Long project) {
        List<ReleaseDO> releases = releaseMapper.selectListByProject(project);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", releases.size());
        result.put("list", BeanUtils.toBean(releases, ReleaseRespVO.class));
        return success(result);
    }

}
