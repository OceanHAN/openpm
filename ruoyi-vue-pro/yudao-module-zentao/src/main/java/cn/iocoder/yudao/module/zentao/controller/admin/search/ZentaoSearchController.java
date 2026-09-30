package cn.iocoder.yudao.module.zentao.controller.admin.search;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.search.vo.SearchQueryPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.search.vo.SearchQueryRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.search.vo.SearchQuerySaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.search.SearchQueryDO;
import cn.iocoder.yudao.module.zentao.service.search.SearchService;
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
 * 保存查询 / 搜索（禅道 {@code module/search}）。
 *
 * <p>禅道有 11 个 action，其中 3 个（buildOldForm/buildOldQuery/saveOldQuery）是 v20 之前的老页面，
 * 本实现只做新链路：保存/取用/删除/快捷方式 + 拼音首字母词典。
 * 全文检索（{@code zt_searchindex}）本轮不做，见 {@link SearchService} 的类注释。
 */
@Tag(name = "管理后台 - 禅道保存查询")
@RestController
@RequestMapping("/zentao/search")
@Validated
public class ZentaoSearchController {

    @Resource
    private SearchService searchService;

    @GetMapping("/query/page")
    @Operation(summary = "保存的查询分页", description = "不传 account 就是「我的查询」")
    @PreAuthorize("@ss.hasPermission('zentao:search:query')")
    public CommonResult<PageResult<SearchQueryRespVO>> getPage(@Valid SearchQueryPageReqVO pageReqVO) {
        PageResult<SearchQueryDO> page = searchService.getPage(pageReqVO);
        return success(toVoPage(page));
    }

    @GetMapping("/query/get")
    @Operation(summary = "获得保存的查询")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:search:query')")
    public CommonResult<SearchQueryRespVO> getQuery(@RequestParam("id") Long id) {
        return success(toVo(searchService.getQuery(id)));
    }

    @GetMapping("/query/list")
    @Operation(summary = "某模块下的查询（我的 + 公共）",
            description = "禅道 buildQuery 的口径：列表页打开时拿它渲染「已保存的查询」下拉")
    @Parameter(name = "module", description = "模块", required = true, example = "story")
    @Parameter(name = "account", description = "账号（不传=当前登录账号）", example = "admin")
    @PreAuthorize("@ss.hasPermission('zentao:search:query')")
    public CommonResult<List<SearchQueryRespVO>> listByModule(@RequestParam("module") String module,
                                                             @RequestParam(value = "account", required = false) String account) {
        return success(searchService.listByModule(account, module).stream().map(this::toVo).toList());
    }

    @GetMapping("/query/shortcut-list")
    @Operation(summary = "快捷方式列表", description = "显示在列表页页签上的那几个")
    @Parameter(name = "module", description = "模块", required = true, example = "story")
    @PreAuthorize("@ss.hasPermission('zentao:search:query')")
    public CommonResult<List<SearchQueryRespVO>> listShortcuts(@RequestParam("module") String module,
                                                              @RequestParam(value = "account", required = false) String account) {
        return success(searchService.listShortcuts(account, module).stream().map(this::toVo).toList());
    }

    @PostMapping("/query/save")
    @Operation(summary = "保存/修改查询",
            description = "conditions 是**结构化条件 JSON**，不是 SQL（见表与 DO 的注释）："
                    + "禅道存的是 SQL 片段，Java 侧照搬会有注入风险且 MP 不接受半截 SQL")
    @PreAuthorize("@ss.hasPermission('zentao:search:save')")
    public CommonResult<Long> save(@Valid @RequestBody SearchQuerySaveReqVO reqVO) {
        SearchQueryDO query = new SearchQueryDO();
        query.setId(reqVO.getId());
        query.setModule(reqVO.getModule());
        query.setTitle(reqVO.getTitle());
        query.setForm(reqVO.getForm());
        query.setSql(reqVO.getConditions());
        query.setShortcut(reqVO.getShortcut());
        query.setCommon(reqVO.getCommon());
        return success(searchService.save(query));
    }

    @PutMapping("/query/shortcut")
    @Operation(summary = "设为/取消快捷方式")
    @PreAuthorize("@ss.hasPermission('zentao:search:save')")
    public CommonResult<Boolean> setShortcut(@RequestParam("id") Long id,
                                            @RequestParam("shortcut") Integer shortcut) {
        searchService.setShortcut(id, shortcut);
        return success(true);
    }

    @DeleteMapping("/query/delete")
    @Operation(summary = "删除保存的查询", description = "只能删自己的（禅道按 account 过滤；这里越权会明确报错）")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:search:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        searchService.delete(id);
        return success(true);
    }

    @GetMapping("/dict/pinyin")
    @Operation(summary = "取拼音首字母", description = "禅道 convert2Pinyin 的等价物：逐字查 zt_searchdict 码表")
    @Parameter(name = "text", description = "中文文本", required = true, example = "需求")
    @PreAuthorize("@ss.hasPermission('zentao:search:query')")
    public CommonResult<String> pinyin(@RequestParam("text") String text) {
        return success(searchService.getPinyinInitials(text));
    }

    private PageResult<SearchQueryRespVO> toVoPage(PageResult<SearchQueryDO> page) {
        PageResult<SearchQueryRespVO> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setList(page.getList().stream().map(this::toVo).toList());
        return result;
    }

    /** 表里的 {@code sql} 列 → VO 的 {@code conditions} 字段（换个名字，免得调用方以为能直接拼 SQL） */
    private SearchQueryRespVO toVo(SearchQueryDO query) {
        SearchQueryRespVO vo = BeanUtils.toBean(query, SearchQueryRespVO.class);
        if (vo != null) {
            vo.setConditions(query.getSql());
        }
        return vo;
    }

}
