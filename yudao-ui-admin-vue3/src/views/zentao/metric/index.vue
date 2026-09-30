<template>
  <ContentWrap>
    <!-- 概览 + 全局操作 -->
    <div class="mb-12px flex items-center justify-between">
      <div class="flex gap-24px">
        <div class="metric-card">
          <div class="metric-card-num">{{ summary.totalCount }}</div>
          <div class="metric-card-label">内置度量项</div>
        </div>
        <div class="metric-card">
          <div class="metric-card-num">{{ summary.implementedCount }}</div>
          <div class="metric-card-label">已迁移口径</div>
        </div>
        <div class="metric-card">
          <div class="metric-card-num">{{ summary.pendingCount }}</div>
          <div class="metric-card-label">待补口径</div>
        </div>
        <div class="metric-card">
          <div class="metric-card-num">{{ summary.dataCount }}</div>
          <div class="metric-card-label">度量数据条数</div>
        </div>
      </div>
      <div class="text-right">
        <el-button
          type="primary"
          :loading="calcAllLoading"
          @click="handleCalcAll"
          v-hasPermi="['zentao:metric:calc']"
        >
          <Icon icon="ep:refresh" class="mr-5px" /> 全部计算
        </el-button>
        <div class="mt-6px text-12px text-gray-400">
          上次计算：{{ summary.lastCalcTime || '还没算过' }}
        </div>
      </div>
    </div>
  </ContentWrap>

  <el-row :gutter="12">
    <!-- 左：度量项列表 -->
    <el-col :span="9">
      <ContentWrap>
        <el-form inline class="-mb-15px">
          <el-form-item label="目的">
            <el-select v-model="query.purpose" clearable class="!w-130px" @change="loadMetrics">
              <el-option v-for="o in dict.purposeList" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="范围">
            <el-select v-model="query.scope" clearable class="!w-130px" @change="loadMetrics">
              <el-option v-for="o in dict.scopeList" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="query.onlyImplemented" @change="loadMetrics">只看已迁移口径</el-checkbox>
          </el-form-item>
        </el-form>

        <el-table
          v-loading="loading"
          :data="metricList"
          height="560"
          highlight-current-row
          @current-change="handleMetricChange"
        >
          <el-table-column label="度量项" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <div>{{ row.alias || row.name }}</div>
              <div class="text-12px text-gray-400">{{ row.code }}</div>
            </template>
          </el-table-column>
          <el-table-column label="范围" align="center" width="80">
            <template #default="{ row }">{{ row.scopeName }}</template>
          </el-table-column>
          <el-table-column label="口径" align="center" width="90">
            <template #default="{ row }">
              <el-tag :type="row.implemented ? 'success' : 'info'" size="small">
                {{ row.implemented ? '已迁移' : '待补' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </ContentWrap>
    </el-col>

    <!-- 右：定义 + 数据 -->
    <el-col :span="15">
      <ContentWrap>
        <div v-if="!current" class="py-40px text-center text-gray-400">先选左边的一个度量项</div>
        <template v-else>
          <div class="mb-10px flex items-center justify-between">
            <span class="font-bold">
              {{ current.name }}
              <el-tag size="small" class="ml-6px">{{ current.unitName }}</el-tag>
              <el-tag size="small" type="info" class="ml-4px">{{ current.dateTypeName }}</el-tag>
            </span>
            <el-button
              type="primary"
              plain
              :disabled="!current.implemented"
              :loading="calcLoading"
              @click="handleCalc"
              v-hasPermi="['zentao:metric:calc']"
            >
              <Icon icon="ep:refresh" class="mr-5px" /> 计算
            </el-button>
          </div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="目的">{{ current.purposeName }}</el-descriptions-item>
            <el-descriptions-item label="对象">{{ current.object }}</el-descriptions-item>
            <el-descriptions-item label="口径" :span="2">{{ current.definition || '-' }}</el-descriptions-item>
            <el-descriptions-item label="上次计算" :span="2">
              {{ current.lastCalcRows || 0 }} 条 / {{ current.lastCalcTime || '未计算' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="!current.implemented" label="提示" :span="2">
              <span class="text-red-500">
                该口径尚未迁移（禅道用 calc 类实现，见 README 3.34），计算会明确报错而不会算错
              </span>
            </el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">
            度量数据（{{ dataTotal }} 条）
            <span class="ml-8px text-12px text-gray-400">
              快照型按「今天算的那一份」显示；周期型按 年/月/周/日 展示
            </span>
          </el-divider>
          <el-table v-loading="dataLoading" :data="dataRows" size="small">
            <el-table-column label="对象" prop="scopeObjectName" min-width="160" show-overflow-tooltip />
            <el-table-column label="期间" prop="period" width="160" />
            <el-table-column label="值" prop="value" align="right" width="120">
              <template #default="{ row }">
                <span class="font-bold">{{ row.value }}</span>
                <span class="ml-4px text-12px text-gray-400">{{ current.unitName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="计算方式" align="center" width="100">
              <template #default="{ row }">{{ row.calcType === 'cron' ? '定时' : '人工' }}</template>
            </el-table-column>
            <el-table-column label="计算人" prop="calculatedBy" align="center" width="100" />
            <el-table-column label="计算时间" prop="date" align="center" width="170" />
          </el-table>
          <Pagination
            :total="dataTotal"
            v-model:page="dataQuery.pageNo"
            v-model:limit="dataQuery.pageSize"
            @pagination="loadData"
          />
        </template>
      </ContentWrap>
    </el-col>
  </el-row>
</template>

<script lang="ts" setup>
import * as MetricApi from '@/api/zentao/metric'

defineOptions({ name: 'ZentaoMetric' })

const message = useMessage()

const loading = ref(false)
const metricList = ref<MetricApi.MetricVO[]>([])
const current = ref<MetricApi.MetricVO>()
const dict = ref<Record<string, Array<{ value: string; label: string }>>>({})
const summary = ref({ totalCount: 0, implementedCount: 0, pendingCount: 0, dataCount: 0, lastCalcTime: '' })
const query = reactive({
  purpose: undefined as string | undefined,
  scope: undefined as string | undefined,
  onlyImplemented: false
})

const dataLoading = ref(false)
const dataRows = ref<MetricApi.MetricDataRow[]>([])
const dataTotal = ref(0)
const dataQuery = reactive({ pageNo: 1, pageSize: 10 })

const calcLoading = ref(false)
const calcAllLoading = ref(false)

const loadSummary = async () => {
  summary.value = (await MetricApi.getMetricSummary()) as any
}

const loadMetrics = async () => {
  loading.value = true
  try {
    const page = await MetricApi.getMetricPage({
      pageNo: 1,
      pageSize: 200,
      purpose: query.purpose,
      scope: query.scope,
      onlyImplemented: query.onlyImplemented || undefined
    } as any)
    metricList.value = page.list
    // 选中的度量项不在新列表里就清掉（避免右侧显示错的对象）
    if (current.value && !metricList.value.some((m) => m.code === current.value?.code)) {
      current.value = undefined
      dataRows.value = []
      dataTotal.value = 0
    }
  } finally {
    loading.value = false
  }
}

const handleMetricChange = async (row: MetricApi.MetricVO | null) => {
  if (!row) return
  current.value = row
  dataQuery.pageNo = 1
  await loadData()
}

const loadData = async () => {
  if (!current.value?.code) return
  dataLoading.value = true
  try {
    const data = await MetricApi.getMetricData({
      code: current.value.code,
      pageNo: dataQuery.pageNo,
      pageSize: dataQuery.pageSize
    } as any)
    dataRows.value = data.rows
    dataTotal.value = data.total
  } finally {
    dataLoading.value = false
  }
}

const handleCalc = async () => {
  if (!current.value?.code) return
  calcLoading.value = true
  try {
    const result = await MetricApi.calcMetric(current.value.code)
    message.success(`计算完成：${result.recordCount} 条（周期 ${result.cycle}）`)
    await loadSummary()
    current.value = await MetricApi.getMetric(current.value.code)
    await loadMetrics2()
    await loadData()
  } finally {
    calcLoading.value = false
  }
}

/** 只刷新列表里的这一行（保留选中） */
const loadMetrics2 = async () => {
  const page = await MetricApi.getMetricPage({
    pageNo: 1,
    pageSize: 200,
    purpose: query.purpose,
    scope: query.scope,
    onlyImplemented: query.onlyImplemented || undefined
  } as any)
  metricList.value = page.list
}

const handleCalcAll = async () => {
  calcAllLoading.value = true
  try {
    const results = await MetricApi.calcAllMetric()
    const total = results.reduce((sum, r) => sum + (r.recordCount || 0), 0)
    message.success(`批量计算完成：${results.length} 个口径，共 ${total} 条数据`)
    await loadSummary()
    await loadMetrics2()
    if (current.value) await handleMetricRefreshData()
  } finally {
    calcAllLoading.value = false
  }
}

const handleMetricRefreshData = async () => {
  if (!current.value?.code) return
  current.value = await MetricApi.getMetric(current.value.code)
  await loadData()
}

onMounted(async () => {
  dict.value = await MetricApi.getMetricDict()
  await loadSummary()
  await loadMetrics()
})
</script>

<style scoped>
.metric-card {
  min-width: 110px;
}
.metric-card-num {
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
}
.metric-card-label {
  font-size: 12px;
  color: #6b7280;
}
</style>
