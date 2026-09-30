package cn.iocoder.yudao.module.zentao.controller.admin.team;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zentao.controller.admin.team.vo.TeamMemberRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.team.vo.TeamMemberSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.team.vo.TeamUpdateReqVO;
import cn.iocoder.yudao.module.zentao.service.team.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 项目/执行团队 Controller
 *
 * 项目与执行的成员在同一张表（{@code zt_team}）里，靠 {@code type} 区分。
 */
@Tag(name = "管理后台 - 项目/执行团队")
@RestController
@RequestMapping("/zentao/team")
@Validated
public class TeamController {

    @Resource
    private TeamService teamService;

    @GetMapping("/list")
    @Operation(summary = "获得成员列表", description = "带姓名与可用工时（天数 × 每天小时数）")
    @Parameter(name = "root", description = "项目或执行编号", required = true, example = "1")
    @Parameter(name = "type", description = "project/execution", required = true, example = "project")
    @PreAuthorize("@ss.hasPermission('zentao:team:query')")
    public CommonResult<List<TeamMemberRespVO>> getMemberList(@RequestParam("root") Long root,
                                                             @RequestParam(value = "type", defaultValue = "project") String type) {
        return success(teamService.getMemberList(root, type));
    }

    @GetMapping("/total-hours")
    @Operation(summary = "团队可用工时合计", description = "Σ(天数 × 每天小时数)，禅道的 totalHours")
    @PreAuthorize("@ss.hasPermission('zentao:team:query')")
    public CommonResult<BigDecimal> getTotalHours(@RequestParam("root") Long root,
                                                  @RequestParam(value = "type", defaultValue = "project") String type) {
        return success(teamService.getTotalHours(root, type));
    }

    @PostMapping("/add-member")
    @Operation(summary = "添加成员", description = "同一个人在同一对象下只能有一条成员记录")
    @PreAuthorize("@ss.hasPermission('zentao:team:update')")
    public CommonResult<Long> addMember(@Valid @RequestBody TeamMemberSaveReqVO reqVO) {
        return success(teamService.addMember(reqVO));
    }

    @PutMapping("/update-member")
    @Operation(summary = "修改成员", description = "改角色/天数/每天小时数/受限标记")
    @PreAuthorize("@ss.hasPermission('zentao:team:update')")
    public CommonResult<Boolean> updateMember(@Valid @RequestBody TeamMemberSaveReqVO reqVO) {
        teamService.updateMember(reqVO);
        return success(true);
    }

    @DeleteMapping("/remove-member")
    @Operation(summary = "移除成员", description = "物理删除：本表没有 deleted 列，逻辑删除会撞唯一键")
    @Parameter(name = "id", description = "成员记录编号", required = true, example = "98101")
    @PreAuthorize("@ss.hasPermission('zentao:team:update')")
    public CommonResult<Boolean> removeMember(@RequestParam("id") Long id) {
        teamService.removeMember(id);
        return success(true);
    }

    @PutMapping("/update-members")
    @Operation(summary = "全量保存成员",
            description = "禅道 updateTeamMembers：先删后插，**老成员的加入日期保留**")
    @PreAuthorize("@ss.hasPermission('zentao:team:update')")
    public CommonResult<List<String>> updateMembers(@Valid @RequestBody TeamUpdateReqVO reqVO) {
        return success(teamService.updateMembers(reqVO));
    }

}
