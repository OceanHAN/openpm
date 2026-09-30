<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="工时明细：已消耗是加总，剩余只看最后一条"
      description="一条工时记录 = 谁、哪天、为哪个任务花了多久、这之后还剩多久。任务的已消耗 = 所有工时之和；任务的剩余 = 最后一条工时里声明的值，不是「预计 - 已消耗」。这样估时被修正时，改的是最后一条声明，历史记录不用动。剩余填 0，任务自动变已完成。"
    />

    <el-form class="-mb-15px" :inline="true" label-width="86px">
      <el-form-item label="任务编号">
        <el-input-number v-model="queryParams.taskId" :min="1" :controls="false" placeholder="任务编号"
                         clearable class="!w-140px" />
      </el-form-item>
      <el-form-item label="账号">
        <el-select v-model="queryParams.account" filterable clearable placeholder="全部" class="!w-180px"
                   @change="handleQuery">
          <el-option v-for="u in userList" :key="u.account" :label="`${u.realname || u.account}（${u.account}）`"
                     :value="u.account!" />
        </el-select>
      </el-form-item>
      <el-form-item label="所属项目">
        <el-input-number v-model="queryParams.project" :min="1" :controls="false" placeholder="项目编号"
                         clearable class="!w-140px" />
      </el-form-item>
      <el-form-item label="所属执行">
        <el-select v-model="queryParams.execution" filterable clearable placeholder="全部执行" class="!w-180px"
                   @change="handleQuery">
          <el-option v-for="e in executionList" :key="e.id" :label="e.name" :value="e.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="工作日期">
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期" class="!w-240px" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm()" v-hasPermi="['zentao:effort:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 登记工时
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-row :gutter="15">
      <el-col :span="17">
        <el-table v-loading="loading" :data="list" empty-text="没有符合条件的工时记录">
          <el-table-column label="日期" align="center" prop="date" width="110" />
          <el-table-column label="账号" align="center" prop="account" width="110" />
          <el-table-column label="任务" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="text-gray-400 mr-5px">#{{ row.objectID }}</span>{{ row.taskName || '(任务已删除)' }}
            </template>
          </el-table-column>
          <el-table-column label="工作内容" prop="work" min-width="220" show-overflow-tooltip />
          <el-table-column label="消耗" align="center" prop="consumed" width="80" />
          <el-table-column label="剩余" align="center" prop="left" width="80" />
          <el-table-column label="起止" align="center" width="110">
            <template #default="{ row }">
              {{ row.begin || row.end ? `${row.begin || '?'}~${row.end || '?'}` : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openForm(row)"
                         v-hasPermi="['zentao:effort:update']">修改</el-button>
              <el-button link type="danger" @click="handleDelete(row)"
                         v-hasPermi="['zentao:effort:delete']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize"
                    @pagination="getList" />
      </el-col>
      <el-col :span="7">
        <el-card shadow="never" header="按账号汇总（当前筛选条件）">
          <el-table :data="summary" size="small" empty-text="没有数据">
            <el-table-column label="账号" prop="account" min-width="100" />
            <el-table-column label="任务数" align="center" prop="taskCount" width="70" />
            <el-table-column label="条数" align="center" prop="effortCount" width="60" />
            <el-table-column label="合计消耗" align="center" prop="consumed" width="90" />
          </el-table>
          <div class="mt-10px text-13px text-gray-500">
            合计 <strong>{{ totalConsumed }}</strong> 小时 / {{ summary.length }} 人
          </div>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>

  <EffortForm ref="formRef" @success="refresh" />
</template>

<script lang="ts" setup>
import * as EffortApi from '@/api/zentao/effort'
import * as ExecutionApi from '@/api/zentao/execution'
import * as OrgApi from '@/api/zentao/organization'
import EffortForm from '../components/EffortForm.vue'

defineOptions({ name: 'ZentaoEffort' })

const message = useMessage()
const loading = ref(false)
const list = ref<EffortApi.EffortVO[]>([])
const total = ref(0)
const summary = ref<EffortApi.EffortSummaryVO[]>([])
const executionList = ref<ExecutionApi.ExecutionVO[]>([])
const userList = ref<OrgApi.OrgUserVO[]>([])
const dateRange = ref<string[]>([])
const formRef = ref()

const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  taskId: undefined as number | undefined,
  account: undefined as string | undefined,
  project: undefined as number | undefined,
  execution: undefined as number | undefined
})

/** 日期区间拆成后端要的 date[0]/date[1] */
const buildParams = () => {
  const params: any = { ...queryParams }
  if (dateRange.value && dateRange.value.length === 2) {
    params.date = dateRange.value
  }
  return params
}

const totalConsumed = computed(() =>
  summary.value.reduce((sum, row) => sum + Number(row.consumed || 0), 0)
)

const getList = async () => {
  loading.value = true
  try {
    const params = buildParams()
    const [page, sums] = await Promise.all([
      EffortApi.getEffortPage(params),
      EffortApi.getEffortSummary(params)
    ])
    list.value = page.list
    total.value = page.total
    summary.value = sums
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  return getList()
}

const resetQuery = () => {
  queryParams.taskId = undefined
  queryParams.account = undefined
  queryParams.project = undefined
  queryParams.execution = undefined
  dateRange.value = []
  return handleQuery()
}

const refresh = () => getList()

const openForm = (row?: EffortApi.EffortVO) => {
  formRef.value.open({ id: row?.objectID }, row)
}

const handleDelete = async (row: EffortApi.EffortVO) => {
  await message.delConfirm(`确认删除「${row.date} ${row.work || ''}」这条工时？任务的已消耗/剩余会跟着重算`)
  await EffortApi.deleteEffort(row.id!)
  message.success('工时已删除')
  await getList()
}

onMounted(async () => {
  getList()
  try {
    const page = await ExecutionApi.getExecutionPage({ pageNo: 1, pageSize: 100 })
    executionList.value = page.list
  } catch {
    executionList.value = []
  }
  try {
    userList.value = await OrgApi.getUserList({})
  } catch {
    userList.value = []
  }
})
</script>
