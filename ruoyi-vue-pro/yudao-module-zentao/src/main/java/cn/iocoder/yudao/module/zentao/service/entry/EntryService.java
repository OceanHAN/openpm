package cn.iocoder.yudao.module.zentao.service.entry;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntryLogPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntryPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntrySignRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntryVerifyReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntryVerifyRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.entry.EntryDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.entry.EntryLogDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.entry.EntryLogMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.entry.EntryMapper;
import cn.iocoder.yudao.module.zentao.service.score.ScoreService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 应用接入（禅道 {@code module/entry} + {@code common::checkEntry}）。
 *
 * <h3>为什么这个模块值得搬</h3>
 * 它是禅道**唯一**的「第三方免登录入口」：OA / 门户 / 移动端拿 {@code code + token} 就能调接口，
 * 勾了「免密登录」还能直接以某个账号的身份跳进禅道。它的价值全在那条校验链上，而不在 CRUD：
 *
 * <pre>
 *   code / token 缺失 → 401
 *   按 code 查应用 → 没有 → 404 EMPTY_ENTRY = 应用不存在
 *   应用没配 key → 401 EMPTY_KEY
 *   来源 IP 不在白名单 → 403 IP_DENIED
 *   token 不对 → 401 INVALID_TOKEN
 *   非免密又没绑账号 → 403 ACCOUNT_UNBOUND
 *   账号在用户表里不存在 → 406 INVALID_ACCOUNT
 * </pre>
 *
 * <h3>两种签名（禅道 task #5384 之后）</h3>
 * <ol>
 *   <li><b>带时间戳</b>：{@code token = md5(code + key + time)}，且必须 {@code time > calledTime}（防重放），
 *       校验通过后把 {@code time} 写回 {@code calledTime}。注意时间戳只校验「截断后 10 位、首位 < '4'」，
 *       但摘要用的是**原始**（可能 13 位毫秒）时间戳 —— 这个不对称是禅道原样，见下面的实现注释。</li>
 *   <li><b>不带时间戳</b>：{@code token = md5(md5(queryString 去掉 token) + key)}。</li>
 * </ol>
 * 第一种失败时会**继续**尝试第二种（且 queryString 里仍含 time），这是禅道的 fallthrough 行为。
 *
 * <h3>与禅道的两处**有意偏离**（都不影响校验能力）</h3>
 * <ol>
 *   <li><b>不建立登录会话</b>：禅道校验通过后直接 {@code session->set('user', $user)} 完成免密登录。
 *       另外禅道此时还会计一次登录分（{@code score->create('user','login')}）—— 那一条已经接上了。
 *       yudao 的认证由 OAuth2（{@code Admin-Token} + Redis）统一负责，一个业务模块不能也不应该自己写会话。
 *       所以 {@code verify} 只做「校验 + 记账 + 返回账号信息」，需要真正登录时由调用方走 yudao 的登录接口
 *       （这也是「协议网关」与「业务模块」的边界，README 里单独记了这一条）。</li>
 *   <li><b>多了个签名助手</b>：{@code /sign} 用管理端权限帮管理员算出 token，
 *       便于对接方自测；它需要 {@code zentao:entry:query} 权限，不是公开接口。</li>
 * </ol>
 */
@Service
@Slf4j
public class EntryService {

    /** 禅道 {@code zt_log.objectType} 里应用接入的取值 */
    public static final String OBJECT_TYPE_ENTRY = "entry";

    /** 禅道 {@code $lang->entry->note->code} + DAO 的 {@code check('code','code')}：字母或数字 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9]+$");

    /** 禅道全局默认白名单（{@code config->ipWhiteList = '*'}） */
    private static final String DEFAULT_IP_WHITE_LIST = "*";

    /**
     * 禅道 {@code entry::getList()} 里有 {@code andWhere('code')->ne('gitfox')}：
     * {@code gitfox} 是内置的代码库接入应用，不在「应用列表」里露出来。
     */
    private static final String INTERNAL_CODE_GITFOX = "gitfox";

