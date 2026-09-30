<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="Webhook：禅道「事件外发的通用出口」"
      description="业务动作（新增/编辑/关闭需求、任务、缺陷……）发生后，禅道遍历所有启用的 webhook，用 buildData() 把这次 zt_action 的字段按「参数」映射成 JSON，HTTP POST 到你填的地址，结果写进通用日志表 zt_log（objectType=webhook）。本页照抄三条关键规则：① objectTypes 白名单（9 种对象类型 + 各自允许的动作）；② products 取交集、executions 包含才发；③ 发送失败只落日志、不影响业务。"
    />
  </ContentWrap>

  <el-tabs v-model="tab" class="px-10px">
    <!-- ① Webhook 列表 -->
    <el-tab-pane label="Webhook 列表" name="list">
      <ContentWrap>
        <el-form class="-mb-15px" :inline="true" label-width="70px">
          <el-form-item label="名称">
            <el-input v-model="query.name" placeholder="名称模糊" clearable class="!w-160px" @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="query.type" clearable placeholder="全部" class="!w-180px">
              <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 查询</el-button>
            <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
            <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:webhook:create']">
              <Icon icon="ep:plus" class="mr-5px" /> 新增 Webhook
            </el-button>
          </el-form-item>
        </el-form>
      </ContentWrap>

      <ContentWrap>
        <el-table v-loading="loading" :data="list" empty-text="还没有配置 Webhook">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="名称" prop="name" min-width="150" show-overflow-tooltip />
          <el-table-column label="类型" width="150">
            <template #default="{ row }">
              <el-tag size="small" :type="row.type === 'default' ? 'info' : 'success'">
                {{ typeLabel(row.type) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Hook 地址" prop="url" min-width="240" show-overflow-tooltip />
          <el-table-column label="关联产品" width="110" align="center">
            <template #default="{ row }">
              <span v-if="!row.products" class="text-gray-400">不限</span>
              <span v-else>{{ row.products }}</span>
            </template>
          </el-table-column>
          <el-table-column label="关注对象（actions）" min-width="200">
            <template #default="{ row }">
              <span v-if="!row.actions" class="text-gray-400">白名单全量</span>
              <template v-else>
                <el-tag
                  v-for="(acts, ot) in parseActions(row.actions)"
                  :key="ot"
                  size="small"
                  class="mr-4px mb-2px"
                >{{ objectTypeLabel(String(ot)) }}:{{ (acts as string[]).length }}</el-tag>
              </template>
            </template>
          </el-table-column>
          <el-table-column label="发送方式" width="90" align="center">
            <template #default="{ row }">{{ row.sendType === 'async' ? '异步' : '同步' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170" align="center">
            <template #default="{ row }">{{ formatDate(row.createdDate) }}</template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="290" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="sendTest(row)" v-hasPermi="['zentao:webhook:create']">发送测试</el-button>
              <el-button link type="primary" @click="simulate(row)" v-hasPermi="['zentao:webhook:create']">模拟触发</el-button>
              <el-button link type="primary" @click="openLog(row)">日志</el-button>
              <el-button link type="primary" @click="openForm('edit', row)" v-hasPermi="['zentao:webhook:update']">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)" v-hasPermi="['zentao:webhook:delete']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
      </ContentWrap>

      <ContentWrap>
        <el-card shadow="never">
          <template #header>
            <span class="font-bold">「某个对象上会发生什么」预演（available-list）</span>
            <span class="ml-8px text-12px text-gray-400">选对象类型与动作，看哪些 webhook 会被命中 —— 判定与发送时同一份白名单配置</span>
          </template>
          <el-form :inline="true" label-width="80px">
            <el-form-item label="对象类型">
              <el-select v-model="probe.objectType" class="!w-180px" @change="onProbeObjectTypeChange">
                <el-option v-for="ot in objectTypes" :key="ot.type" :label="`${ot.name}（${ot.type}）`" :value="ot.type" />
              </el-select>
            </el-form-item>
            <el-form-item label="动作">
              <el-select v-model="probe.actionType" clearable placeholder="不选 = 不限动作" class="!w-200px">
                <el-option v-for="a in probeActionOptions" :key="a" :label="`${a}（${actionLabel(a)}）`" :value="a" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="runAvailable"><Icon icon="ep:search" class="mr-5px" /> 查可用 webhook</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="available" size="small" empty-text="点上面的按钮查一次">
            <el-table-column label="编号" prop="id" width="80" align="center" />
            <el-table-column label="名称" prop="name" min-width="150" />
            <el-table-column label="Hook 地址" prop="url" min-width="260" show-overflow-tooltip />
            <el-table-column label="关注对象" min-width="200">
              <template #default="{ row }">
                <span v-if="!row.actions" class="text-gray-400">白名单全量</span>
                <span v-else>{{ row.actions }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </ContentWrap>
    </el-tab-pane>

    <!-- ② 发送日志（读 zt_log） -->
    <el-tab-pane label="发送日志" name="log">
      <ContentWrap>
        <el-form class="-mb-15px" :inline="true" label-width="80px">
          <el-form-item label="Webhook">
            <el-select v-model="logQuery.objectID" clearable placeholder="全部" class="!w-200px" @change="loadLog">
              <el-option v-for="w in allWebhooks" :key="w.id" :label="`${w.name}（${w.id}）`" :value="w.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="地址">
            <el-input v-model="logQuery.url" placeholder="地址模糊" clearable class="!w-200px" @keyup.enter="handleLogQuery" />
          </el-form-item>
          <el-form-item>
            <el-button @click="handleLogQuery"><Icon icon="ep:search" class="mr-5px" /> 查询</el-button>
          </el-form-item>
        </el-form>
      </ContentWrap>
      <ContentWrap>
        <el-table v-loading="logLoading" :data="logList" empty-text="还没有发送记录">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="发送时间" width="170" align="center">
            <template #default="{ row }">{{ formatDate(row.date) }}</template>
          </el-table-column>
          <el-table-column label="Webhook" width="90" align="center" prop="objectID" />
          <el-table-column label="动作" width="80" align="center" prop="action" />
          <el-table-column label="请求地址" prop="url" min-width="230" show-overflow-tooltip />
          <el-table-column label="结果" min-width="200">
            <template #default="{ row }">
              <el-tag size="small" :type="isFailed(row.result) ? 'danger' : 'success'">
                {{ isFailed(row.result) ? '失败' : '成功' }}
              </el-tag>
              <span class="ml-6px text-12px">{{ shortText(row.result) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="payload" min-width="220">
            <template #default="{ row }">
              <el-button link type="primary" @click="showPayload(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          :total="logTotal"
          v-model:page="logQuery.pageNo"
          v-model:limit="logQuery.pageSize"
          @pagination="loadLog"
        />
      </ContentWrap>
    </el-tab-pane>

    <!-- ③ 发送测试（mock 接收端） -->
    <el-tab-pane label="发送测试（mock 接收端）" name="mock">
      <ContentWrap>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          class="mb-10px"
          title="这张表只是「联调脚手架」，不是业务数据"
          description="后端 /zentao/webhook/mock-receive 是 @PermitAll 的 mock 接收端点：任何 POST 过来的 body 都原样存进**服务端内存**（上限 200 条、进程重启即清空），供端到端回归断言 payload。演示 webhook 92200 的地址就指向它。"
        />
        <div class="mb-10px">
          <el-button @click="loadMock"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
          <el-button type="danger" plain @click="clearMock">
            <Icon icon="ep:delete" class="mr-5px" /> 清空
          </el-button>
        </div>
        <el-table v-loading="mockLoading" :data="mockList" empty-text="还没有收到请求">
          <el-table-column label="序号" prop="seq" width="80" align="center" />
          <el-table-column label="接收时间" width="170" align="center">
            <template #default="{ row }">{{ formatDate(row.receivedAt) }}</template>
          </el-table-column>
          <el-table-column label="Content-Type" prop="contentType" min-width="180" show-overflow-tooltip />
          <el-table-column label="请求体" min-width="320">
            <template #default="{ row }">
              <span class="text-12px">{{ shortText(row.body, 160) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </ContentWrap>
    </el-tab-pane>
  </el-tabs>

  <!-- 新建 / 编辑 -->
  <el-dialog v-model="formVisible" :title="formTitle" width="760px" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="必填；禅道 config/webhook.php 的 requiredFields" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="form.type" class="!w-280px" @change="onTypeChange">
          <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <span class="ml-8px text-12px text-gray-400">群机器人类会被强制成 application/json</span>
      </el-form-item>
      <el-form-item label="Hook 地址" :prop="form.id ? 'url' : ''">
        <el-input v-model="form.url" :placeholder="urlPlaceholder" />
        <div class="text-12px text-gray-400">
          禅道规则：非空时必须以 http:// 或 https:// 开头；**编辑态必填**（创建态可空，这个不对称是禅道原样）
        </div>
      </el-form-item>
      <el-form-item label="禅道域名">
        <el-input v-model="form.domain" placeholder="拼「查看链接」用；留空回落到站内前端地址" />
      </el-form-item>
      <el-form-item label="密钥 secret">
        <el-input v-model="form.secret" placeholder="钉钉群：timestamp(ms)+\n+secret 做 HMAC-SHA256 后拼到 URL；飞书群：塞进 body" />
      </el-form-item>
      <el-form-item label="内容类型">
        <el-input v-model="form.contentType" class="!w-260px" placeholder="application/json" />
      </el-form-item>
      <el-form-item label="发送方式">
        <el-radio-group v-model="form.sendType">
          <el-radio label="sync">同步</el-radio>
          <el-radio label="async">异步</el-radio>
        </el-radio-group>
        <span class="ml-8px text-12px text-gray-400">
          禅道 async 会落 zt_notify 由 cron 消费；本实现直接发（日志与 payload 一致）
        </span>
      </el-form-item>
      <el-form-item label="关联产品">
        <el-select v-model="formProducts" multiple clearable placeholder="空 = 所有产品的动作都触发" class="!w-460px">
          <el-option v-for="p in products" :key="p.id" :label="`${p.name}（${p.id}）`" :value="p.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="关联执行">
        <el-select v-model="formExecutions" multiple clearable placeholder="空 = 所有执行的动作都触发" class="!w-460px">
          <el-option v-for="e in executions" :key="e.id" :label="`${e.name}（${e.id}）`" :value="e.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="参数（payload 字段）">
        <el-checkbox-group v-model="formParams">
          <el-checkbox v-for="p in paramOptions" :key="p.value" :label="p.value">{{ p.label }}</el-checkbox>
        </el-checkbox-group>
        <div class="text-12px text-gray-400">
          值取自**动作行** zt_action 的同名列；保存后 text 会被强制补上，且 text 是现拼的「动作文本 + 查看链接」
        </div>
      </el-form-item>
      <el-form-item label="触发动作">
        <div class="w-full">
          <div v-for="ot in objectTypes" :key="ot.type" class="mb-6px">
            <span class="inline-block w-110px text-12px">{{ ot.name }}（{{ ot.type }}）</span>
            <el-checkbox-group v-model="formActions[ot.type]" class="inline-block">
              <el-checkbox v-for="a in ot.actionTypes" :key="a" :label="a" class="mr-8px">
                <span :class="{ 'text-gray-400': !ot.observedActionTypes.includes(a) }">{{ a }}</span>
              </el-checkbox>
            </el-checkbox-group>
          </div>
          <div class="text-12px text-gray-400">
            灰掉的动作是「白名单允许、但本系统 zt_action 里还没出现过」；一个都不勾 = 用白名单全量
          </div>
        </div>
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.desc" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取 消</el-button>
      <el-button type="primary" :loading="saving" @click="submitForm">确 定</el-button>
    </template>
  </el-dialog>

  <!-- 日志抽屉 -->
  <el-drawer v-model="logDrawerVisible" :title="logDrawerTitle" size="720px">
    <el-table v-loading="logDrawerLoading" :data="logDrawerList" empty-text="这个 webhook 还没有发送记录">
      <el-table-column label="编号" prop="id" width="70" align="center" />
      <el-table-column label="发送时间" width="170" align="center">
        <template #default="{ row }">{{ formatDate(row.date) }}</template>
      </el-table-column>
      <el-table-column label="动作" width="70" align="center" prop="action" />
      <el-table-column label="结果" min-width="160">
        <template #default="{ row }">
          <el-tag size="small" :type="isFailed(row.result) ? 'danger' : 'success'">
            {{ isFailed(row.result) ? '失败' : '成功' }}
          </el-tag>
          <span class="ml-6px text-12px">{{ shortText(row.result) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="payload" width="90" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="showPayload(row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="logDrawerTotal"
      v-model:page="logDrawerQuery.pageNo"
      v-model:limit="logDrawerQuery.pageSize"
      @pagination="loadLogDrawer"
    />
  </el-drawer>

  <!-- payload / 发送结果 -->
  <el-dialog v-model="payloadVisible" title="详情" width="760px" append-to-body>
    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="说明">{{ payloadInfo.title }}</el-descriptions-item>
      <el-descriptions-item label="请求地址">{{ payloadInfo.url || '—' }}</el-descriptions-item>
    </el-descriptions>
    <el-alert
      v-if="payloadInfo.message"
      :type="payloadInfo.failed ? 'warning' : 'success'"
      :closable="false"
      show-icon
      class="mt-10px"
    >
      <template #title>{{ payloadInfo.message }}</template>
    </el-alert>
    <div class="mt-10px font-bold text-13px">payload</div>
    <pre class="text-12px whitespace-pre-wrap break-all bg-gray-50 p-8px">{{ prettyJson(payloadInfo.payload) }}</pre>
    <div v-if="payloadInfo.result" class="mt-10px font-bold text-13px">发送结果</div>
    <pre v-if="payloadInfo.result" class="text-12px whitespace-pre-wrap break-all bg-gray-50 p-8px">{{ payloadInfo.result }}</pre>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as WebhookApi from '@/api/zentao/webhook'
import { getProductSimpleList } from '@/api/zentao/product'
import { getProjectSimpleList } from '@/api/zentao/project'
import { getExecutionListByProject } from '@/api/zentao/execution'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'ZentaoWebhook' })

const message = useMessage()
const tab = ref('list')
const loading = ref(false)
const saving = ref(false)
const list = ref<WebhookApi.WebhookVO[]>([])
const total = ref(0)
const query = reactive<any>({ pageNo: 1, pageSize: 10 })

/** 类型下拉逐条来自禅道 lang/zh-cn.php 的 typeList */
const typeOptions = [
  { value: 'default', label: '其他（通用 JSON）' },
  { value: 'dinggroup', label: '钉钉群通知机器人' },
  { value: 'wechatgroup', label: '企业微信群机器人' },
  { value: 'feishugroup', label: '飞书群通知机器人' }
]
/** 三种「应用消息」禅道有、本实现不投递（需要企业应用凭据 + openID 绑定），只作为提示展示 */
const userAppTypes = ['dinguser', 'wechatuser', 'feishuuser']

/** 参数下拉逐条来自禅道 lang/zh-cn.php 的 paramsList */
const paramOptions = [
  { value: 'objectType', label: '对象类型' },
  { value: 'objectID', label: '对象ID' },
  { value: 'product', label: '所属产品' },
  { value: 'execution', label: '所属执行' },
  { value: 'action', label: '动作' },
  { value: 'actor', label: '操作者' },
  { value: 'date', label: '操作日期' },
  { value: 'comment', label: '备注' },
  { value: 'text', label: '操作内容' }
]

const objectTypes = ref<WebhookApi.WebhookObjectTypeVO[]>([])
const allWebhooks = ref<WebhookApi.WebhookVO[]>([])
const products = ref<any[]>([])
const executions = ref<any[]>([])

// ---- 表单 ----
const formVisible = ref(false)
const formTitle = ref('')
const formRef = ref()
const form = ref<WebhookApi.WebhookVO>({ name: '', type: 'default', contentType: 'application/json', sendType: 'sync' })
const formProducts = ref<number[]>([])
const formExecutions = ref<number[]>([])
const formParams = ref<string[]>(['objectType', 'objectID', 'action', 'text'])
const formActions = reactive<Record<string, string[]>>({})
const rules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  url: [
    { required: true, message: '编辑态 Hook 地址必填', trigger: 'blur' },
    { pattern: /^http(s)?:\/\//, message: 'Hook 地址只能以 http:// 或 https:// 开头', trigger: 'blur' }
  ]
}
const urlPlaceholder = computed(() =>
  form.value.type === 'default'
    ? 'http://127.0.0.1:48080/admin-api/zentao/webhook/mock-receive'
    : '群机器人地址（钉钉/企微/飞书后台获取）'
)

// ---- 日志（列表页签 + 抽屉共用一个 api）----
const logLoading = ref(false)
const logList = ref<WebhookApi.WebhookLogVO[]>([])
const logTotal = ref(0)
const logQuery = reactive<any>({ pageNo: 1, pageSize: 10, objectType: 'webhook' })

const logDrawerVisible = ref(false)
const logDrawerLoading = ref(false)
const logDrawerTitle = ref('发送日志')
const logDrawerList = ref<WebhookApi.WebhookLogVO[]>([])
const logDrawerTotal = ref(0)
const logDrawerQuery = reactive<any>({ pageNo: 1, pageSize: 10, objectType: 'webhook', objectID: undefined })

// ---- mock 接收端 ----
const mockLoading = ref(false)
const mockList = ref<WebhookApi.WebhookMockRecordVO[]>([])

// ---- 预演 ----
const probe = reactive<{ objectType: string; actionType?: string }>({ objectType: 'story' })
const available = ref<WebhookApi.WebhookVO[]>([])

// ---- 详情弹窗 ----
const payloadVisible = ref(false)
const payloadInfo = reactive({ title: '', url: '', payload: '', result: '', message: '', failed: false })

const load = async () => {
  loading.value = true
  try {
    const data = await WebhookApi.getWebhookPage(query)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 下拉要的是「全部 webhook」，而分页只会给当前页 —— 这里拉一页大一点的当选项源 */
const loadAllWebhooks = async () => {
  const data = await WebhookApi.getWebhookPage({ pageNo: 1, pageSize: 100 })
  allWebhooks.value = data.list
}

const handleQuery = () => {
  query.pageNo = 1
  load()
}

const resetQuery = () => {
  query.name = undefined
  query.type = undefined
  handleQuery()
}

const typeLabel = (type?: string) => {
  const found = typeOptions.find((t) => t.value === type)
  if (found) return found.label
  if (type && userAppTypes.includes(type)) return `${type}（本实现未投递）`
  return type || '其他'
}

const objectTypeLabel = (type: string) => {
  const found = objectTypes.value.find((t) => t.type === type)
  return found ? found.name : type
}

/** 动作中文标签 —— 与后端 WebhookTypeConfig.actionLabel 保持同一份叫法（只用于展示） */
const actionLabels: Record<string, string> = {
  opened: '创建了',
  edited: '编辑了',
  closed: '关闭了',
  undeleted: '还原了',
  commented: '评论了',
  frombug: '转了需求',
  changed: '变更了',
  reviewed: '评审了',
  activated: '激活了',
  started: '开始了',
  delayed: '延期了',
  suspended: '挂起了',
  assigned: '指派了',
  confirmed: '确认了',
  finished: '完成了',
  paused: '暂停了',
  canceled: '取消了',
  restarted: '继续了',
  bugconfirmed: '确认了',
  resolved: '解决了',
  blocked: '阻塞了'
}
const actionLabel = (a: string) => actionLabels[a] || a

const probeActionOptions = computed(() => {
  const found = objectTypes.value.find((t) => t.type === probe.objectType)
  return found ? found.actionTypes : []
})

const onProbeObjectTypeChange = () => {
  probe.actionType = undefined
  available.value = []
}

const runAvailable = async () => {
  available.value = await WebhookApi.getAvailableWebhooks({
    objectType: probe.objectType,
    actionType: probe.actionType
  })
}

/** actions 列是 JSON；解析失败就当作「没配」，页面不能因为一行脏数据白屏 */
const parseActions = (raw?: string): Record<string, string[]> => {
  if (!raw) return {}
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

const prettyJson = (raw?: string) => {
  if (!raw) return '（空）'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

const shortText = (raw?: string, max = 80) => {
  if (!raw) return '—'
  const one = String(raw).replace(/\s+/g, ' ')
  return one.length > max ? one.slice(0, max) + '…' : one
}

/** 结果是不是失败：本实现把失败写成「发送失败：...」，也兼容 HTTP 4xx/5xx 的裸状态码 */
const isFailed = (result?: string) => {
  if (!result) return false
  if (result.startsWith('发送失败')) return true
  const code = Number(result)
  return Number.isFinite(code) && code >= 400
}

const showPayload = (row: WebhookApi.WebhookLogVO) => {
  payloadInfo.title = `发送日志 #${row.id}（webhook ${row.objectID}，动作 ${row.action ?? '-'}）`
  payloadInfo.url = row.url || ''
  payloadInfo.payload = row.data || ''
  payloadInfo.result = row.result || ''
  payloadInfo.failed = isFailed(row.result)
  payloadInfo.message = payloadInfo.failed
    ? '这次发送失败了。禅道的语义是：失败只落 zt_log，send() 仍然返回 true，不影响业务事务。'
    : ''
  payloadVisible.value = true
}

const openForm = (mode: string, row?: WebhookApi.WebhookVO) => {
  Object.keys(formActions).forEach((k) => delete formActions[k])
  objectTypes.value.forEach((ot) => (formActions[ot.type] = []))
  if (mode === 'edit' && row) {
    form.value = { ...row }
    formProducts.value = row.products ? row.products.split(',').filter(Boolean).map(Number) : []
    formExecutions.value = row.executions ? row.executions.split(',').filter(Boolean).map(Number) : []
    formParams.value = row.params ? row.params.split(',').filter(Boolean) : ['text']
    const parsed = parseActions(row.actions)
    Object.keys(parsed).forEach((ot) => (formActions[ot] = parsed[ot] || []))
    formTitle.value = '编辑 Webhook'
  } else {
    form.value = {
      name: '',
      type: 'default',
      url: 'http://127.0.0.1:48080/admin-api/zentao/webhook/mock-receive',
      domain: '',
      secret: '',
      contentType: 'application/json',
      sendType: 'sync',
      desc: ''
    }
    formProducts.value = []
    formExecutions.value = []
    formParams.value = ['objectType', 'objectID', 'action', 'text']
    formTitle.value = '新增 Webhook'
  }
  formVisible.value = true
}

const onTypeChange = (type: string) => {
  if (userAppTypes.includes(type)) {
    message.warning('「应用消息」类型需要企业应用凭据，本实现不投递；保存后发送会记一条未投递的日志')
  }
}

const buildActions = () => {
  const result: Record<string, string[]> = {}
  Object.keys(formActions).forEach((ot) => {
    if (formActions[ot] && formActions[ot].length) result[ot] = formActions[ot]
  })
  return Object.keys(result).length ? JSON.stringify(result) : ''
}

const submitForm = async () => {
  // Element Plus 的 validate() 校验失败时是 **reject**（带字段错误对象），
  // 不 catch 就会冒一条未捕获的 unhandledrejection（坑位 #53）。
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload: WebhookApi.WebhookVO = {
      ...form.value,
      products: formProducts.value.length ? formProducts.value.join(',') : '',
      executions: formExecutions.value.length ? formExecutions.value.join(',') : '',
      params: formParams.value.length ? formParams.value.join(',') : 'text',
      actions: buildActions()
    }
    if (payload.id) {
      await WebhookApi.updateWebhook(payload)
      message.success('已保存')
    } else {
      await WebhookApi.createWebhook(payload)
      message.success('已创建')
    }
    formVisible.value = false
    await Promise.all([load(), loadAllWebhooks()])
  } catch {
    // 后端业务失败时（例如创建态 Hook 地址留空，后端 WebhookService:268 会拒：
    // 「请求地址不能为空」）axios 拦截器已经弹过 ElNotification 了
    // （config/axios/service.ts:218，注意它随后是 `return Promise.reject('error')`），
    // 但那个 reject 会一路冒出 submitForm —— 这是 async 函数、又没人接，
    // 于是变成一条 unhandledrejection：界面检查实测**每次非法提交都抓到一条**
    // Playwright pageerror（ctor=PlaywrightError / message="error" / json={"log":[],"name":""}）。
    // 这里吞掉：提示已经给过用户了，弹窗保持打开让人改，不该再冒未捕获的 Promise。
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row: WebhookApi.WebhookVO) => {
  await message.delConfirm(`确认删除 Webhook「${row.name}」？`)
  await WebhookApi.deleteWebhook(row.id!)
  message.success('已删除')
  await Promise.all([load(), loadAllWebhooks()])
}

// ---- 日志 ----
const handleLogQuery = () => {
  logQuery.pageNo = 1
  loadLog()
}

const loadLog = async () => {
  logLoading.value = true
  try {
    const data = await WebhookApi.getWebhookLogPage(logQuery)
    logList.value = data.list
    logTotal.value = data.total
  } finally {
    logLoading.value = false
  }
}

const openLog = async (row: WebhookApi.WebhookVO) => {
  logDrawerTitle.value = `发送日志 - ${row.name}（zt_log objectType=webhook）`
  logDrawerQuery.objectID = row.id
  logDrawerQuery.pageNo = 1
  logDrawerVisible.value = true
  await loadLogDrawer()
}

const loadLogDrawer = async () => {
  logDrawerLoading.value = true
  try {
    const data = await WebhookApi.getWebhookLogPage(logDrawerQuery)
    logDrawerList.value = data.list
    logDrawerTotal.value = data.total
  } finally {
    logDrawerLoading.value = false
  }
}

// ---- 发送测试 / 模拟触发 ----
const loadMock = async () => {
  mockLoading.value = true
  try {
    mockList.value = await WebhookApi.getMockRecords()
  } finally {
    mockLoading.value = false
  }
}

const clearMock = async () => {
  await WebhookApi.clearMockRecords()
  message.success('已清空 mock 记录')
  await loadMock()
}

/** 发送测试：把一段 JSON 打到 mock 接收端，再从 mock-list 读回来 —— 验「地址收不收得到」 */
const sendTest = async (row: WebhookApi.WebhookVO) => {
  const body = JSON.stringify({
    test: 'webhook-send-test',
    webhookId: row.id,
    name: row.name,
    type: row.type,
    at: new Date().toISOString()
  })
  await WebhookApi.postMockReceive(body)
  await loadMock()
  payloadInfo.title = `发送测试：${row.name}`
  payloadInfo.url = row.url || ''
  payloadInfo.payload = body
  payloadInfo.result = '已 POST 到 mock 接收端（/zentao/webhook/mock-receive）'
  payloadInfo.failed = false
  payloadInfo.message = '这是「地址能不能收到」的连通性测试。要验真实链路的 payload 与日志，请用「模拟触发」。'
  payloadVisible.value = true
  tab.value = 'mock'
}

/**
 * 模拟触发：走**真实链路** —— 按对象+动作取 zt_action → buildData 组 payload → POST → 写 zt_log。
 *
 * 用演示需求 92201 + edited（55-zt_webhook.sql 里给了对应的动作行），
 * actionID 不传，让后端按「对象类型 + 对象编号 + 动作」取最新一条（与禅道一致）。
 */
const simulate = async (row: WebhookApi.WebhookVO) => {
  const resp = await WebhookApi.sendWebhook({
    objectType: 'story',
    objectID: 92201,
    actionType: 'edited',
    webhookId: row.id
  })
  const item = (resp.items || [])[0]
  payloadInfo.title = `模拟触发：${row.name}（story 92201 / edited）`
  payloadInfo.url = row.url || ''
  payloadInfo.payload = resp.payload || item?.payload || ''
  payloadInfo.result = item?.result || resp.message || ''
  payloadInfo.failed = resp.skipped ? true : (resp.failedCount || 0) > 0
  payloadInfo.message = resp.message || ''
  payloadVisible.value = true
  await Promise.all([loadLog(), loadMock()])
}

/**
 * 切到「发送日志」/「发送测试」页签时把对应列表拉起来。
 *
 * 为什么需要：loadLog / loadMock 原来只在「查询」「刷新」「分页」「模拟触发 / 发送测试」里调，
 * 首次切到页签时表格是空的 —— 「发送日志」页签会显示「还没有发送记录」，
 * 可 zt_log 里明明有记录（界面检查实测：切过去后**激活页签内 0 行**，
 * 而接口 /zentao/webhook/log-page 有数据）。页签自己不带 @tab-change，
 * 所以这里监听 tab 变化补一次加载。
 */
watch(tab, (v) => {
  if (v === 'log') loadLog()
  if (v === 'mock') loadMock()
})

onMounted(async () => {
  objectTypes.value = await WebhookApi.getWebhookObjectTypes()
  objectTypes.value.forEach((ot) => (formActions[ot.type] = []))
  products.value = await getProductSimpleList()
  // 执行下拉：禅道的 zt_project 里执行有几千条，这里只取「第一个项目」下的执行当选项（够演示用）
  const projects = await getProjectSimpleList()
  if (projects.length) {
    executions.value = await getExecutionListByProject(projects[0].id!)
  }
  await Promise.all([load(), loadAllWebhooks()])
})
</script>
