package cn.iocoder.yudao.module.zentao.service.dimension;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo.DimensionCurrentRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo.DimensionDropMenuRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo.DimensionItemVO;
import cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo.DimensionRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo.DimensionVisibilityItemVO;
import cn.iocoder.yudao.module.zentao.controller.admin.dimension.vo.DimensionVisibilityRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.dimension.DimensionDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.dimension.DimensionMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.organization.SystemOrgMapper;
import cn.iocoder.yudao.module.zentao.service.search.SearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.DIMENSION_NOT_EXISTS;

/**
 * 维度（禅道 {@code module/dimension}：control 72 行 + model 118 行，2 个 action）。
 *
 * <h3>它在禅道里做什么</h3>
 * BI 的「1.5 级导航」：把大屏 / 透视表 / 图表归类到「宏观 / 效能 / 质量」三个维度下，
 * 提供切换维度的下拉，并记住用户上次所在的维度。它是 {@code chart}/{@code pivot}/{@code screen}
 * 的真实前置依赖，但**开源版不含维度 CRUD / 管理界面** —— 所以本实现只发布「只读维度 + 切换语义」。
 *
 * <h3>照抄的四条规则</h3>
 * <ol>
 *   <li><b>可见性</b>（{@code biModel::getViewableObject('dimension')}，{@code module/bi/model.php:41-65}）：
 *       {@code acl='open'} 或 {@code createdBy=自己} 或 {@code whitelist} 命中自己；超管（admins）直通全部。
 *       判定**不在 dimension 模块里**，这是禅道的分层。</li>
 *   <li><b>末次维度四级兜底链</b>（{@code dimensionModel::saveState()}，{@code model.php:83-117}）：
 *       配置 → 会话 → 可见性校验（不在可见集合里就取可见的第一条）→ {@code getFirst()}。</li>
 *   <li><b>切换后双写</b>（{@code dimensionModel::getDimension()}）：{@code saveState()} 写 session，
 *       紧接着写 setting 项 {@code {account}common.dimension.lastDimension}。</li>
 *   <li><b>下拉链接的两处参数例外</b>（{@code control.php:39-46}）：{@code pivot-design → browse}；
 *       {@code tab=bi + tree-browsegroup} 追加 {@code groupID=0&type={viewType}}。</li>
 * </ol>
 *
 * <h3>三处有意偏离（README 有记录）</h3>
 * <table>
 *   <tr><th>禅道</th><th>本实现</th><th>理由</th></tr>
 *   <tr><td>维度 CRUD / 管理界面</td><td>不做</td>
 *       <td>开源版就没有：{@code lang} 里的 {@code aclList} 无调用点，全库只有
 *           {@code upgrade/model.php:6971} 直接 INSERT。管理端留 P3。</td></tr>
 *   <tr><td>session（按 {@code app->tab} 分桶）+ setting 表</td><td>Redis 用户级末次访问记录</td>
 *       <td>本项目不迁 {@code zt_setting}，也没有可写的用户级配置 API。键
 *           {@code zentao:dimension:last:{tab}:{account}} 与 session 语义等价（重启后仍在），
 *           兜底顺序与写回时机完全照抄。</td></tr>
 *   <tr><td>{@code createLink} 生成 {@code index.php?m=x&f=y&...}</td>
 *       <td>{@code /{module}/{method}?{params}}</td>
 *       <td>PHP 路由在 yudao 里没有意义；参数与两条例外照抄，并把 module/method/params 单列。</td></tr>
 * </table>
 *
 * <p>另外 {@code /get} 比禅道多了一层<b>可见性校验</b>：禅道 {@code getByID()} 不校验，因为它只被
 * 「切维度」链路内部调用（入参来自 config/session），从不暴露成 HTTP 接口；本实现把它做成接口，
 * 就必须挡住「用 id 遍历私有维度」。不可见时返回「维度不存在」而不是单独的错误码（不新增错误码）。
 */
@Service
@Slf4j
public class DimensionService {

    /** 禅道 {@code zt_dimension.acl} 的两个取值 */
    public static final String ACL_OPEN = "open";
    public static final String ACL_PRIVATE = "private";

    /** 禅道 1.5 级导航所在的标签页（{@code $this->app->tab == 'bi'}） */
    public static final String TAB_BI = "bi";

    /** 禅道 {@code $lang->searchAB}（module/common/lang/zh-cn.php:520） */
    private static final String SEARCH_HINT = "搜索";

    /** 禅道 {@code $lang->dimension->common}（module/dimension/lang/zh-cn.php） */
    private static final String LABEL_DIMENSION = "维度";

    /** Redis 键前缀：{@code zentao:dimension:last:{tab}:{account}}（等价于禅道的 session->dimension[tab]） */
    private static final String LAST_KEY_PREFIX = "zentao:dimension:last:";

