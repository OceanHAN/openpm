package cn.iocoder.yudao.module.zentao.controller.admin.repo;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.repo.vo.RepoCommitPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.repo.vo.RepoCommitRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.repo.vo.RepoPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.repo.vo.RepoRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.repo.vo.RepoSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.repo.RepoDO;
import cn.iocoder.yudao.module.zentao.service.repo.RepoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 代码库（禅道 {@code module/repo}）。
 *
 * <p><b>类名带 {@code Zentao} 前缀</b>：延续坑位 #47 的纪律 —— 新模块开工前先搜同名类、
 * 同名注入字段、同名 mapper，避免 Spring/MyBatis 按短类名或字段名解析时撞车。
 */
@Tag(name = "管理后台 - 禅道代码库")
@RestController
@RequestMapping("/zentao/repo")
@Validated
public class ZentaoRepoController {

    @Resource
    private RepoService repoService;

    @PostMapping("/create")
    @Operation(summary = "新增代码库", description = "本实现只支持「本地 Git 仓库」：路径必须是服务器上存在且含 .git 的目录")
    @PreAuthorize("@ss.hasPermission('zentao:repo:create')")
    public CommonResult<Long> createRepo(@Valid @RequestBody RepoSaveReqVO reqVO) {
        return success(repoService.createRepo(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改代码库", description = "改了路径会清掉「同步到哪儿了」，下次同步从最新开始")
    @PreAuthorize("@ss.hasPermission('zentao:repo:update')")
    public CommonResult<Boolean> updateRepo(@Valid @RequestBody RepoSaveReqVO reqVO) {
        repoService.updateRepo(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除代码库", description = "提交记录与改动文件一起物理清理（这两张表没有 deleted 列）")
    @Parameter(name = "id", description = "代码库编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:repo:delete')")
    public CommonResult<Boolean> deleteRepo(@RequestParam("id") Long id) {
        repoService.deleteRepo(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得代码库")
    @Parameter(name = "id", description = "代码库编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:repo:query')")
    public CommonResult<RepoRespVO> getRepo(@RequestParam("id") Long id) {
        RepoDO repo = repoService.getRepo(id);
        return success(BeanUtils.toBean(repo, RepoRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "代码库分页")
    @PreAuthorize("@ss.hasPermission('zentao:repo:query')")
    public CommonResult<PageResult<RepoRespVO>> getRepoPage(@Valid RepoPageReqVO reqVO) {
        PageResult<RepoDO> page = repoService.getRepoPage(reqVO);
        return success(new PageResult<>(BeanUtils.toBean(page.getList(), RepoRespVO.class), page.getTotal()));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "代码库精简列表", description = "给下拉选择用")
    @PreAuthorize("@ss.hasPermission('zentao:repo:query')")
    public CommonResult<List<RepoRespVO>> getSimpleList() {
        return success(BeanUtils.toBean(repoService.getSimpleList(), RepoRespVO.class));
    }

    @PostMapping("/sync")
    @Operation(summary = "同步提交记录",
            description = "跑一次 git log（增量：从上次同步到的 sha 往后），写入 zt_repohistory / zt_repofiles，"
                    + "并解析提交说明里的 Story #1 / Task #2 / Bug #3 写成对象关联；返回本次新入库的提交数")
    @Parameter(name = "id", description = "代码库编号", required = true, example = "1")
    @Parameter(name = "maxCount", description = "最多拉多少条，默认 200", example = "200")
    @PreAuthorize("@ss.hasPermission('zentao:repo:sync')")
    public CommonResult<Integer> sync(@RequestParam("id") Long id,
                                      @RequestParam(value = "maxCount", required = false) Integer maxCount) {
        return success(repoService.sync(id, maxCount));
    }

    @GetMapping("/commit-page")
    @Operation(summary = "提交记录分页",
            description = "不传 repo 就是全部代码库；传 objectType + objectID 就是「某个需求/任务/缺陷关联的提交」")
    @PreAuthorize("@ss.hasPermission('zentao:repo:query')")
    public CommonResult<PageResult<RepoCommitRespVO>> getCommitPage(@Valid RepoCommitPageReqVO reqVO) {
        return success(repoService.getCommitPage(reqVO));
    }

    @GetMapping("/commit-get")
    @Operation(summary = "提交详情", description = "带改动的文件清单与关联的对象")
    @Parameter(name = "repo", description = "代码库编号", required = true, example = "1")
    @Parameter(name = "revision", description = "commit sha", required = true)
    @PreAuthorize("@ss.hasPermission('zentao:repo:query')")
    public CommonResult<RepoCommitRespVO> getCommit(@RequestParam("repo") Long repo,
                                                    @RequestParam("revision") String revision) {
        return success(repoService.getCommit(repo, revision));
    }

}
