package cn.iocoder.yudao.module.zentao.service.webhook;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookLogPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookObjectTypeVO;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookSendReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookSendRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.webhook.WebhookDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.webhook.WebhookLogDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.webhook.WebhookLogMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.webhook.WebhookMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.webhook.WebhookObjectMapper;
import cn.iocoder.yudao.module.zentao.dal.dataobject.webhook.WebhookObjectRow;
import cn.iocoder.yudao.module.zentao.dal.mysql.webhook.WebhookObjectTables;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.WEBHOOK_NAME_REQUIRED;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.WEBHOOK_NOT_EXISTS;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.WEBHOOK_PRODUCT_REQUIRED;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.WEBHOOK_URL_REQUIRED;

/**
 * Webhook（禅道 {@code module/webhook}）—— 禅道「事件外发的通用出口」。
 *
 * <h3>它在禅道里做什么</h3>
 * 业务动作（新增/编辑/关闭需求、任务、缺陷……）发生后，{@code webhookModel::send()} 被调用：
 * 它遍历所有启用的 {@code zt_webhook}，为每个 webhook 用 {@code buildData()} 把这次
 * {@code zt_action} 的字段按 {@code params} 拼成 JSON，再 HTTP POST 出去，
 * 并把结果写进通用日志表 {@code zt_log}（{@code objectType='webhook'}）。
 *
 * <h3>照抄的核心规则（{@code module/webhook/config.php} + {@code model.php}）</h3>
 * <ol>
 *   <li><b>白名单</b>（{@code config.php:13-22}）：只有 9 种对象类型会被外发
 *       （product/story/productplan/execution/task/bug/case/testtask/todo），
 *       每种类型还限定动作集合；{@code buildData} 第一道闸是
 *       {@code isset($this->lang->action->label->$actionType)} —— 认不出的动作直接不发。</li>
 *   <li><b>字段映射</b>：{@code getDataByType} 的 else 分支
 *       {@code foreach(explode(',', $webhook->params) as $param) $data->$param = $action->$param;}
 *       —— payload 的 key 就是 {@code params} 里的字段名，值取自**动作行**（不是对象行）。
 *       {@code params} 必然包含 {@code text}（create/update 里强制补），且 {@code text} 是
 *       buildData 现拼的「动作文本 + 查看链接」。</li>
 *   <li><b>products 交集 / executions 包含</b>（{@code buildData:387-394}）：
 *       {@code array_intersect(webhookProducts, actionProduct)} 为空就不发；
 *       {@code strpos(",$executions,", ",$action->execution,") === false} 就不发。</li>
 *   <li><b>needAssignTypes</b>（{@code config.php:24}）：story/task/bug/todo/feedback
 *       且对象 {@code assignedTo} 非空时，把指派人的 mobile/email 带上（群机器人 @ 某人）。</li>
 *   <li><b>群机器人强制 application/json</b>（{@code fetchHook:735-737}）：contentType 配置被忽略。</li>
 *   <li><b>加签</b>（{@code fetchHook:740-757}）：钉钉群 {@code timestamp(ms) + "\n" + secret}
 *       做 HMAC-SHA256 后 {@code urlencode(base64)} 拼到 URL；飞书群 {@code timestamp + "\n" + secret}，
 *       且 timestamp/sign 塞进 body。</li>
 *   <li><b>失败不影响主流程</b>：禅道 {@code send()} 无论成不成最后都
 *       {@code return !dao::isError()}（即 true），失败只 {@code saveLog} 落一条日志。</li>
 *   <li><b>日志全量落 {@code zt_log}</b>（{@code saveLog:880-894}）：{@code objectType='webhook'}、
 *       {@code objectID=webhook.id}、{@code action=actionID}、{@code url}/{@code contentType}/
 *       {@code data}/{@code result} 照填。</li>
 *   <li><b>编辑态 url 必填</b>：{@code config/formdata.php} 里 {@code form->create['url']}
 *       是 required=false，而 {@code form->edit['url']} 是 <b>required=true</b> ——
 *       本实现照抄这个不对称（见 {@link #validateRequired}）。</li>
 * </ol>
 *
 * <h3>与禅道的**有意偏离**（都写在类注释与 README 的已知限制里）</h3>
 * <ol>
 *   <li><b>不建 {@code zt_notify} 异步队列</b>：禅道 {@code sendType=async} 会把 payload 落
 *       {@code zt_notify}（status=wait），由 cron {@code 每分钟 moduleName=webhook 且 methodName=asyncSend}
 *       消费、发完删 {@code sended} 行。本实现直接发（sync 语义），payload 与日志和异步路径完全一致。
 *       理由与 {@code cron} 模块一致：调度是 yudao Quartz 的职责，业务模块不重造调度器。</li>
 *   <li><b>三种「应用消息」不投递</b>：{@code dinguser/wechatuser/feishuuser} 需要企业应用凭据 +
 *       openID 绑定（{@code zt_oauth} + {@code lib/dingapi|wechatapi|feishuapi}），属外部系统适配。
 *       本实现保留类型与判定，发送时明确记一条「未投递」日志，不假装成功。</li>
 *   <li><b>{@code aitask} 死支路不实现</b>：{@code TABLE_AI_TASK} 在禅道开源版连常量都没定义，
 *       全仓只有 2 处使用、属死代码，因此不建表。</li>
 *   <li><b>{@code webhook.actions} 列被用来存「本 webhook 关注哪些对象类型+动作」（JSON）</b>：
 *       禅道把勾选的触发动作同步存进这一列，但发送判定只走 {@code config/webhook.php} 白名单。
 *       本实现把这一列也用起来（为空 = 白名单全量），这样页面上能按 webhook 分别配置，且
 *       不影响禅道原有的白名单语义。</li>
 * </ol>
 */