    /** 末次维度记录的存活时间：禅道 setting 项是永久的，这里给 180 天，避免键无限期堆积 */
    private static final long LAST_KEY_TTL_DAYS = 180L;

    // 四级兜底链的命中来源
    private static final String SOURCE_EXPLICIT = "explicit";
    private static final String SOURCE_CONFIG = "config";
    private static final String SOURCE_LAST = "last";
    private static final String SOURCE_FALLBACK = "fallback";
    private static final String SOURCE_FIRST = "first";
    private static final String SOURCE_NONE = "none";

    /** 可见性判据（照抄 module/bi/model.php:41-65），诊断接口原样返回给前端显示 */
    private static final String VISIBILITY_RULE =
            "acl = 'open' OR createdBy = 自己 OR FIND_IN_SET(自己, whitelist)；超管（yudao 的 super_admin 角色，"
                    + "禅道原为 zt_company.admins）直通全部。deleted=0 才参与判定。";

    @Resource
    private DimensionMapper dimensionMapper;

    /** 拼音首字母（禅道 {@code common::convert2Pinyin} 的等价物，走 zt_searchdict 码表） */
    @Resource
    private SearchService searchService;

    @Resource
    private AdminUserApi adminUserApi;

    /** 判超管：yudao 的 super_admin 是硬编码放行、不查权限表（坑位 #24），只能按角色查 */
    @Resource
    private SystemOrgMapper systemOrgMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 禅道 {@code config->dimensions->lastDimension}（来自 setting 表；本项目用配置项，同 zentao.score.enabled） */
    @Value("${zentao.dimension.last-dimension:0}")
    private Long lastDimensionFromConfig;

    // ==================== 查 ====================

    /** 当前账号可见的维度（禅道 {@code dimensionModel::getList()}） */
    public List<DimensionDO> getList() {
        return listViewable();
    }

    /**
     * 按 id 取维度（禅道 {@code dimensionModel::getByID()} + 本实现补的可见性校验）。
     *
     * <p>不可见与不存在都返回 {@code DIMENSION_NOT_EXISTS} —— 不区分，免得成为「私有维度探针」。
     */
    public DimensionDO getById(Long id) {
        DimensionDO dimension = dimensionMapper.selectById(id);
        if (dimension == null || !viewableIds().contains(dimension.getId())) {
            throw exception(DIMENSION_NOT_EXISTS, id);
        }
        return dimension;
    }

    /**
     * 当前维度（禅道 {@code dimensionModel::getDimension()}）：走四级兜底链，并把最终结果写回末次记录。
     *
     * @param dimensionID 期望的维度编号（0/null = 没指定，走兜底）
     * @param tab         标签页（禅道 {@code app->tab}；默认 bi）
     */
    public DimensionCurrentRespVO getCurrentDimension(Long dimensionID, String tab) {
        String targetTab = StrUtil.blankToDefault(tab, TAB_BI);
        State state = saveState(dimensionID, targetTab);

        DimensionCurrentRespVO resp = new DimensionCurrentRespVO();
        resp.setDimensionID(state.dimensionID);
        resp.setTab(targetTab);
        resp.setSource(state.source);
        resp.setSourceDesc(sourceDesc(state.source));
        if (state.dimensionID != null && state.dimensionID > 0) {
            resp.setDimension(BeanUtils.toBean(dimensionMapper.selectById(state.dimensionID), DimensionRespVO.class));
        }
        resp.setViewableIds(viewableIds());
        return resp;
    }

    /** 1.5 级导航下拉（禅道 {@code dimension::ajaxGetDropMenu}） */
    public DimensionDropMenuRespVO getDropMenu(Long dimensionID, String module, String method,
                                               String viewType, String tab) {
        DimensionDropMenuRespVO resp = new DimensionDropMenuRespVO();
        resp.setData(getItems());
        resp.setSearchHint(SEARCH_HINT);
        resp.setExpandName("closed");
        resp.setItemType("dimension");
        resp.setLabelMap(Map.of("dimension", LABEL_DIMENSION));

        // 例外 ①：透视表的设计页改成浏览页（control.php:39）
        String targetModule = StrUtil.blankToDefault(module, "");
        String targetMethod = StrUtil.blankToDefault(method, "");
        if ("pivot".equals(targetModule) && "design".equals(targetMethod)) {
            targetMethod = "browse";
        }

        // 例外 ②：BI 里的 tree/browsegroup 需要带上 groupID 与视图类型（control.php:42-46）
        String params = "dimensionID={id}";
        if (TAB_BI.equals(StrUtil.blankToDefault(tab, TAB_BI)) && "tree".equals(targetModule)
                && "browsegroup".equals(targetMethod)) {
            params = "dimensionID={id}&groupID=0&type=" + StrUtil.blankToDefault(viewType, "");
        }

        Map<String, String> link = new LinkedHashMap<>();
        link.put("dimension", "/" + targetModule + "/" + targetMethod + "?" + params);
        resp.setLink(link);
        resp.setDimensionID(dimensionID);
        resp.setModule(targetModule);
        resp.setMethod(targetMethod);
        resp.setParams(params);
        return resp;
    }

