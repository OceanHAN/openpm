package cn.iocoder.yudao.module.zentao.controller.admin.entry;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntryLogPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntryLogRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntryPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntryRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntrySaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntrySignRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntryVerifyReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntryVerifyRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.entry.EntryDO;
import cn.iocoder.yudao.module.zentao.service.entry.EntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
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
 * 应用接入（禅道 {@code module/entry}）。
 *
 * <p>类名带 {@code Zentao} 前缀是纪律（坑位 #47）：新模块先确认没有同名 Controller / Service / Mapper / 注入字段。
 */
@Tag(name = "管理后台 - 禅道应用接入")
@RestController
@RequestMapping("/zentao/entry")
@Validated
public class ZentaoEntryController {

    @Resource
    private EntryService entryService;

    @GetMapping("/page")
    @Operation(summary = "应用接入分页",
            description = "与禅道 entry::browse 一致：代号为 gitfox 的内置应用不出现在列表里")
    @PreAuthorize("@ss.hasPermission('zentao:entry:query')")
    public CommonResult<PageResult<EntryRespVO>> getPage(@Valid EntryPageReqVO pageReqVO) {
        PageResult<EntryDO> page = entryService.getPage(pageReqVO);
        return success(BeanUtils.toBean(page, EntryRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "应用接入精简列表（下拉用）")
    @PreAuthorize("@ss.hasPermission('zentao:entry:query')")
    public CommonResult<List<EntryRespVO>> getSimpleList() {
        return success(BeanUtils.toBean(entryService.getSimpleList(), EntryRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得应用接入")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:entry:query')")
    public CommonResult<EntryRespVO> getEntry(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(entryService.getEntry(id), EntryRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建应用接入", description = "密钥留空时自动生成 32 位；免密登录时可以不绑账号")
    @PreAuthorize("@ss.hasPermission('zentao:entry:create')")
    public CommonResult<Long> createEntry(@Valid @RequestBody EntrySaveReqVO reqVO) {
        return success(entryService.createEntry(BeanUtils.toBean(reqVO, EntryDO.class)));
    }

    @PutMapping("/update")
    @Operation(summary = "修改应用接入")
    @PreAuthorize("@ss.hasPermission('zentao:entry:update')")
    public CommonResult<Boolean> updateEntry(@Valid @RequestBody EntrySaveReqVO reqVO) {
        entryService.updateEntry(BeanUtils.toBean(reqVO, EntryDO.class));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除应用接入")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:entry:delete')")
    public CommonResult<Boolean> deleteEntry(@RequestParam("id") Long id) {
        entryService.deleteEntry(id);
        return success(true);
    }

    @GetMapping("/random-key")
    @Operation(summary = "生成一个 32 位密钥", description = "对应禅道界面的「重新生成密钥」")
    @PreAuthorize("@ss.hasPermission('zentao:entry:query')")
    public CommonResult<String> randomKey() {
        return success(entryService.generateKey());
    }

    @GetMapping("/log-page")
    @Operation(summary = "调用日志分页", description = "对应禅道 entry::log，数据落在通用日志表 zt_log")
    @PreAuthorize("@ss.hasPermission('zentao:entry:query')")
    public CommonResult<PageResult<EntryLogRespVO>> getLogPage(@Valid EntryLogPageReqVO pageReqVO) {
        return success(BeanUtils.toBean(entryService.getLogPage(pageReqVO), EntryLogRespVO.class));
    }

    @GetMapping("/sign")
    @Operation(summary = "计算签名（管理端助手）",
            description = "带 time 时返回 md5(code+key+time)；不带 time 时返回 md5(md5(query)+key)。"
                    + "这是给管理员联调用的，需要 zentao:entry:query 权限")
    @Parameter(name = "code", description = "应用代号", required = true, example = "oa")
    @Parameter(name = "time", description = "时间戳（可选，秒或毫秒）", example = "1700000000")
    @Parameter(name = "query", description = "不带 time 时的查询串（不含 token）", example = "m=user&f=apilogin&account=admin")
    @PreAuthorize("@ss.hasPermission('zentao:entry:query')")
    public CommonResult<EntrySignRespVO> sign(@RequestParam("code") String code,
                                             @RequestParam(value = "time", required = false) String time,
                                             @RequestParam(value = "query", required = false) String query) {
        return success(entryService.sign(code, time, query));
    }

    /**
     * 应用接入校验：第三方系统（OA / 门户 / 移动端）用 {@code code + token} 调它。
     *
     * <p><b>必须免登录</b>（{@link PermitAll}）：调用方还没有 yudao 的登录态，
     * 凭的就是 code + token。校验通过后返回绑定的账号信息并记一笔 zt_log。
     *
     * <p>有意偏离：禅道校验通过后直接建立 PHP 会话完成免密登录；yudao 的认证归 OAuth2，
     * 这里不写会话（详见 {@code EntryService} 的类注释）。
     */
    @PostMapping("/verify")
    @PermitAll
    @Operation(summary = "应用接入校验（免登录）",
            description = "支持两种签名：time 模式 md5(code+key+time)（要求 time > calledTime 防重放）、"
                    + "query 模式 md5(md5(query 去掉 token) + key)。错误码沿用禅道语义：401 签名类、403 IP/账号类、"
                    + "404 应用不存在、405 重放、406 账号不存在、407 时间戳格式错误")
    public CommonResult<EntryVerifyRespVO> verify(@Valid @RequestBody EntryVerifyReqVO reqVO) {
        return success(entryService.verify(reqVO));
    }

}