    @Resource
    private EntryMapper entryMapper;

    @Resource
    private EntryLogMapper entryLogMapper;

    @Resource
    private AdminUserApi adminUserApi;

    @Resource
    private ScoreService scoreService;

    // ==================== 查 ====================

    public PageResult<EntryDO> getPage(EntryPageReqVO reqVO) {
        return entryMapper.selectPage(reqVO);
    }

    /**
     * 下拉用的精简列表。
     *
     * <p>这里刻意**不用**链式调用：{@code LambdaQueryWrapperX} 只覆写了一部分方法，
     * 继承来的方法（如 {@code ne}、{@code orderByAsc}）会把表达式类型退回 {@code LambdaQueryWrapper}，
     * 链到尾上就不再是 X 了（坑位 #1 的变体）。逐条语句调用最稳。
     */
    public List<EntryDO> getSimpleList() {
        LambdaQueryWrapperX<EntryDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.and(true, inner -> inner.ne(EntryDO::getCode, INTERNAL_CODE_GITFOX));
        wrapper.orderByAsc(EntryDO::getId);
        return entryMapper.selectList(wrapper);
    }

    public EntryDO getEntry(Long id) {
        EntryDO entry = entryMapper.selectById(id);
        if (entry == null) {
            throw exception(ENTRY_NOT_EXISTS);
        }
        return entry;
    }

    public EntryDO getByCode(String code) {
        return entryMapper.selectByCode(code);
    }

    public PageResult<EntryLogDO> getLogPage(EntryLogPageReqVO reqVO) {
        return entryLogMapper.selectPage(reqVO);
    }

    // ==================== 写 ====================

    public Long createEntry(EntryDO entry) {
        validateCode(entry.getCode(), null);
        validateRequired(entry);
        if (StrUtil.isBlank(entry.getKey())) {
            entry.setKey(generateKey());
        }
        entryMapper.insert(entry);
        return entry.getId();
    }

    public void updateEntry(EntryDO entry) {
        getEntry(entry.getId());
        validateCode(entry.getCode(), entry.getId());
        validateRequired(entry);
        if (StrUtil.isBlank(entry.getKey())) {
            entry.setKey(generateKey());
        }
        entryMapper.updateById(entry);
    }

    public void deleteEntry(Long id) {
        getEntry(id);
        entryMapper.deleteById(id);
    }

    /** 32 位随机密钥（禅道界面上「重新生成密钥」的等价物） */
    public String generateKey() {
        return cn.hutool.core.util.RandomUtil.randomString(32);
    }

    /**
     * 必填校验，照抄禅道 {@code model::create()}：
     * {@code freePasswd == 1} 时必填项收缩为 {@code name, code, key}（免密登录可以不绑账号）。
     */
    private void validateRequired(EntryDO entry) {
        boolean free = entry.getFreePasswd() != null && entry.getFreePasswd() == 1;
        if (!free && StrUtil.isBlank(entry.getAccount())) {
            throw exception(ENTRY_ACCOUNT_UNBOUND);
        }
    }

    private void validateCode(String code, Long excludeId) {
        if (StrUtil.isBlank(code) || !CODE_PATTERN.matcher(code).matches()) {
            throw exception(ENTRY_CODE_INVALID, code);
        }
        /* 禅道 check('code','unique') 不带 deleted 条件：删掉的应用仍占着这个代号（坑：不要用普通查询） */
        Long existsId = entryMapper.selectIdByCodeIgnoreDeleted(code);
        if (existsId != null && !existsId.equals(excludeId)) {
            throw exception(ENTRY_CODE_DUPLICATE, code);
        }
    }

    // ==================== 签名 ====================

