<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="应用接入：第三方系统带 code + token 免登录调禅道的入口"
      description="两种签名：带时间戳 token=md5(code+key+time)，且 time 必须大于上次调用时间（防重放）；不带时间戳 token=md5(md5(查询串去掉 token)+key)。校验链与错误码照抄禅道：401 签名类、403 IP 或未绑账号、404 应用不存在、405 重放、406 账号不存在、407 时间戳格式。IP 白名单支持 * / 精确 / 逗号列表 / a-b 区间 / 192.168.1.* / CIDR，留空等于不限制（禅道全局默认 *）。"
    />

    <el-form class="-mb-15px" :inline="true" label-width="70px">
      <el-form-item label="名称">
        <el-input v-model="query.name" placeholder="应用名称" clearable class="!w-160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="代号">
        <el-input v-model="query.code" placeholder="应用代号" clearable class="!w-140px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="免密">
        <el-select v-model="query.freePasswd" clearable placeholder="全部" class="!w-120px">
          <el-option label="开启" :value="1" />
          <el-option label="关闭" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:entry:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增应用
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" empty-text="还没有配置应用接入">
      <el-table-column label="编号" prop="id" width="70" align="center" />
      <el-table-column label="应用名称" prop="name" min-width="150" show-overflow-tooltip />
      <el-table-column label="代号" prop="code" width="110" />
      <el-table-column label="密钥" min-width="230">
        <template #default="{ row }">
          <span class="font-mono text-12px">{{ row.key }}</span>
        </template>
      </el-table-column>
      <el-table-column label="绑定账号" prop="account" width="100" />
      <el-table-column label="免密" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.freePasswd === 1 ? 'success' : 'info'" size="small">
            {{ row.freePasswd === 1 ? '开启' : '关闭' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="允许 IP" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="!row.ip" class="text-gray-400">不限制（留空）</span>
          <span v-else>{{ row.ip }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最近调用" width="160" align="center">
        <template #default="{ row }">
          <span v-if="!row.calledTime" class="text-gray-400">未调用</span>
          <span v-else>{{ formatTime(row.calledTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createdDate" width="160" align="center">
        <template #default="{ row }">{{ formatDate(row.createdDate) }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openLog(row)">日志</el-button>
          <el-button link type="primary" @click="openForm('edit', row)" v-hasPermi="['zentao:entry:update']">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)" v-hasPermi="['zentao:entry:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <ContentWrap>
    <el-card shadow="never">
      <template #header>
        <span class="font-bold">接入自测</span>
        <span class="ml-8px text-12px text-gray-400">
          先用「生成签名」算出 token，再「发起校验」走一遍禅道的校验链（校验接口是免登录的）
        </span>
      </template>
      <el-form :inline="true" label-width="80px">
        <el-form-item label="应用">
          <el-select v-model="probe.code" class="!w-200px" @change="onProbeCodeChange">
            <el-option v-for="e in simpleList" :key="e.code" :label="`${e.name}（${e.code}）`" :value="e.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间戳">
          <el-input v-model="probe.time" class="!w-180px" />
        </el-form-item>
        <el-form-item label="来源 IP">
          <el-input v-model="probe.clientIp" placeholder="留空取真实 IP" class="!w-180px" />
        </el-form-item>
        <el-form-item>
          <el-button :disabled="!probe.code" @click="runSign">
            <Icon icon="ep:key" class="mr-5px" /> 生成签名
          </el-button>
          <el-button type="primary" :disabled="!probe.token" @click="runVerify">
            <Icon icon="ep:connection" class="mr-5px" /> 发起校验
          </el-button>
        </el-form-item>
      </el-form>
      <el-descriptions v-if="probe.token" :column="1" border size="small" class="mb-10px">
        <el-descriptions-item label="token">{{ probe.token }}</el-descriptions-item>
        <el-descriptions-item label="签名方式">{{ probe.mode }}</el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="probeResult" :type="probeResult.type" :closable="false" show-icon class="mb-10px">
        <template #title>{{ probeResult.title }}</template>
        <pre class="text-12px whitespace-pre-wrap mb-0">{{ probeResult.detail }}</pre>
      </el-alert>
      <div class="text-12px text-gray-400">
        提示：把「来源 IP」改成 8.8.8.8 再看内网受限应用（10.0.0.0/8），会拿到 403 该IP被限制访问；
        时间戳重复用一次会拿到 405（防重放）。
      </div>
    </el-card>
  </ContentWrap>

  <el-dialog v-model="formVisible" :title="formTitle" width="620px" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="应用名称" prop="name">
        <el-input v-model="form.name" placeholder="例如 OA 办公系统" />
      </el-form-item>
      <el-form-item label="应用代号" prop="code">
        <el-input v-model="form.code" placeholder="字母或数字的组合，例如 oa" />
      </el-form-item>
      <el-form-item label="密钥" prop="key">
        <el-input v-model="form.key" placeholder="留空自动生成 32 位">
          <template #append>
            <el-button @click="regenKey">重新生成</el-button>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="免密登录">
        <el-switch v-model="form.freePasswd" :active-value="1" :inactive-value="0" />
        <span class="ml-8px text-12px text-gray-400">开启后可不绑账号，调用方还能用 account 参数指定以谁的身份进入</span>
      </el-form-item>
      <el-form-item label="绑定账号" :prop="form.freePasswd === 1 ? '' : 'account'">
        <el-input v-model="form.account" placeholder="禅道账号，例如 admin" class="!w-260px" />
      </el-form-item>
      <el-form-item label="允许 IP">
        <el-input v-model="form.ip" :disabled="allIP" placeholder="逗号分隔；支持 192.168.1.* 与 CIDR" />
        <el-checkbox v-model="allIP" class="ml-10px" @change="onAllIPChange">无限制</el-checkbox>
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

  <el-dialog v-model="logVisible" :title="logTitle" width="760px" append-to-body>
    <el-table v-loading="logLoading" :data="logList" empty-text="还没有调用记录">
      <el-table-column label="编号" prop="id" width="80" align="center" />
      <el-table-column label="请求时间" width="170" align="center">
        <template #default="{ row }">{{ formatDate(row.date) }}</template>
      </el-table-column>
      <el-table-column label="请求地址" prop="url" min-width="280" show-overflow-tooltip />
      <el-table-column label="结果" prop="result" width="130" />
    </el-table>
    <Pagination
      :total="logTotal"
      v-model:page="logQuery.pageNo"
      v-model:limit="logQuery.pageSize"
      @pagination="loadLog"
    />
  </el-dialog>
</template>

<script lang="ts" setup>
import * as EntryApi from '@/api/zentao/entry'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'ZentaoEntry' })

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const list = ref<EntryApi.EntryVO[]>([])
const total = ref(0)
const query = reactive<any>({ pageNo: 1, pageSize: 10 })

const allIP = ref(false)
const formVisible = ref(false)
const formTitle = ref('')
const formRef = ref()
const form = ref<EntryApi.EntryVO>({ name: '', code: '', freePasswd: 0, ip: '' })
const rules = {
  name: [{ required: true, message: '应用名称不能为空', trigger: 'blur' }],
  code: [
    { required: true, message: '应用代号不能为空', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]+$/, message: '应用代号必须为字母或数字的组合', trigger: 'blur' }
  ],
  account: [{ required: true, message: '非免密登录必须绑定账号', trigger: 'blur' }]
}

// 日志
const logVisible = ref(false)
const logLoading = ref(false)
const logTitle = ref('调用日志')
const logList = ref<EntryApi.EntryLogVO[]>([])
const logTotal = ref(0)
const logQuery = reactive<any>({ pageNo: 1, pageSize: 10, objectType: 'entry', objectID: undefined })

// 自测
const simpleList = ref<EntryApi.EntryVO[]>([])
const probe = reactive<{ code?: string; time: string; clientIp: string; token: string; mode: string }>({
  time: `${Math.floor(Date.now() / 1000)}`,
  clientIp: '',
  token: '',
  mode: ''
})
const probeResult = ref<{ type: 'success' | 'error' | 'warning'; title: string; detail: string }>()

/** calledTime 是秒级时间戳（不是 LocalDateTime），这里单独格式化 */
const formatTime = (seconds: number) => formatDate(new Date(seconds * 1000))

const load = async () => {
  loading.value = true
  try {
    const data = await EntryApi.getEntryPage(query)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  query.pageNo = 1
  load()
}

const resetQuery = () => {
  query.name = undefined
  query.code = undefined
  query.freePasswd = undefined
  handleQuery()
}

const loadSimpleList = async () => {
  simpleList.value = await EntryApi.getEntrySimpleList()
  if (!probe.code && simpleList.value.length) {
    probe.code = simpleList.value[0].code
  }
}

const openForm = (type: string, row?: EntryApi.EntryVO) => {
  form.value =
    type === 'edit' && row
      ? { ...row }
      : { name: '', code: '', freePasswd: 0, ip: '', key: '', account: '' }
  allIP.value = form.value.ip === '*'
  formTitle.value = type === 'edit' ? '编辑应用接入' : '新增应用接入'
  formVisible.value = true
}

const onAllIPChange = (value: boolean) => {
  if (value) form.value.ip = '*'
  else if (form.value.ip === '*') form.value.ip = ''
}

const regenKey = async () => {
  form.value.key = await EntryApi.getRandomKey()
}

const submitForm = async () => {
  // Element Plus 的 validate() 校验失败时是 **reject**（带字段错误对象），
  // 直接 await 而不 catch，在「点确定但必填没填」时就会冒一条未捕获的 unhandledrejection ——
  // 页面看起来正常（表单上照样飘红），但浏览器检查会判成 JS 报错（坑位 #53）。
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    if (form.value.id) {
      await EntryApi.updateEntry(form.value)
      message.success('已保存')
    } else {
      await EntryApi.createEntry(form.value)
      message.success('已创建')
    }
    formVisible.value = false
    await Promise.all([load(), loadSimpleList()])
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row: EntryApi.EntryVO) => {
  await message.delConfirm(`确认删除应用「${row.name}」？`)
  await EntryApi.deleteEntry(row.id!)
  message.success('已删除')
  await Promise.all([load(), loadSimpleList()])
}

const openLog = async (row: EntryApi.EntryVO) => {
  logTitle.value = `调用日志 - ${row.name}`
  logQuery.objectID = row.id
  logQuery.pageNo = 1
  logVisible.value = true
  await loadLog()
}

const loadLog = async () => {
  logLoading.value = true
  try {
    const data = await EntryApi.getEntryLogPage(logQuery)
    logList.value = data.list
    logTotal.value = data.total
  } finally {
    logLoading.value = false
  }
}

const onProbeCodeChange = () => {
  probe.token = ''
  probeResult.value = undefined
}

const runSign = async () => {
  const data = await EntryApi.signEntry({ code: probe.code!, time: probe.time })
  probe.token = data.token
  probe.mode = data.mode
  probeResult.value = undefined
}

const runVerify = async () => {
  try {
    const data = await EntryApi.verifyEntry({
      code: probe.code!,
      token: probe.token,
      time: probe.time,
      module: 'user',
      method: 'apilogin',
      url: `/index.php?m=user&f=apilogin&code=${probe.code}`,
      clientIp: probe.clientIp || undefined
    })
    probeResult.value = {
      type: 'success',
      title: `校验通过（${data.tokenMode} 模式）：${data.name} → ${data.account} / ${data.userNickname}`,
      detail: `${data.message}\nuserId=${data.userId}  calledTime=${data.calledTime}  logId=${data.logId}`
    }
    await load()
  } catch (e: any) {
    probeResult.value = {
      type: 'error',
      title: '校验失败（与禅道同样的错误码语义）',
      detail: `${e?.code ?? ''} ${e?.msg ?? e?.message ?? e}`
    }
  }
}

onMounted(async () => {
  await loadSimpleList()
  await load()
})
</script>