    /** 下拉项（禅道 items：{id, text, keys(拼音)}） */
    public List<DimensionItemVO> getItems() {
        List<DimensionItemVO> items = new ArrayList<>();
        for (DimensionDO dimension : listViewable()) {
            DimensionItemVO item = new DimensionItemVO();
            item.setId(dimension.getId());
            item.setText(dimension.getName());
            item.setKeys(pinyinOf(dimension.getName()));
            items.add(item);
        }
        return items;
    }

    // ==================== 可见性口径自检（只读诊断） ====================

    /**
     * 复算「某账号能看到哪些维度」（{@code biModel::getViewableObject}）。
     *
     * <p>存在的意义：admin 是超管、走「直通全部」的短路，用 admin 调任何接口都验证不了
     * {@code acl/createdBy/whitelist} 这三句；这个只读视图让任意账号都能复算一遍。
     * 做法对齐 {@code company} 的 {@code /admins} 口径对照（README 3.44②）。
     */
    public DimensionVisibilityRespVO getVisibility(String account) {
        String target = StrUtil.blankToDefault(account, currentAccount());
        boolean superAdmin = isSuperAdmin(target);

        List<DimensionVisibilityItemVO> rows = new ArrayList<>();
        List<Long> viewable = new ArrayList<>();
        for (DimensionDO dimension : dimensionMapper.selectAllOrdered()) {
            String reason = visibilityReason(dimension, target, superAdmin);
            DimensionVisibilityItemVO row = new DimensionVisibilityItemVO();
            row.setId(dimension.getId());
            row.setName(dimension.getName());
            row.setAcl(dimension.getAcl());
            row.setCreatedBy(dimension.getCreatedBy());
            row.setWhitelist(dimension.getWhitelist());
            row.setReason(reason);
            row.setVisible(!"none".equals(reason));
            rows.add(row);
            if (row.getVisible()) {
                viewable.add(dimension.getId());
            }
        }

        DimensionVisibilityRespVO resp = new DimensionVisibilityRespVO();
        resp.setAccount(target);
        resp.setSuperAdmin(superAdmin);
        resp.setRule(VISIBILITY_RULE);
        resp.setDimensions(rows);
        resp.setViewableIds(viewable);
        resp.setNote("禅道把这段判定放在 bi 模块（biModel::getViewableObject），不在 dimension 模块里；"
                + "超管是短路分支（禅道看 zt_company.admins，本项目看 yudao 的 super_admin 角色），"
                + "所以超管看到的是全部维度，非超管才逐行走 acl/createdBy/whitelist 三句。");
        return resp;
    }

    /** 判断某账号对某维度是否可见；返回命中的判据（admin/open/creator/whitelist/none） */
    private String visibilityReason(DimensionDO dimension, String account, boolean superAdmin) {
        if (superAdmin) {
            return "admin";
        }
        if (ACL_OPEN.equals(dimension.getAcl())) {
            return "open";
        }
        if (StrUtil.isNotBlank(account) && account.equals(dimension.getCreatedBy())) {
            return "creator";
        }
        // 禅道是 explode(',', whitelist) + in_array(account)（精确匹配，不 trim）
        if (StrUtil.isNotBlank(account)
                && StrUtil.split(StrUtil.blankToDefault(dimension.getWhitelist(), ""), ',').contains(account)) {
            return "whitelist";
        }
        return "none";
    }

    /** 禅道 {@code app->user->admin}：本项目用 yudao 的 super_admin 角色（硬编码放行，不查权限表） */
    private boolean isSuperAdmin(String account) {
        if (StrUtil.isBlank(account)) {
            return false;
        }
        List<AdminUserRespDTO> users = adminUserApi.getUserListByUsernames(List.of(account));
        if (users == null || users.isEmpty() || users.get(0) == null) {
            return false;
        }
        Long count = systemOrgMapper.countSuperAdminRoleByUser(users.get(0).getId());
        return count != null && count > 0;
    }

    // ==================== 内部：可见性 / 兜底链 / 末次记录 ====================

    /** 当前账号可见的维度列表（超管直通全部，否则走三选一 SQL） */
    private List<DimensionDO> listViewable() {
        String account = currentAccount();
        if (StrUtil.isNotBlank(account) && isSuperAdmin(account)) {
            return dimensionMapper.selectAllOrdered();
        }
        return dimensionMapper.selectViewableOrdered(account);
    }

    private List<Long> viewableIds() {
        return listViewable().stream().map(DimensionDO::getId).toList();
    }