    /** 算出 token（管理端助手，便于对接方自测） */
    public EntrySignRespVO sign(String code, String time, String query) {
        EntryDO entry = entryMapper.selectByCode(code);
        if (entry == null) {
            throw exception(ENTRY_NOT_EXISTS);
        }
        if (StrUtil.isBlank(entry.getKey())) {
            throw exception(ENTRY_EMPTY_KEY);
        }
        EntrySignRespVO resp = new EntrySignRespVO();
        resp.setCode(code);
        if (StrUtil.isNotBlank(time)) {
            resp.setTime(time);
            resp.setMode("time");
            resp.setToken(md5(code + entry.getKey() + time));
        } else {
            resp.setMode("query");
            resp.setToken(md5(md5(query == null ? "" : query) + entry.getKey()));
        }
        return resp;
    }

    // ==================== 校验（禅道 common::checkEntry 的等价物） ====================

    public EntryVerifyRespVO verify(EntryVerifyReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getCode())) {
            throw exception(ENTRY_PARAM_CODE_MISSING);
        }
        if (StrUtil.isBlank(reqVO.getToken())) {
            throw exception(ENTRY_PARAM_TOKEN_MISSING);
        }
        EntryDO entry = entryMapper.selectByCode(reqVO.getCode());
        if (entry == null) {
            throw exception(ENTRY_NOT_EXISTS);
        }
        if (StrUtil.isBlank(entry.getKey())) {
            throw exception(ENTRY_EMPTY_KEY);
        }

        String clientIp = StrUtil.isNotBlank(reqVO.getClientIp()) ? reqVO.getClientIp() : ServletUtils.getClientIP();
        if (!checkIP(entry.getIp(), clientIp)) {
            throw exception(ENTRY_IP_DENIED, clientIp);
        }

        String tokenMode = checkToken(entry, reqVO);

        /* 非免密且没绑账号 → 禅道 ACCOUNT_UNBOUND(403) */
        if (!isFreePasswd(entry) && StrUtil.isBlank(entry.getAccount())) {
            throw exception(ENTRY_ACCOUNT_UNBOUND);
        }

        /* 免密登录时，调用方可以指定以哪个账号进入（禅道 user.apilogin 的 account 参数） */
        String account = entry.getAccount();
        if (isFreePasswd(entry) && isApiLogin(reqVO) && StrUtil.isNotBlank(reqVO.getAccount())) {
            account = reqVO.getAccount();
        }
        AdminUserRespDTO user = findUserByAccount(account);
        if (user == null) {
            throw exception(ENTRY_INVALID_ACCOUNT, account);
        }

        /* 计分：禅道 common::checkEntry 校验通过后会 loadModel('score')->create('user','login')，
           这一轮把 score 模块迁进来之后，这条链路终于对上了（一天最多 3 分，见 ScoreRules）。
           计分失败不能影响校验（createQuietly 内部吞异常）。 */
        scoreService.createQuietly("user", "login", null, account, java.time.LocalDateTime.now());

        /* 记账：写 zt_log + 更新 calledTime（时间戳模式已在 checkToken 里更新过） */
        Long logId = saveLog(entry.getId(), StrUtil.blankToDefault(reqVO.getUrl(),
                "/index.php?m=" + StrUtil.blankToDefault(reqVO.getModule(), "") + "&f=" + StrUtil.blankToDefault(reqVO.getMethod(), "")),
                "success:" + tokenMode);

        EntryVerifyRespVO resp = new EntryVerifyRespVO();
        resp.setEntryId(entry.getId());
        resp.setName(entry.getName());
        resp.setCode(entry.getCode());
        resp.setAccount(account);
        resp.setFreePasswd(entry.getFreePasswd());
        resp.setTokenMode(tokenMode);
        resp.setCalledTime(entry.getCalledTime());
        resp.setUserId(user.getId());
        resp.setUserNickname(user.getNickname());
        resp.setLogId(logId);
        resp.setMessage(isFreePasswd(entry)
                ? "校验通过：禅道此时会免密跳转到首页并建立登录态；yudao 侧认证由 OAuth2 负责，本接口只返回账号信息"
                : "校验通过：禅道此时记录登录态与调用日志；yudao 侧认证由 OAuth2 负责");
        return resp;
    }

    /**
     * 校验 token，返回命中的方式（{@code time} / {@code query}）。
     *
     * <p>照抄禅道 {@code checkEntryToken}，包含两个容易踩的地方：
     * <ul>
     *   <li>时间戳**只校验**截断后的 10 位，却用**原始**字符串算摘要 ——
     *       所以 13 位毫秒时间戳也能通过（对接方按原样拼接即可）。</li>
     *   <li>时间戳模式失败后**不返回**，继续用查询串模式再算一次（此时 queryString 里仍含 time）。</li>
     * </ul>
     */
    private String checkToken(EntryDO entry, EntryVerifyReqVO reqVO) {
        String rawTime = reqVO.getTime();
        if (StrUtil.isNotBlank(rawTime)) {
            String timestamp = rawTime.length() > 10 ? rawTime.substring(0, 10) : rawTime;
            if (timestamp.length() != 10 || timestamp.charAt(0) >= '4') {
                throw exception(ENTRY_ERROR_TIMESTAMP);
            }
            if (reqVO.getToken().equals(md5(entry.getCode() + entry.getKey() + rawTime))) {
                int calledTime = entry.getCalledTime() == null ? 0 : entry.getCalledTime();
                if (Integer.parseInt(timestamp) <= calledTime) {
                    throw exception(ENTRY_CALLED_TIME_REPLAY);
                }
                entryMapper.updateCalledTime(entry.getCode(), Integer.parseInt(timestamp));
                entry.setCalledTime(Integer.parseInt(timestamp));
                return "time";
            }
        }
        String query = reqVO.getQuery() == null ? "" : reqVO.getQuery();
        if (reqVO.getToken().equals(md5(md5(query) + entry.getKey()))) {
            return "query";
        }
        throw exception(ENTRY_INVALID_TOKEN);
    }

    private boolean isFreePasswd(EntryDO entry) {
        return entry.getFreePasswd() != null && entry.getFreePasswd() == 1;
    }

    /** 禅道：{@code $_GET['m'] == 'user' && strtolower($_GET['f']) == 'apilogin' && $_GET['account']} */
    private boolean isApiLogin(EntryVerifyReqVO reqVO) {
        return "user".equals(reqVO.getModule())
                && "apilogin".equalsIgnoreCase(StrUtil.blankToDefault(reqVO.getMethod(), ""));
    }

    private AdminUserRespDTO findUserByAccount(String account) {
        if (StrUtil.isBlank(account)) {
            return null;
        }
        List<AdminUserRespDTO> users = adminUserApi.getUserListByUsernames(List.of(account));
        return users.isEmpty() ? null : users.get(0);
    }

    /** 写 {@code zt_log}（禅道 {@code entry::saveLog}），返回日志编号 */
    public Long saveLog(Long entryId, String url, String result) {
        EntryLogDO log = new EntryLogDO();
        log.setObjectType(OBJECT_TYPE_ENTRY);
        log.setObjectID(entryId);
        log.setUrl(url);
        log.setDate(LocalDateTime.now());
        log.setResult(result);
        entryLogMapper.insert(log);
        return log.getId();
    }

    // ==================== IP 白名单（禅道 common::checkIP 的等价物） ====================

    /**
     * 判断 IP 是否在白名单内。支持禅道的全部形态：
     * <pre>
     *   '*'                      → 放行
     *   '192.168.1.10'           → 精确匹配
     *   'a,b,c'                  → 逗号列表（任一命中即放行，递归）
     *   '192.168.1.1-192.168.1.10' → 区间（ip2long 比较）
     *   '192.168.1.*'            → 通配（按点的个数补 0 / 255，也支持 192.* / 192.168.*）
     *   '192.168.1.0/24'         → CIDR
     * </pre>
     *
     * <p><b>禅道的一个坑</b>：白名单留空时用的是全局 {@code config->ipWhiteList}，
     * 而它默认是 {@code '*'} —— 所以「IP 栏留空」等于「不限制」，不是「谁都不许」。
     * 本实现保持一致。
     *
     * <p><b>比禅道严一点</b>（但只在不命中 {@code '*'} 时生效）：禅道用 {@code ip2long} 失败会退化成 0，
     * 于是「非法 IP + a-b 白名单」有可能算成 0 落在区间内而放行。本实现对非法 IPv4 直接拒绝。
     * 注意 {@code '*'} 是在最前面短路的（与禅道一致），所以配了「无限制」的应用不会去校验来源 IP 的合法性。
     */
    public static boolean checkIP(String ipWhiteList, String ip) {
        if (StrUtil.isBlank(ipWhiteList)) {
            ipWhiteList = DEFAULT_IP_WHITE_LIST;
        }
        if (DEFAULT_IP_WHITE_LIST.equals(ipWhiteList)) {
            return true;
        }
        String candidate = StrUtil.trimToEmpty(ip);
        if (StrUtil.isBlank(candidate) || ip2long(candidate) == 0L && !"0.0.0.0".equals(candidate)) {
            return false; // 非法来源 IP：直接拒绝（禅道会算成 0，有放行风险）
        }
        if (candidate.equals(ipWhiteList)) {
            return true;
        }
        if (ipWhiteList.contains(",")) {
            for (String rule : ipWhiteList.split(",")) {
                if (checkIP(rule, candidate)) {
                    return true;
                }
            }
            return false;
        }
        if (ipWhiteList.contains("-")) {
            String[] parts = ipWhiteList.split("-", 2);
            long min = ip2long(parts[0]);
            long max = ip2long(parts[1]);
            long value = ip2long(candidate);
            return value >= min && value <= max;
        }
        if (ipWhiteList.contains("*")) {
            int dots = StrUtil.count(ipWhiteList, ".");
            String min;
            String max;
            if (dots == 3) {
                min = ipWhiteList.replace("*", "0");
                max = ipWhiteList.replace("*", "255");
            } else if (dots == 2) {
                min = ipWhiteList.replace("*", "0.0");
                max = ipWhiteList.replace("*", "255.255");
            } else if (dots == 1) {
                min = ipWhiteList.replace("*", "0.0.0");
                max = ipWhiteList.replace("*", "255.255.255");
            } else {
                return false; // 禅道此处 $min/$max 未定义（等价于 0-0），没有任何合法 IP 命中
            }
            long value = ip2long(candidate);
            return value >= ip2long(min) && value <= ip2long(max);
        }
        /* CIDR，禅道注释里写了「Thanks to zcat」 */
        String cidr = ipWhiteList;
        int netmask = 32;
        if (cidr.contains("/")) {
            String[] parts = cidr.split("/", 2);
            cidr = parts[0];
            try {
                netmask = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (netmask < 0 || netmask > 32) {
            return false;
        }
        long wildcard = (1L << (32 - netmask)) - 1;
        long maskBits = ~wildcard & 0xFFFFFFFFL;
        return (ip2long(candidate) & maskBits) == (ip2long(cidr) & maskBits);
    }

    /** PHP {@code ip2long} 的等价物（32 位无符号；非法输入返回 0） */
    public static long ip2long(String ip) {
        if (ip == null) {
            return 0L;
        }
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) {
            return 0L;
        }
        long result = 0L;
        for (String part : parts) {
            long value;
            try {
                value = Long.parseLong(part.trim());
            } catch (NumberFormatException e) {
                return 0L;
            }
            if (value < 0 || value > 255) {
                return 0L;
            }
            result = (result << 8) | value;
        }
        return result;
    }

    private static String md5(String text) {
        return DigestUtils.md5DigestAsHex(text.getBytes(StandardCharsets.UTF_8));
    }

}
