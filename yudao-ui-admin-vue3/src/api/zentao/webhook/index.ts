import request from '@/config/axios'

// Webhook（禅道 module/webhook，「事件外发的通用出口」）
//
// 禅道 9 个业务 action 的对应关系：
//   browse  → getWebhookPage
//   create  → createWebhook
//   edit    → updateWebhook
//   delete  → deleteWebhook
//   log     → getWebhookLogPage（读通用日志表 zt_log，objectType='webhook'）
//   asyncSend → sendWebhook（禅道 send() 的同步语义入口）
//   bind / chooseDept / ajaxGetFeishuDeptList → 需要钉钉/企微/飞书的企业应用凭据，本实现未迁移
export interface WebhookVO {
  id?: number
  /** default（其他）/ dinggroup / wechatgroup / feishugroup
   *  dinguser / wechatuser / feishuuser 三种「应用消息」需要企业应用凭据，本实现不投递 */
  type?: string
  name: string
  url?: string
  /** 禅道域名：拼「查看链接」用；空 = 回落到站内前端地址 */
  domain?: string
  /** 加签密钥（钉钉群/飞书群） */
  secret?: string
  contentType?: string
  /** sync / async */
  sendType?: string
  /** 只对这些产品发（逗号列表；空 = 不限产品） */
  products?: string
  /** 只对这个执行发（逗号列表；空 = 不限执行） */
  executions?: string
  /** payload 字段列表（逗号）。text 会被强制补上，且 text 是现拼的「动作文本+查看链接」 */
  params?: string
  /** 本 webhook 关注的对象类型+动作（JSON，如 {"story":["opened"]}）；空 = 白名单全量 */
  actions?: string
  desc?: string
  createdBy?: string
  createdDate?: number
  editedBy?: string
  editedDate?: number
  createTime?: number
  /** 仅创建时使用：是否强制要求 products 非空（默认 false，与禅道「产品可空」一致） */
  requireProduct?: boolean
}

/** 发送日志行（zt_log，objectType='webhook'） */
export interface WebhookLogVO {
  id: number
  objectType: string
  objectID: number
  action?: number
  date?: number
  url?: string
  contentType?: string
  /** 实际发出去的 payload */
  data?: string
  /** 三方返回 body / HTTP 状态码 / 异常信息 */
  result?: string
}

/** 对象类型白名单项（逐条来自禅道 config/webhook.php 的 objectTypes） */
export interface WebhookObjectTypeVO {
  type: string
  name: string
  /** 白名单允许的动作 */
  actionTypes: string[]
  /** 本系统 zt_action 里真实出现过的动作 */
  observedActionTypes: string[]
  needAssign?: boolean
}

/** 单个 webhook 的发送明细 */
export interface WebhookSendItemVO {
  webhookId: number
  name?: string
  url?: string
  payload?: string
  success?: boolean
  result?: string
  logId?: number
  async?: boolean
}

/** 发送结果 */
export interface WebhookSendRespVO {
  objectType?: string
  objectID?: number
  actionType?: string
  actionID?: number
  payload?: string
  skipped?: boolean
  failedCount?: number
  matchedWebhookIds?: number[]
  items?: WebhookSendItemVO[]
  message?: string
}

/** mock 接收记录（仅联调/测试用） */
export interface WebhookMockRecordVO {
  seq: number
  receivedAt?: number
  contentType?: string
  body?: string
}

export const getWebhookPage = (params: any) => {
  return request.get({ url: '/zentao/webhook/page', params })
}

export const getWebhook = (id: number) => {
  return request.get<WebhookVO>({ url: '/zentao/webhook/get', params: { id } })
}

export const createWebhook = (data: WebhookVO) => {
  return request.post({ url: '/zentao/webhook/create', data })
}

export const updateWebhook = (data: WebhookVO) => {
  return request.put({ url: '/zentao/webhook/update', data })
}

export const deleteWebhook = (id: number) => {
  return request.delete({ url: '/zentao/webhook/delete', params: { id } })
}

/**
 * 对象类型白名单。
 * 页面的「对象类型 / 动作」下拉直接吃它 —— 白名单只有一份实现（后端 WebhookTypeConfig），
 * 前端不抄第二遍，避免两边跑偏。
 */
export const getWebhookObjectTypes = () => {
  return request.get<WebhookObjectTypeVO[]>({ url: '/zentao/webhook/object-types' })
}

/**
 * 按对象取可用的 webhook。
 * 回答「如果现在 objectType 上发生了 actionType，会有哪些 webhook 收到」。
 */
export const getAvailableWebhooks = (params: {
  objectType: string
  actionType?: string
  product?: number
  execution?: number
}) => {
  return request.get<WebhookVO[]>({ url: '/zentao/webhook/available-list', params })
}

/**
 * 发送（禅道 webhookModel::send）。
 * 失败只落 zt_log、接口仍返回 success —— 由返回体里的 items[].success / failedCount 判断。
 */
export const sendWebhook = (data: {
  objectType: string
  objectID: number
  actionType: string
  actionID?: number
  actor?: string
  webhookId?: number
}) => {
  return request.post<WebhookSendRespVO>({ url: '/zentao/webhook/send', data })
}

export const getWebhookLogPage = (params: any) => {
  return request.get({ url: '/zentao/webhook/log-page', params })
}

// ---- 以下三个只给「发送测试」用（后端是 @PermitAll 的 mock 接收端，不碰业务数据） ----

export const getMockRecords = () => {
  return request.get<WebhookMockRecordVO[]>({ url: '/zentao/webhook/mock-list' })
}

export const clearMockRecords = () => {
  return request.delete({ url: '/zentao/webhook/mock-clear' })
}

/**
 * 只给「发送测试」按钮用：往 mock 接收端直接打一段 JSON。
 *
 * <p>为什么不复用 {@link sendWebhook}：{@code sendWebhook} 走的是**真实链路**
 * （按 actionID 读 {@code zt_action} 再组装 payload），需要一条真实动作行；
 * 「发送测试」按钮要验的只是「这个地址收不收得到、回什么」，所以直接打一段自造 JSON。
 * 页面上两个能力是分开的：**「发送测试」** 打 mock 地址本身，
 * **「模拟触发」** 才调用 {@code sendWebhook}（真实的 zt_action → payload → POST → zt_log）。
 */
export const postMockReceive = (body: string) => {
  return request.post({ url: '/zentao/webhook/mock-receive', data: body })
}