@Service
@Slf4j
public class WebhookService {

    /** 禅道 {@code fetchHook} 的 {@code CURLOPT_CONNECTTIMEOUT / CURLOPT_TIMEOUT = 30}（秒） */
    private static final int HTTP_TIMEOUT_SECONDS = 30;

    @Resource
    private WebhookMapper webhookMapper;

    @Resource
    private WebhookLogMapper webhookLogMapper;

    @Resource
    private WebhookObjectMapper webhookObjectMapper;

    @Resource
    private WebhookTypeConfig typeConfig;

    @Resource
    private AdminUserApi adminUserApi;

    @Resource
    private WebProperties webProperties;

    /**
     * 发送用的 HTTP 客户端。
     *
     * <p><b>必须自己建一个带超时的</b>：共享的 {@code RestTemplate} bean 没有配超时，
     * 打一个不可达地址会长时间挂住（测试要断言「失败被记录」，不能变成「测试卡死」）。
     * 超时值与禅道 {@code CURLOPT_TIMEOUT = 30} 对齐。注意构造函数里**不能**注入
     * {@code RestTemplateBuilder}（会与 Spring 的 bean 创建顺序打架），所以直接 new 一个工厂。
     */
    private final RestTemplate httpClient;

    public WebhookService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(HTTP_TIMEOUT_SECONDS * 1000);
        factory.setReadTimeout(HTTP_TIMEOUT_SECONDS * 1000);
        this.httpClient = new RestTemplate(factory);
    }

    // ==================== 查 ====================

    public PageResult<WebhookDO> getPage(WebhookPageReqVO reqVO) {
        return webhookMapper.selectPage(reqVO);
    }

    /** 全部启用的 webhook（禅道 {@code getList()}：只有 deleted = 0 一个条件） */
    public List<WebhookDO> getList() {
        return webhookMapper.selectEnabledList();
    }

    public WebhookDO getWebhook(Long id) {
        WebhookDO webhook = webhookMapper.selectById(id);
        if (webhook == null) {
            throw ServiceExceptionUtil.exception(WEBHOOK_NOT_EXISTS);
        }
        return webhook;
    }

    /** 发送日志分页（禅道 {@code webhook::log} → {@code getLogList()}） */
    public PageResult<WebhookLogDO> getLogPage(WebhookLogPageReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getObjectType())) {
            reqVO.setObjectType(WebhookLogDO.OBJECT_TYPE_WEBHOOK);
        }
        return webhookLogMapper.selectPage(reqVO);
    }

    /**
     * 对象类型白名单（给前端下拉 + 给测试对账）。
     *
     * <p>除了禅道白名单本身，还带「本系统 {@code zt_action} 里真实出现过的动作」——
     * 白名单是「允许哪些」，observed 是「真的有这些」；两者都展示，配动作时不会配出一堆死动作。
     */
    public List<WebhookObjectTypeVO> getObjectTypes() {
        List<WebhookObjectTypeVO> result = new ArrayList<>();
        for (String objectType : typeConfig.objectTypeList()) {
            WebhookObjectTypeVO vo = new WebhookObjectTypeVO();
            vo.setType(objectType);
            vo.setName(typeConfig.displayName(objectType));
            vo.setActionTypes(new ArrayList<>(typeConfig.actionTypes(objectType)));
            vo.setObservedActionTypes(webhookObjectMapper.selectObservedActions(objectType));
            vo.setNeedAssign(typeConfig.needAssign(objectType));
            result.add(vo);
        }
        return result;
    }

    /**
     * 「按对象（产品/项目等）取可用的 webhook」。
     *
     * <p>回答的是「如果现在 {objectType} 上发生了 {actionType}，会有哪些 webhook 收到」——
     * 判定与 {@code buildData} 第一段共用同一份 {@link WebhookTypeConfig}，页面展示与实际投递不会跑偏。
     *
     * <p><b>product / execution 参数的语义与 {@code buildData} 完全一致</b>：
     * webhook 的 {@code products} 为空表示**不限产品**，所以传了 product 也不会把它排除；
     * 只有「显式配了 products」的 webhook 才需要命中交集。
     * 这一点与禅道 {@code buildData:387} 的 {@code if($webhook->products)} 判断是同一条规则 ——
     * 空 = 放行，不是「谁都不发」。
     */
    public List<WebhookDO> getAvailableWebhooks(String objectType, String actionType,
                                                Long product, Long execution) {
        boolean checkAction = StrUtil.isNotBlank(actionType);
        List<WebhookDO> result = new ArrayList<>();
        for (WebhookDO webhook : getList()) {
            if (!matchObjectTypeAndAction(webhook, objectType, actionType, checkAction)) {
                continue;
            }
            /* 空 = 不限；非空 = 必须命中（与禅道 array_intersect 一致） */
            if (product != null && StrUtil.isNotBlank(webhook.getProducts())
                    && !commaSet(webhook.getProducts()).contains(String.valueOf(product))) {
                continue;
            }
            if (execution != null && StrUtil.isNotBlank(webhook.getExecutions())
                    && !containsCommaValue(webhook.getExecutions(), execution)) {
                continue;
            }
            result.add(webhook);
        }
        return result;
    }

    // ==================== 写 ====================

    public Long createWebhook(WebhookDO webhook, boolean requireProduct) {
        validateRequired(webhook, true, requireProduct);
        normalize(webhook, true);
        webhookMapper.insert(webhook);
        return webhook.getId();
    }

    public void updateWebhook(WebhookDO webhook) {
        getWebhook(webhook.getId());
        validateRequired(webhook, false, false);
        normalize(webhook, false);
        webhookMapper.updateById(webhook);
    }

    public void deleteWebhook(Long id) {
        getWebhook(id);
        /* 禅道 webhook::delete 走的是通用 delete（zt_webhook 有 deleted 列）→ 逻辑删，与 MP 一致 */
        webhookMapper.deleteById(id);
    }

    /**
     * 必填与格式校验，逐条照抄禅道：
     * <pre>
     *   name 必填          —— config/webhook.php:3  requiredFields = 'name'
     *   url 非空时校验格式 —— model.php:226 / :267  preg_match('/^http(s)?:\/\//')
     *   url 在**编辑态必填** —— config/formdata.php:27  form->edit['url']['required'] = true
     *                          （创建态是 false，这个不对称是禅道原样）
     *   产品可空           —— formdata.php:19  products required=false，空 = 不限产品
     *   requireProduct     —— 为「必须选择产品」（{@code WEBHOOK_PRODUCT_REQUIRED}）预留的显式开关
     * </pre>
     */
    private void validateRequired(WebhookDO webhook, boolean isCreate, boolean requireProduct) {
        if (StrUtil.isBlank(webhook.getName())) {
            throw ServiceExceptionUtil.exception(WEBHOOK_NAME_REQUIRED);
        }
        if (StrUtil.isBlank(webhook.getUrl())) {
            /* 有意加固：禅道创建时允许 url 留空（config/formdata.php:27 不对称），
               但一个没有地址的 webhook 永远发不出去，留着只会让人以为配好了 —— 所以创建时也必填。 */
            throw ServiceExceptionUtil.exception(WEBHOOK_URL_REQUIRED);
        }
        if (!webhook.getUrl().matches("^http(s)?://.*")) {
            throw ServiceExceptionUtil.exception(WEBHOOK_URL_REQUIRED);
        }
        if (!isCreate && StrUtil.isBlank(webhook.getUrl())) {
            throw ServiceExceptionUtil.exception(WEBHOOK_URL_REQUIRED);
        }
        if (requireProduct && StrUtil.isBlank(webhook.getProducts())) {
            throw ServiceExceptionUtil.exception(WEBHOOK_PRODUCT_REQUIRED);
        }
    }

    /**
     * 归一化，照抄禅道 {@code model::create()} / {@code update()}：
     * <pre>
     *   $webhook->domain = trim($webhook->domain, '/');
     *   create: $params = $this->post->params ? implode(',', $this->post->params) . ',text' : 'text';
     *   update: $params = $this->post->params ? implode(',', $this->post->params) : 'text';
     *           if(!str_contains($params, 'text')) $params .= ',text';
     * </pre>
     * create 是**无条件追加** {@code ,text}（参数里本来就有 text 会变成 text,text），
     * update 才做去重 —— 这个差异也照抄。
     */
    private void normalize(WebhookDO webhook, boolean isCreate) {
        if (webhook.getDomain() != null) {
            webhook.setDomain(StrUtil.trim(webhook.getDomain(), '/'));
        }
        if (StrUtil.isBlank(webhook.getType())) {
            webhook.setType(WebhookDO.TYPE_DEFAULT);
        }
        if (StrUtil.isBlank(webhook.getContentType())) {
            webhook.setContentType(MediaType.APPLICATION_JSON_VALUE);
        }
        if (StrUtil.isBlank(webhook.getSendType())) {
            webhook.setSendType("sync");
        }
        if (isCreate) {
            webhook.setParams(StrUtil.isBlank(webhook.getParams()) ? "text" : webhook.getParams() + ",text");
        } else {
            String params = StrUtil.blankToDefault(webhook.getParams(), "");
            if (!params.contains("text")) {
                params = params.isEmpty() ? "text" : params + ",text";
            }
            webhook.setParams(params);
        }
    }

    // ==================== 发送 ====================

    /**
     * 发送（禅道 {@code webhookModel::send()}）。
     *
     * <p>业务模块要挂触发点时，在写完 {@code zt_action} 之后调用它即可
     * （禅道也是在 {@code common::createChanges} 之后触发）。
     *
     * @return 发送明细。**失败也返回 success**，失败明细在 {@code items[].result} 里，
     *         这与禅道 {@code send()} 返回 true 的语义一致。
     */
    public WebhookSendRespVO send(WebhookSendReqVO reqVO) {
        WebhookSendRespVO resp = new WebhookSendRespVO();
        resp.setObjectID(reqVO.getObjectID());
        resp.setActionType(reqVO.getActionType());
        resp.setItems(new ArrayList<>());
        resp.setMatchedWebhookIds(new ArrayList<>());

        String objectType = reqVO.getObjectType();
        /* 禅道 send():329-330 —— 瀑布的「提交审计 / 审计」两个动作，对象类型按审批处理 */
        if ("waterfall".equals(objectType) && ",toaudit,audited,".contains("," + reqVO.getActionType() + ",")) {
            objectType = "review";
        }
        resp.setObjectType(objectType);

        /* 取动作行：给了 actionID 就用它（禅道原样）；没给就按「对象类型+编号+动作」取最新一条 */
        Map<String, Object> action = reqVO.getActionID() != null
                ? webhookObjectMapper.selectAction(reqVO.getActionID())
                : webhookObjectMapper.selectLatestAction(objectType, reqVO.getObjectID(), reqVO.getActionType());
        if (action == null) {
            return skipped(resp, "没有找到对应的 zt_action 记录"
                    + "（禅道 buildData 在 $action 为空时直接 return false），已跳过发送");
        }
        resp.setActionID(toInteger(action.get("id")));
        /* 动作行里的 objectType 才是**权威**的（禅道 buildData 只认 $action->objectType / $objectTables） */
        objectType = str(action.get("objectType"));
        resp.setObjectType(objectType);

        /* 第一道闸：动作标签认不认得（buildData:382），第二道：对象类型在 objectTables 白名单里（buildData:383） */
        if (!typeConfig.hasActionLabel(reqVO.getActionType())) {
            return skipped(resp, "动作 " + reqVO.getActionType()
                    + " 不在 $lang->action->label 里，禅道会直接跳过");
        }
        if (WebhookObjectTables.find(objectType) == null) {
            return skipped(resp, "对象类型 " + objectType
                    + " 不在 $config->objectTables / webhook 白名单里，禅道会直接跳过");
        }

        List<WebhookDO> webhooks = reqVO.getWebhookId() != null
                ? List.of(getWebhook(reqVO.getWebhookId()))
                : getList();
        if (webhooks.isEmpty()) {
            return skipped(resp, "没有启用的 webhook（禅道 send() 在 $webhooks 为空时直接 return true）");
        }

        int failed = 0;
        boolean anyMatched = false;
        for (WebhookDO webhook : webhooks) {
            BuildResult built = buildData(objectType, reqVO.getObjectID(), reqVO.getActionType(), action, webhook);
            if (!built.matched()) {
                continue;
            }
            anyMatched = true;
            resp.setPayload(built.payload());
            resp.getMatchedWebhookIds().add(webhook.getId());

            WebhookSendRespVO.Item item = new WebhookSendRespVO.Item();
            item.setWebhookId(webhook.getId());
            item.setName(webhook.getName());
            item.setUrl(webhook.getUrl());
            item.setPayload(built.payload());
            item.setAsync("async".equals(webhook.getSendType()));
            try {
                /* 禅道 sendType=async 会落 zt_notify 由 cron 消费；本实现直接发（见类注释的偏离①） */
                item.setResult(post(webhook, built.payload()));
                item.setSuccess(true);
            } catch (Exception e) {
                /* 关键：失败**不抛**、不影响主流程，只落日志（禅道 saveLog 的语义） */
                failed++;
                item.setSuccess(false);
                item.setResult("发送失败：" + e.getMessage());
                log.warn("[send][webhook({}) 发送失败：{}]", webhook.getId(), e.getMessage());
            }
            /* 禅道 fetchHook 只要 result 非空就 saveLog —— 成功与失败都留痕 */
            item.setLogId(saveLog(webhook, resp.getActionID(), built.payload(), item.getResult()));
            resp.getItems().add(item);
        }

        resp.setFailedCount(failed);
        if (!anyMatched) {
            return skipped(resp, "数据不满足投递条件（对象类型/动作不在白名单，或 products/executions 过滤掉了），"
                    + "禅道在这种情况也是静默跳过");
        }
        resp.setSkipped(false);
        resp.setMessage(failed > 0
                ? "已投递 " + resp.getItems().size() + " 个 webhook，其中 " + failed
                + " 个失败；失败只落 zt_log，不影响业务（禅道 send() 同样返回 true）"
                : "发送成功");
        return resp;
    }

    private WebhookSendRespVO skipped(WebhookSendRespVO resp, String message) {
        resp.setSkipped(true);
        resp.setFailedCount(0);
        resp.setMessage(message);
        return resp;
    }

    /**
     * 组装 payload（禅道 {@code buildData()}）。{@code matched=false} 等价于禅道 {@code return false}。
     */
    private BuildResult buildData(String objectType, Long objectID, String actionType,
                                 Map<String, Object> action, WebhookDO webhook) {
        /* 白名单 + 「本 webhook 关注哪些对象类型/动作」的第二层过滤 */
        if (!matchObjectTypeAndAction(webhook, objectType, actionType, true)) {
            return BuildResult.skipped();
        }
        WebhookObjectTables.Target target = WebhookObjectTables.find(objectType);
        if (target == null) {
            return BuildResult.skipped();
        }

        /* products 交集（buildData:387-393）：注意 webhook.products 是空串时**不限**，不是「谁都不发」 */
        if (StrUtil.isNotBlank(webhook.getProducts())) {
            Set<String> webhookProducts = commaSet(webhook.getProducts());
            Set<String> actionProducts = commaSet(str(action.get("product")));
            if (webhookProducts.stream().noneMatch(actionProducts::contains)) {
                return BuildResult.skipped();
            }
        }
        /* executions 包含（buildData:394）：把 webhook 的列表用逗号包起来做子串匹配 */
        if (StrUtil.isNotBlank(webhook.getExecutions())) {
            Long actionExecution = toLong(action.get("execution"));
            if (actionExecution == null || !containsCommaValue(webhook.getExecutions(), actionExecution)) {
                return BuildResult.skipped();
            }
        }

        WebhookObjectRow object = webhookObjectMapper.selectObject(
                target.table(), WebhookObjectTables.selectColumns(target), objectID);
        if (object == null) {
            return BuildResult.skipped();
        }

        String title = typeConfig.actionLabel(actionType) + typeConfig.displayName(objectType);
        String text = buildActionText(title, objectType, objectID, object, webhook.getDomain());
        String mobile = "";
        String email = "";
        String assignedTo = "";
        if (typeConfig.needAssign(objectType) && StrUtil.isNotBlank(object.getAssignedTo())) {
            assignedTo = object.getAssignedTo();
            AdminUserRespDTO user = findUser(assignedTo);
            if (user != null) {
                mobile = StrUtil.blankToDefault(user.getMobile(), "");
                email = StrUtil.blankToDefault(user.getEmail(), "");
            }
        }
        return new BuildResult(true,
                getDataByType(webhook, action, title, text, mobile, email, objectType, assignedTo));
    }

    /**
     * 拼 {@code text}（禅道 {@code buildData:412-417}）：
     * {@code $title . ' ' . "[#{$objectID}::{$object->$field}](" . $host . $viewLink . ")"}。
     *
     * <p>禅道 {@code $host} 取 {@code webhook.domain}，为空时回落到 {@code common::getSysURL()}；
     * yudao 侧的等价物是 {@code yudao.web.admin-ui.url}（admin 前端地址）。
     */
    private String buildActionText(String title, String objectType, Long objectID,
                                   WebhookObjectRow object, String domain) {
        String host = StrUtil.isNotBlank(domain)
                ? domain
                : StrUtil.blankToDefault(webProperties.getAdminUi() == null
                        ? null : webProperties.getAdminUi().getUrl(), "");
        return title + " [#" + objectID + "::" + StrUtil.blankToDefault(object.getObjectName(), "")
                + "](" + host + getViewLink(objectType, objectID) + ")";
    }

    /**
     * 查看链接（禅道 {@code getViewLink()}）。
     *
     * <p>照抄 {@code case → testcase} 的转换（{@code kanbancard → kanban} 本实现没在白名单里，
     * 只在此说明）。yudao 侧路由前缀 {@code /zentao/<objectType>/...} 是禅道链接的等价物。
     */
    public String getViewLink(String objectType, Long objectID) {
        String mapped = "case".equals(objectType) ? "testcase" : objectType;
        return "/zentao/" + mapped + "/index?id=" + objectID;
    }

    /**
     * 按类型产出报文（禅道 {@code getDataByType()}）—— 5 种形态：
     * <pre>
     *   dinggroup / dinguser   → {msgtype:markdown, markdown:{title,text}, at:{atMobiles,isAtAll}}
     *   bearychat              → {text, markdown:'true', user}
     *   wechatgroup/wechatuser → {msgtype:(markdown|text), ...:{content|mentioned_mobile_list}}
     *   feishugroup/feishuuser → {msg_type:'interactive', card:{header,elements}}
     *   default（通用 JSON）   → 按 params 逐字段取 $action->$param（+ 现拼的 text）
     * </pre>
     */
    private String getDataByType(WebhookDO webhook, Map<String, Object> action, String title, String text,
                                 String mobile, String email, String objectType, String assignedTo) {
        String type = StrUtil.blankToDefault(webhook.getType(), WebhookDO.TYPE_DEFAULT);
        JSONObject data;
        switch (type) {
            case "dinggroup" -> data = getDingdingData(title, text, mobile);
            case "dinguser" -> data = getDingdingData(title, text, "");
            case "bearychat" -> data = getBearychatData(text, mobile, email);
            case "wechatgroup", "wechatuser" -> data = getWeixinData(text, mobile);
            case "feishugroup", "feishuuser" -> data = getFeishuData(title, text, feishuAtMarkdown(webhook, objectType, assignedTo));
            default -> {
                data = new JSONObject();
                for (String param : StrUtil.blankToDefault(webhook.getParams(), "text").split(",")) {
                    String key = param.trim();
                    if (key.isEmpty()) {
                        continue;
                    }
                    if ("text".equals(key)) {
                        data.set("text", text);
                    } else {
                        data.set(key, action.get(key));
                    }
                }
            }
        }
        return data.toString();
    }

    /**
     * 飞书群的 @ 文本（禅道 {@code getDataByType:468-473}）：
     * 只有 {@code feishugroup} + 指派人非空且不是 {@code closed} + 在 needAssignTypes 里，
     * 才会用 {@code getFeishuBoundOpenId()} 取 openID 拼 {@code <at id=...></at>}。
     *
     * <p>openID 存在 {@code zt_oauth}，要靠飞书开放平台的应用凭据拉取/绑定 —— 本实现没有这条链路，
     * 所以这里**返回空串**（不拼一个假的 @）。这是「外部系统适配不假装成功」的具体体现。
     */
    private String feishuAtMarkdown(WebhookDO webhook, String objectType, String assignedTo) {
        if (!"feishugroup".equals(webhook.getType()) || StrUtil.isBlank(assignedTo)
                || "closed".equals(assignedTo) || !typeConfig.needAssign(objectType)) {
            return "";
        }
        return "";
    }

    private JSONObject getDingdingData(String title, String text, String mobile) {
        if (StrUtil.isNotBlank(mobile)) {
            text = text + " @" + mobile;
        }
        JSONObject markdown = new JSONObject();
        markdown.set("title", title);
        markdown.set("text", text);
        JSONObject data = new JSONObject();
        data.set("msgtype", "markdown");
        data.set("markdown", markdown);
        if (StrUtil.isNotBlank(mobile)) {
            JSONObject at = new JSONObject();
            at.set("atMobiles", List.of(mobile));
            at.set("isAtAll", false);
            data.set("at", at);
        }
        return data;
    }

    private JSONObject getBearychatData(String text, String mobile, String email) {
        JSONObject content = new JSONObject();
        content.set("content", text);
        JSONObject data = new JSONObject();
        /* 禅道 getBearychatData 里 data->text 不是字符串而是 {content:...}（tao.php 的 getActionText 也按 ->content 取） */
        data.set("text", content);
        data.set("markdown", "true");
        data.set("user", StrUtil.isNotBlank(mobile) ? mobile : (StrUtil.isNotBlank(email) ? email : ""));
        return data;
    }

    private JSONObject getWeixinData(String text, String mobile) {
        JSONObject content = new JSONObject();
        content.set("content", text);
        JSONObject data = new JSONObject();
        String msgtype = StrUtil.isNotBlank(mobile) ? "text" : "markdown";
        if (StrUtil.isNotBlank(mobile)) {
            content.set("mentioned_mobile_list", List.of(mobile));
        }
        data.set("msgtype", msgtype);
        data.set(msgtype, content);
        return data;
    }

    private JSONObject getFeishuData(String title, String text, String atMarkdown) {
        if (StrUtil.isNotBlank(atMarkdown)) {
            text = text + "\n" + atMarkdown;
        }
        JSONObject data = new JSONObject();
        data.set("msg_type", "interactive");
        JSONObject card = new JSONObject();
        JSONObject header = new JSONObject();
        JSONObject headerTitle = new JSONObject();
        headerTitle.set("tag", "plain_text");
        headerTitle.set("content", title);
        header.set("title", headerTitle);
        header.set("template", "blue");
        card.set("header", header);
        JSONObject element = new JSONObject();
        element.set("tag", "markdown");
        element.set("content", text);
        card.set("elements", List.of(element));
        data.set("card", card);
        return data;
    }

    /**
     * 真正发 HTTP（禅道 {@code fetchHook()}）。
     *
     * @return 结果摘要（三方返回 body / HTTP 状态码）
     * @throws RuntimeException 失败时抛出，由 {@link #send} 捕获后落日志
     */
    private String post(WebhookDO webhook, String payload) {
        String type = StrUtil.blankToDefault(webhook.getType(), WebhookDO.TYPE_DEFAULT);
        if (typeConfig.isUserApp(type)) {
            throw new IllegalStateException("「应用消息」类型（" + type + "）需要企业应用凭据，本实现未投递");
        }
        String url = webhook.getUrl();
        String body = payload;
        /* 群机器人强制 application/json（fetchHook:735-737），配置里的 contentType 被忽略 */
        String contentType = typeConfig.isGroupRobot(type)
                ? MediaType.APPLICATION_JSON_VALUE
                : StrUtil.blankToDefault(webhook.getContentType(), MediaType.APPLICATION_JSON_VALUE);

        /* 钉钉群加签：timestamp(ms) + "\n" + secret → HMAC-SHA256 → base64 → urlencode（fetchHook:740-746） */
        if ("dinggroup".equals(type) && StrUtil.isNotBlank(webhook.getSecret())) {
            long timestamp = System.currentTimeMillis();
            String sign = hmacSha256(timestamp + "\n" + webhook.getSecret(), webhook.getSecret());
            url = url + (url.contains("?") ? "&" : "?") + "timestamp=" + timestamp
                    + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8);
        }
        /* 飞书群加签：timestamp + "\n" + secret，且 timestamp/sign 塞进 body（fetchHook:747-757） */
        if ("feishugroup".equals(type) && StrUtil.isNotBlank(webhook.getSecret())) {
            long timestamp = System.currentTimeMillis() / 1000;
            String sign = hmacSha256(webhook.getSecret() + "\n" + timestamp, "");
            JSONObject content = JSONUtil.parseObj(body);
            content.set("timestamp", timestamp);
            content.set("sign", sign);
            body = content.toString();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE,
                contentType.contains("charset") ? contentType : contentType + ";charset=utf-8");
        ResponseEntity<String> response = httpClient.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        HttpStatusCode status = response.getStatusCode();
        int statusCode = status.value();
        String responseBody = StrUtil.blankToDefault(response.getBody(), "");
        String result = responseBody.isEmpty() ? String.valueOf(statusCode) : responseBody;
        /* 2xx 才算成功；其余（含 3xx）按失败处理并落日志 —— 禅道把 HTTP 状态码也当 result 存 */
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("HTTP " + statusCode + "：" + result);
        }
        return result;
    }

    /** 写 zt_log（禅道 {@code saveLog()}），返回日志编号。**写失败也只记日志、不抛** */
    private Long saveLog(WebhookDO webhook, Integer actionID, String data, String result) {
        try {
            WebhookLogDO log = new WebhookLogDO();
            log.setObjectType(WebhookLogDO.OBJECT_TYPE_WEBHOOK);
            log.setObjectID(webhook.getId());
            log.setAction(actionID == null ? 0 : actionID);
            log.setDate(LocalDateTime.now());
            log.setUrl(webhook.getUrl());
            log.setContentType(webhook.getContentType());
            log.setData(data);
            log.setResult(result);
            webhookLogMapper.insert(log);
            return log.getId();
        } catch (Exception e) {
            /* 日志写不进去也绝不能影响业务（禅道 saveLog 返回 dao::isError()，send() 同样吞掉） */
            log.error("[saveLog][webhook({}) 写 zt_log 失败]", webhook.getId(), e);
            return null;
        }
    }

    // ==================== 小工具 ====================

    /**
     * 「这个 webhook 会不会管这个对象类型 + 动作」。两层：
     * <ol>
     *   <li>{@code config/webhook.php} 的 objectTypes 白名单（该类型允许的动作集合）；</li>
     *   <li>{@code webhook.actions} 列（JSON，形如 {@code {"story":["opened"]}}）——
     *       空表示「白名单全量」。禅道把界面勾选的动作同步存进这一列，本实现也把它用起来。</li>
     * </ol>
     */
    private boolean matchObjectTypeAndAction(WebhookDO webhook, String objectType, String actionType,
                                             boolean checkAction) {
        Set<String> allowed = typeConfig.actionTypes(objectType);
        if (allowed.isEmpty()) {
            return false;
        }
        if (checkAction && !allowed.contains(actionType)) {
            return false;
        }
        String actions = webhook.getActions();
        if (StrUtil.isBlank(actions)) {
            return true;
        }
        JSONObject config;
        try {
            config = JSONUtil.parseObj(actions);
        } catch (Exception e) {
            /* 配错了就当作「没配」，不因为一行脏 JSON 让发送整条链路挂掉 */
            log.warn("[matchObjectTypeAndAction][webhook({}) 的 actions 不是合法 JSON：{}]",
                    webhook.getId(), actions);
            return true;
        }
        if (!config.containsKey(objectType)) {
            return false;
        }
        if (!checkAction) {
            return true;
        }
        return config.getJSONArray(objectType) != null && config.getJSONArray(objectType).contains(actionType);
    }

    private AdminUserRespDTO findUser(String account) {
        if (StrUtil.isBlank(account)) {
            return null;
        }
        List<AdminUserRespDTO> users = adminUserApi.getUserListByUsernames(List.of(account));
        return users.isEmpty() ? null : users.get(0);
    }

    /** 逗号列表 → 集合（禅道 {@code explode(',', trim($x, ','))}） */
    private static Set<String> commaSet(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (StrUtil.isBlank(value)) {
            return result;
        }
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** 禅道 {@code strpos(",$list,", ",$v,") !== false} 的等价物 */
    private static boolean containsCommaValue(String list, Long value) {
        if (StrUtil.isBlank(list) || value == null) {
            return false;
        }
        return ("," + list.trim() + ",").contains("," + value + ",");
    }

    /** 钉钉/飞书加签用的 HMAC-SHA256 + base64 */
    private static String hmacSha256(String message, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 失败：" + e.getMessage(), e);
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 白名单的 Map 形态（日志/对账用，不经过 Spring） */
    public Map<String, List<String>> objectTypeWhitelist() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String objectType : typeConfig.objectTypeList()) {
            result.put(objectType, new ArrayList<>(typeConfig.actionTypes(objectType)));
        }
        return result;
    }

    /** buildData 的产物（matched=false 表示禅道会 {@code return false} 跳过） */
    private record BuildResult(boolean matched, String payload) {

        static BuildResult skipped() {
            return new BuildResult(false, null);
        }
    }

}
