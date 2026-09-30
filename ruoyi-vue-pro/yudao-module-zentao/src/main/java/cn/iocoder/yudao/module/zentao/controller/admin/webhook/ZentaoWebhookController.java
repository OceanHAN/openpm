package cn.iocoder.yudao.module.zentao.controller.admin.webhook;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookLogPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookLogRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookObjectTypeVO;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookSendReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookSendRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.webhook.WebhookDO;
import cn.iocoder.yudao.module.zentao.service.webhook.WebhookService;
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
 * Webhook（禅道 {@code module/webhook}）。
 *
 * <p>禅道有 9 个业务 action，本实现的对应关系：
 * <pre>
 *   browse                  → GET    /zentao/webhook/page
 *   create                  → POST   /zentao/webhook/create
 *   edit                    → PUT    /zentao/webhook/update
 *   delete                  → DELETE /zentao/webhook/delete
 *   log                     → GET    /zentao/webhook/log-page（读通用日志表 zt_log）
 *   asyncSend（cron 消费）  → POST   /zentao/webhook/send（禅道 send() 的同步语义入口）
 *   bind / chooseDept /
 *   ajaxGetFeishuDeptList   → 需要钉钉/企微/飞书的企业应用凭据，本实现未迁移
 *                             （见 WebhookService 类注释的偏离②）
 * </pre>
 * 另外补了 4 个本实现需要的端点：{@code get} / {@code object-types} /
 * {@code available-list}（按对象取可用 webhook）+ 3 个 mock 端点（另一个控制器）。
 *
 * <p>类名带 {@code Zentao} 前缀是纪律（坑位 #25/#47）：新模块先确认没有同名
 * Controller / Service / Mapper / 注入字段（本次全仓 grep 过，Webhook* 短名没有冲突）。
 */
@Tag(name = "管理后台 - 禅道 Webhook")
@RestController
@RequestMapping("/zentao/webhook")
@Validated
public class ZentaoWebhookController {

    @Resource
    private WebhookService webhookService;

    // ==================== CRUD ====================

    @GetMapping("/page")
    @Operation(summary = "Webhook 分页", description = "禅道 webhook::browse；只有 deleted = 0 一个硬条件")
    @PreAuthorize("@ss.hasPermission('zentao:webhook:query')")
    public CommonResult<PageResult<WebhookRespVO>> getPage(@Valid WebhookPageReqVO pageReqVO) {
        return success(BeanUtils.toBean(webhookService.getPage(pageReqVO), WebhookRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得 Webhook")
    @Parameter(name = "id", description = "编号", required = true, example = "92200")
    @PreAuthorize("@ss.hasPermission('zentao:webhook:query')")
    public CommonResult<WebhookRespVO> getWebhook(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(webhookService.getWebhook(id), WebhookRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "创建 Webhook",
            description = "name 必填；params 会被强制补上 text（禅道 create 是无条件追加）。"
                    + "参数 requireProduct=true 时要求 products 非空（错误码 WEBHOOK_PRODUCT_REQUIRED）")
    @PreAuthorize("@ss.hasPermission('zentao:webhook:create')")
    public CommonResult<Long> createWebhook(@Valid @RequestBody WebhookSaveReqVO reqVO) {
        return success(webhookService.createWebhook(BeanUtils.toBean(reqVO, WebhookDO.class),
                Boolean.TRUE.equals(reqVO.getRequireProduct())));
    }

    @PutMapping("/update")
    @Operation(summary = "修改 Webhook",
            description = "编辑态 url 必填（禅道 formdata.php 的 form->edit['url'] required=true，创建态是 false）")
    @PreAuthorize("@ss.hasPermission('zentao:webhook:update')")
    public CommonResult<Boolean> updateWebhook(@Valid @RequestBody WebhookSaveReqVO reqVO) {
        webhookService.updateWebhook(BeanUtils.toBean(reqVO, WebhookDO.class));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除 Webhook", description = "逻辑删（zt_webhook 有 deleted 列，与禅道一致）")
    @Parameter(name = "id", description = "编号", required = true, example = "92200")
    @PreAuthorize("@ss.hasPermission('zentao:webhook:delete')")
    public CommonResult<Boolean> deleteWebhook(@RequestParam("id") Long id) {
        webhookService.deleteWebhook(id);
        return success(true);
    }

    // ==================== 配置与「按对象取可用 webhook」 ====================

    @GetMapping("/object-types")
    @Operation(summary = "对象类型白名单",
            description = "逐条来自禅道 config/webhook.php 的 objectTypes（9 种），"
                    + "并带上本系统 zt_action 里真实出现过的动作")
    @PreAuthorize("@ss.hasPermission('zentao:webhook:query')")
    public CommonResult<List<WebhookObjectTypeVO>> getObjectTypes() {
        return success(webhookService.getObjectTypes());
    }

    @GetMapping("/available-list")
    @Operation(summary = "按对象取可用的 webhook",
            description = "「如果现在 objectType 上发生了 actionType，会有哪些 webhook 收到」——"
                    + "判定与发送时同一份白名单配置；product/execution 可选，用于进一步过滤")
    @Parameter(name = "objectType", description = "对象类型", required = true, example = "story")
    @Parameter(name = "actionType", description = "动作类型（可选，传了就按它过滤）", example = "opened")
    @Parameter(name = "product", description = "产品编号（可选）", example = "1")
    @Parameter(name = "execution", description = "执行编号（可选）", example = "1")
    @PreAuthorize("@ss.hasPermission('zentao:webhook:query')")
    public CommonResult<List<WebhookRespVO>> getAvailableList(@RequestParam("objectType") String objectType,
                                                              @RequestParam(value = "actionType", required = false) String actionType,
                                                              @RequestParam(value = "product", required = false) Long product,
                                                              @RequestParam(value = "execution", required = false) Long execution) {
        return success(BeanUtils.toBean(
                webhookService.getAvailableWebhooks(objectType, actionType, product, execution),
                WebhookRespVO.class));
    }

    // ==================== 发送 ====================

    @PostMapping("/send")
    @Operation(summary = "发送 Webhook（禅道 webhookModel::send）",
            description = "按禅道规则拼 payload（params 字段映射 + products 交集 + executions 包含 + 白名单动作）并 HTTP POST；"
                    + "**发送失败只落 zt_log，接口仍然返回 success**（禅道 send() 也是一律返回 true），不影响业务事务。"
                    + "actionID 可省（按对象+动作取最新一条 zt_action）；webhookId 可指定只发某一个")
    @PreAuthorize("@ss.hasPermission('zentao:webhook:create')")
    public CommonResult<WebhookSendRespVO> send(@Valid @RequestBody WebhookSendReqVO reqVO) {
        return success(webhookService.send(reqVO));
    }

    // ==================== 日志（读通用日志表 zt_log） ====================

    @GetMapping("/log-page")
    @Operation(summary = "Webhook 发送日志分页",
            description = "禅道 webhook::log → getLogList()；数据落在通用日志表 zt_log（objectType='webhook'），"
                    + "与 entry 模块共用一张表、不重复建表")
    @PreAuthorize("@ss.hasPermission('zentao:webhook:query')")
    public CommonResult<PageResult<WebhookLogRespVO>> getLogPage(@Valid WebhookLogPageReqVO pageReqVO) {
        return success(BeanUtils.toBean(webhookService.getLogPage(pageReqVO), WebhookLogRespVO.class));
    }

}