    /**
     * 禅道 {@code saveState()} 的逐行移植（四级兜底链）。
     *
     * <p>注意第 ③ 步的守卫：禅道写的是 {@code if($dimensionID && $viewableObjects && !in_array(...))} ——
     * 「一个可见对象都没有」时**不改写** dimensionID，随后由第 ④ 步的「对象是否存在」兜底。
     * 这个守卫很容易被顺手简化掉，简化之后「无可见维度」的账号会拿到 0 而不是「配置里的维度」。
     */
    private State saveState(Long dimensionID, String tab) {
        Long id = dimensionID == null ? 0L : dimensionID;
        String source = id != 0L ? SOURCE_EXPLICIT : null;

        // ① config->dimensions->lastDimension
        if (id == 0L && lastDimensionFromConfig != null && lastDimensionFromConfig > 0) {
            id = lastDimensionFromConfig;
            source = SOURCE_CONFIG;
        }

        // ② session->dimension（本项目：Redis 的用户级末次访问记录）
        if (id == 0L) {
            Long last = loadLast(tab);
            if (last != null && last > 0) {
                id = last;
                source = SOURCE_LAST;
            }
        }

        // ③ 验证维度是否可见
        List<DimensionDO> viewable = listViewable();
        // 局部变量 id 后面还要被改写，lambda 里只能用一次赋值的副本（Java 的 effectively final）
        Long candidate = id;
        if (candidate != 0L && !viewable.isEmpty()
                && viewable.stream().noneMatch(d -> candidate.equals(d.getId()))) {
            id = viewable.get(0).getId();
            source = SOURCE_FALLBACK;
        }

        // ④ 检查对象是否存在；不存在就取第一条
        if (id != 0L && dimensionMapper.selectById(id) == null) {
            id = 0L;
            source = null;
        }
        if (id == 0L) {
            if (viewable.isEmpty()) {
                source = SOURCE_NONE;
            } else {
                id = viewable.get(0).getId();
                source = SOURCE_FIRST;
            }
        }

        // 写回（禅道：session->set + setting 项 {account}common.dimension.lastDimension）
        saveLast(tab, id);
        return new State(id, source);
    }

    private String lastKey(String tab, String account) {
        return LAST_KEY_PREFIX + tab + ":" + account;
    }

    /** 读末次维度；Redis 不可用时降级为「没有记录」（禅道对 session 里的假值也是这个态度） */
    private Long loadLast(String tab) {
        String account = currentAccount();
        if (StrUtil.isBlank(account)) {
            return null;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(lastKey(tab, account));
            return StrUtil.isBlank(value) ? null : Long.parseLong(value.trim());
        } catch (Exception e) {
            log.warn("[loadLast][读取末次维度失败，降级为无记录] tab={} account={}", tab, account, e);
            return null;
        }
    }

    private void saveLast(String tab, Long dimensionID) {
        String account = currentAccount();
        if (StrUtil.isBlank(account) || dimensionID == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(lastKey(tab, account), String.valueOf(dimensionID),
                    LAST_KEY_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("[saveLast][写入末次维度失败] tab={} account={} dimensionID={}", tab, account, dimensionID, e);
        }
    }

    /** 拼音首字母（禅道 {@code common::convert2Pinyin}）：码表缺字时原样保留，查不到就退化成原文 */
    private String pinyinOf(String name) {
        try {
            String pinyin = searchService.getPinyinInitials(name);
            return StrUtil.isBlank(pinyin) ? name : pinyin;
        } catch (Exception e) {
            log.warn("[pinyinOf][拼音码表不可用，退化为原文] name={}", name, e);
            return name;
        }
    }

    private String sourceDesc(String source) {
        if (source == null) {
            return "";
        }
        return switch (source) {
            case SOURCE_EXPLICIT -> "来自入参 dimensionID（禅道 saveState($dimensionID) 直接用传入值）";
            case SOURCE_CONFIG -> "来自配置 zentao.dimension.last-dimension（禅道 config->dimensions->lastDimension）";
            case SOURCE_LAST -> "来自末次访问记录（禅道 session->dimension，本实现存 Redis）";
            case SOURCE_FALLBACK -> "配置/末次记录里的维度不可见，回退到可见的第一条（禅道 saveState 第③步）";
            case SOURCE_FIRST -> "没有任何历史记录，取可见的第一条（禅道 getFirst()）";
            default -> "一个可见维度都没有（禅道会返回 0）";
        };
    }

    /** 与其它模块同一口径：登录用户 ID → 账号（禅道人员引用存账号） */
    private String currentAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StrUtil.isNotBlank(user.getUsername()) ? user.getUsername() : "";
    }

    /** 兜底链的中间结果（dimensionID + 命中来源） */
    private record State(Long dimensionID, String source) {
    }

}
