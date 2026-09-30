package cn.iocoder.yudao.module.zentao.service.search;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.search.vo.SearchQueryPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.search.SearchDictDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.search.SearchQueryDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.search.SearchDictMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.search.SearchQueryMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 保存查询 / 搜索（禅道 {@code module/search}）。
 *
 * <h3>这个模块有两半，本轮做的是第一半</h3>
 * <pre>
 *   ① 保存查询（zt_userquery）—— 列表页的「搜索」能把条件存下来、下次一键用、还能设成快捷方式；
 *      被所有列表页用到（禅道里 testcase/productplan/release/caselib… 都在调 search->setSearchParams/getQuery）
 *   ② 全文检索（zt_searchindex + zt_searchdict）—— 跨对象的 MATCH…AGAINST 搜索页
 * </pre>
 * 第 ② 半**本轮不做**（要覆盖所有对象类型 + 依赖 InnoDB FULLTEXT 分词，收益低成本高），明确记为未做；
 * 但 {@code zt_searchdict}（拼音首字母码表）作为②的零件一起迁了 —— 它本身很小，且「按拼音搜中文」是
 * 列表页搜索框的常见用法。
 *
 * <h3>最关键的一处有意偏离</h3>
 * 禅道把条件序列化成**一段 SQL**存进 {@code zt_userquery.sql}，列表页直接拼进 WHERE。
 * Java 侧照搬等于把注入口子开到数据层，而且 MyBatis-Plus 根本不接受「半截 SQL」。
 * 所以本实现：{@code form} 存表单快照、{@code sql} 列存**结构化条件 JSON**，
 * 由调用方翻译成各模块的强类型查询 VO —— 这不是偷懒，是「保存查询」在 Java 侧的正确形态。
 */
@Service
@Slf4j
public class SearchService {

    @Resource
    private SearchQueryMapper searchQueryMapper;

    @Resource
    private SearchDictMapper searchDictMapper;

    @Resource
    private AdminUserApi adminUserApi;

    // ==================== 保存查询 ====================

    public PageResult<SearchQueryDO> getPage(SearchQueryPageReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getAccount())) {
            reqVO.setAccount(currentAccount());
        }
        return searchQueryMapper.selectPage(reqVO);
    }

    public SearchQueryDO getQuery(Long id) {
        SearchQueryDO query = searchQueryMapper.selectById(id);
        if (query == null) {
            throw exception(SEARCH_QUERY_NOT_EXISTS);
        }
        return query;
    }

    /** 某模块下「我的 + 公共」的查询（禅道 buildQuery 的口径） */
    public List<SearchQueryDO> listByModule(String account, String module) {
        return searchQueryMapper.selectListByAccountAndModule(
                StrUtil.isBlank(account) ? currentAccount() : account, module);
    }

    /** 列表页页签上的快捷方式 */
    public List<SearchQueryDO> listShortcuts(String account, String module) {
        return searchQueryMapper.selectShortcuts(
                StrUtil.isBlank(account) ? currentAccount() : account, module);
    }

    public Long save(SearchQueryDO query) {
        if (StrUtil.isBlank(query.getModule())) {
            throw exception(SEARCH_QUERY_MODULE_REQUIRED);
        }
        String title = StrUtil.trimToEmpty(query.getTitle());
        if (title.isEmpty()) {
            throw exception(SEARCH_QUERY_TITLE_REQUIRED);
        }
        query.setTitle(title);
        if (query.getShortcut() == null) {
            query.setShortcut(0);
        }
        if (query.getCommon() == null) {
            query.setCommon(0);
        }
        if (query.getId() == null) {
            query.setAccount(StrUtil.blankToDefault(query.getAccount(), currentAccount()));
            searchQueryMapper.insert(query);
            return query.getId();
        }
        SearchQueryDO exists = getQuery(query.getId());
        requireOwner(exists);
        query.setAccount(exists.getAccount());  /* 不允许改归属 */
        searchQueryMapper.updateById(query);
        return query.getId();
    }

    public void delete(Long id) {
        requireOwner(getQuery(id));
        searchQueryMapper.deleteById(id);
    }

    /** 设为/取消快捷方式（禅道 ajaxRemoveMenu / 保存时的 shortcut=onMenuBar） */
    public void setShortcut(Long id, Integer shortcut) {
        requireOwner(getQuery(id));
        SearchQueryDO update = new SearchQueryDO();
        update.setId(id);
        update.setShortcut(shortcut == null ? 0 : shortcut);
        searchQueryMapper.updateById(update);
    }

    /** 只能动自己的查询（禅道用 account 过滤；这里显式报错，别人删你的查询会得到 1020033001） */
    private void requireOwner(SearchQueryDO query) {
        String me = currentAccount();
        if (!StrUtil.equals(query.getAccount(), me)) {
            throw exception(SEARCH_QUERY_NOT_MINE);
        }
    }

    // ==================== 拼音首字母（zt_searchdict） ====================

    /**
     * 取一串中文的拼音首字母（禅道 {@code convert2Pinyin} 的等价物）。
     *
     * <p>码表的键是字符的 Unicode 码点（{@code key}）、值是一位字母（{@code value}），
     * 所以这里逐字查；查不到的字原样保留（禅道也是这个降级行为）。
     */
    public String getPinyinInitials(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        List<Integer> keys = new ArrayList<>();
        for (char c : text.toCharArray()) {
            keys.add((int) c);
        }
        List<SearchDictDO> dicts = searchDictMapper.selectListByKeys(keys);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            String letter = null;
            for (SearchDictDO dict : dicts) {
                if (dict.getKey() != null && dict.getKey() == (int) c) {
                    letter = dict.getValue();
                    break;
                }
            }
            sb.append(letter == null ? c : letter);
        }
        return sb.toString();
    }

    /** 与其它模块同一口径：登录用户 ID → 账号 */
    private String currentAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StrUtil.isNotBlank(user.getUsername()) ? user.getUsername() : "";
    }

}
