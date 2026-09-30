<template>
  <ContentWrap>
    <el-tabs v-model="activeTab">
      <!-- ==================== 数据视图 ==================== -->
      <el-tab-pane label="数据视图" name="dataview">
        <div class="mb-10px flex items-center justify-between">
          <span class="text-12px text-gray-400">
            数据视图就是一条**只读 SELECT**（只能查 zt_* 表），图表在它上面做「按维度分组 + 聚合指标」。
          </span>
          <el-button type="primary" plain size="small" @click="openDataViewForm()" v-hasPermi="['zentao:bi:create']">
            <Icon icon="ep:plus" class="mr-3px" /> 新建数据视图
          </el-button>
        </div>
        <el-table v-loading="dvLoading" :data="dataViewList">
          <el-table-column label="名称" prop="name" min-width="160" show-overflow-tooltip />
          <el-table-column label="代码" prop="code" min-width="140" />
          <el-table-column label="字段" align="center" width="90">
            <template #default="{ row }">{{ row.fields?.length || 0 }}</template>
          </el-table-column>
          <el-table-column label="被引用" align="center" width="90">
            <template #default="{ row }">{{ row.chartCount || 0 }} 个图表</template>
          </el-table-column>
          <el-table-column label="SQL" prop="sql" min-width="280" show-overflow-tooltip />
          <el-table-column label="操作" align="center" width="210" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openPreview(row)">预览</el-button>
              <el-button link type="primary" @click="openDataViewForm(row)" v-hasPermi="['zentao:bi:update']">
                编辑
              </el-button>
              <el-button link type="danger" @click="removeDataView(row)" v-hasPermi="['zentao:bi:delete']">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 图表 ==================== -->
      <el-tab-pane label="图表" name="chart">
        <el-row :gutter="12">
          <el-col :span="9">
            <div class="mb-10px flex items-center justify-between">
              <span class="font-bold">图表</span>
              <el-button type="primary" plain size="small" @click="openChartForm()" v-hasPermi="['zentao:bi:create']">
                <Icon icon="ep:plus" class="mr-3px" /> 新建图表
              </el-button>
            </div>
            <el-table
              v-loading="chartLoading"
              :data="chartList"
              highlight-current-row
              @current-change="handleChartChange"
            >
              <el-table-column label="名称" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">
                  <div>{{ row.name }}</div>
                  <div class="text-12px text-gray-400">{{ row.typeName }} · v{{ row.version }}</div>
                </template>
              </el-table-column>
              <el-table-column label="操作" align="center" width="130">
                <template #default="{ row }">
                  <el-button link type="primary" @click.stop="openChartForm(row)" v-hasPermi="['zentao:bi:update']">
                    编辑
                  </el-button>
                  <el-button link type="danger" @click.stop="removeChart(row)" v-hasPermi="['zentao:bi:delete']">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-col>

          <el-col :span="15">
            <div v-if="!currentChart" class="py-40px text-center text-gray-400">先选左边的一个图表</div>
            <template v-else>
              <div class="mb-6px flex items-center justify-between">
                <span class="font-bold">
                  {{ currentChart.name }}
                  <el-tag size="small" class="ml-6px">{{ currentChart.typeName }}</el-tag>
                  <el-tag size="small" type="info" class="ml-4px">
                    按 {{ chartData?.dimensionField }} 分组 / {{ chartData?.agg }}
                  </el-tag>
                </span>
                <span class="text-12px text-gray-400">数据视图：{{ currentChart.viewCode || '自带 SQL' }}</span>
              </div>
              <Echart v-if="chartOption" :options="chartOption" height="360px" />
              <el-table :data="chartData?.rows || []" size="small" class="mt-10px">
                <el-table-column label="维度" prop="name" min-width="140" />
                <el-table-column label="值" prop="value" align="right" width="140" />
              </el-table>
              <div class="mt-6px text-12px text-gray-400 break-all">SQL：{{ chartData?.executedSql }}</div>
            </template>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <!-- 数据视图表单 -->
  <Dialog v-model="dvFormVisible" :title="dvFormTitle" width="760">
    <el-form ref="dvFormRef" :model="dvForm" :rules="dvRules" label-width="90px">
      <el-row>
        <el-col :span="12">
          <el-form-item label="名称" prop="name"><el-input v-model="dvForm.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="代码" prop="code">
            <el-input v-model="dvForm.code" placeholder="如 story_data（图表用它引用）" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="SQL" prop="sql">
        <el-input v-model="dvForm.sql" type="textarea" :rows="5" placeholder="SELECT id, status FROM zt_story WHERE deleted = 0" />
      </el-form-item>
      <el-form-item>
        <el-button :loading="tryLoading" @click="handleTryRun">试 跑</el-button>
        <span class="ml-8px text-12px text-gray-400">
          只允许单条 SELECT、只能查 zt_* 表；不填字段时按 SQL 自动解析
        </span>
      </el-form-item>
      <el-form-item v-if="tryResult" label="试跑结果">
        <div class="w-full">
          <div class="mb-4px text-12px text-gray-400">
            列：{{ tryResult.columns.join(', ') }}（{{ tryResult.total }} 行）
          </div>
          <el-table :data="tryResult.rows" size="small" max-height="240">
            <el-table-column
              v-for="col in tryResult.columns"
              :key="col"
              :label="col"
              :prop="col"
              min-width="120"
              show-overflow-tooltip
            />
          </el-table>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="submitDataViewForm">确 定</el-button>
      <el-button @click="dvFormVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <!-- 数据预览 -->
  <Dialog v-model="previewVisible" title="数据预览" width="900">
    <div class="mb-6px text-12px text-gray-400">
      列：{{ preview?.columns.join(', ') }}（{{ preview?.total }} 行） · {{ preview?.executedSql }}
    </div>
    <el-table :data="preview?.rows || []" size="small" max-height="420">
      <el-table-column
        v-for="col in preview?.columns || []"
        :key="col"
        :label="col"
        :prop="col"
        min-width="130"
        show-overflow-tooltip
      />
    </el-table>
  </Dialog>

  <!-- 图表表单 -->
  <Dialog v-model="chartFormVisible" :title="chartFormTitle" width="720">
    <el-form ref="chartFormRef" :model="chartForm" :rules="chartRules" label-width="100px">
      <el-row>
        <el-col :span="12">
          <el-form-item label="名称" prop="name"><el-input v-model="chartForm.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="类型" prop="type">
            <el-select v-model="chartForm.type" class="w-full">
              <el-option v-for="o in dict.chartTypeList" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="数据视图" prop="viewCode">
        <el-select v-model="chartForm.viewCode" class="w-full" placeholder="选一个数据视图（或下面直接写 SQL）">
          <el-option v-for="o in dict.dataViewList" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="!chartForm.viewCode" label="SQL">
        <el-input v-model="chartForm.sql" type="textarea" :rows="3" placeholder="SELECT id, status FROM zt_story WHERE deleted = 0" />
      </el-form-item>
      <el-row>
        <el-col :span="12">
          <el-form-item label="分组维度" prop="dimensionField">
            <el-input v-model="chartForm.dimensionField" placeholder="如 status" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="聚合方式" prop="agg">
            <el-select v-model="chartForm.agg" class="w-full">
              <el-option v-for="o in dict.aggList" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="12">
          <el-form-item label="指标字段">
            <el-input v-model="chartForm.metricField" placeholder="count 可留空，其余必填" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="最多分组">
            <el-input-number v-model="chartForm.limit" :min="1" :max="200" class="w-full" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="排序">
        <el-select v-model="chartForm.sort" class="w-full">
          <el-option v-for="o in dict.sortList" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="submitChartForm">确 定</el-button>
      <el-button @click="chartFormVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import type { EChartsOption } from 'echarts'
import * as BiApi from '@/api/zentao/bi'
import { Echart } from '@/components/Echart'

defineOptions({ name: 'ZentaoBi' })

const message = useMessage()

const activeTab = ref('dataview')
const dict = ref<Record<string, Array<{ value: string; label: string }>>>({})

/** ---------- 数据视图 ---------- */
const dvLoading = ref(false)
const dataViewList = ref<BiApi.DataViewVO[]>([])
const dvFormVisible = ref(false)
const dvFormTitle = ref('新建数据视图')
const dvFormRef = ref()
const tryLoading = ref(false)
const tryResult = ref<BiApi.DataViewPreviewVO>()
const dvForm = reactive<BiApi.DataViewVO>({ id: undefined, name: '', code: '', sql: '' })
const dvRules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  code: [{ required: true, message: '代码不能为空', trigger: 'blur' }],
  sql: [{ required: true, message: 'SQL 不能为空', trigger: 'blur' }]
}

const previewVisible = ref(false)
const preview = ref<BiApi.DataViewPreviewVO>()

const loadDataViews = async () => {
  dvLoading.value = true
  try {
    dataViewList.value = await BiApi.getDataViewList()
  } finally {
    dvLoading.value = false
  }
}

const openDataViewForm = (row?: BiApi.DataViewVO) => {
  dvFormVisible.value = true
  dvFormTitle.value = row ? '编辑数据视图' : '新建数据视图'
  tryResult.value = undefined
  dvForm.id = row?.id
  dvForm.name = row?.name || ''
  dvForm.code = row?.code || ''
  dvForm.sql = row?.sql || ''
}

const handleTryRun = async () => {
  if (!dvForm.sql) {
    message.warning('先写一条 SQL')
    return
  }
  tryLoading.value = true
  try {
    tryResult.value = await BiApi.previewSql(dvForm.sql, 5)
    message.success(`试跑成功：${tryResult.value.total} 行`)
  } catch {
    // 白名单拦下来的 SQL：错误提示由 axios 拦截器统一弹，这里只要别抛出去
    tryResult.value = undefined
  } finally {
    tryLoading.value = false
  }
}

const submitDataViewForm = async () => {
  await dvFormRef.value.validate()
  if (dvForm.id) {
    await BiApi.updateDataView(dvForm)
    message.success('已保存')
  } else {
    await BiApi.createDataView(dvForm)
    message.success('已创建（字段已按 SQL 自动解析）')
  }
  dvFormVisible.value = false
  await loadDataViews()
  dict.value = await BiApi.getBiDict()
}

const openPreview = async (row: BiApi.DataViewVO) => {
  preview.value = await BiApi.previewDataView(row.id!, 20)
  previewVisible.value = true
}

const removeDataView = async (row: BiApi.DataViewVO) => {
  try {
    await message.delConfirm(`确认删除数据视图「${row.name}」？被图表引用时会被拒绝。`)
    await BiApi.deleteDataView(row.id!)
    message.success('已删除')
    await loadDataViews()
  } catch {}
}

/** ---------- 图表 ---------- */
const chartLoading = ref(false)
const chartList = ref<BiApi.ChartVO[]>([])
const currentChart = ref<BiApi.ChartVO>()
const chartData = ref<BiApi.ChartDataVO>()
const chartFormVisible = ref(false)
const chartFormTitle = ref('新建图表')
const chartFormRef = ref()
const chartForm = reactive({
  id: undefined as number | undefined,
  name: '',
  type: 'pie',
  viewCode: '',
  sql: '',
  dimensionField: '',
  metricField: '',
  agg: 'count',
  limit: 10,
  sort: 'value_desc'
})
const chartRules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '类型不能为空', trigger: 'change' }],
  dimensionField: [{ required: true, message: '分组维度不能为空', trigger: 'blur' }],
  agg: [{ required: true, message: '聚合方式不能为空', trigger: 'change' }]
}

const loadCharts = async () => {
  chartLoading.value = true
  try {
    chartList.value = await BiApi.getChartList()
  } finally {
    chartLoading.value = false
  }
}

const handleChartChange = async (row: BiApi.ChartVO | null) => {
  if (!row?.id) return
  currentChart.value = row
  chartData.value = await BiApi.getChartData(row.id)
}

// 显式标成 EChartsOption：对象字面量里的 'pie'/'line' 会被推断成 string，直接传组件会报类型不匹配
const chartOption = computed<EChartsOption | null>(() => {
  const data = chartData.value
  if (!data || !data.rows?.length) return null
  const rows = data.rows.map((r) => ({ name: String(r.name ?? '(空)'), value: Number(r.value) }))
  const name = currentChart.value?.name || ''
  if (currentChart.value?.echartsType === 'pie') {
    return {
      title: { text: name, left: 'center' },
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{ type: 'pie', radius: '60%', data: rows }]
    } as EChartsOption
  }
  if (currentChart.value?.echartsType === 'line') {
    return {
      title: { text: name, left: 'center' },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: rows.map((r) => r.name) },
      yAxis: { type: 'value' },
      series: [{ type: 'line', data: rows.map((r) => r.value) }]
    } as EChartsOption
  }
  return {
    title: { text: name, left: 'center' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: rows.map((r) => r.name) },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: rows.map((r) => r.value) }]
  } as EChartsOption
})

const openChartForm = (row?: BiApi.ChartVO) => {
  chartFormVisible.value = true
  chartFormTitle.value = row ? '编辑图表' : '新建图表'
  chartForm.id = row?.id
  chartForm.name = row?.name || ''
  chartForm.type = row?.type || 'pie'
  chartForm.viewCode = row?.viewCode || dict.value.dataViewList?.[0]?.value || ''
  chartForm.sql = row?.sql || ''
  chartForm.dimensionField = String(row?.settings?.dimensionField || '')
  chartForm.metricField = String(row?.settings?.metricField || '')
  chartForm.agg = String(row?.settings?.agg || 'count')
  chartForm.limit = Number(row?.settings?.limit || 10)
  chartForm.sort = String(row?.settings?.sort || 'value_desc')
}

const submitChartForm = async () => {
  await chartFormRef.value.validate()
  const data: BiApi.ChartVO = {
    id: chartForm.id,
    name: chartForm.name,
    type: chartForm.type,
    viewCode: chartForm.viewCode,
    sql: chartForm.viewCode ? '' : chartForm.sql,
    settings: {
      dimensionField: chartForm.dimensionField,
      metricField: chartForm.metricField,
      agg: chartForm.agg,
      limit: chartForm.limit,
      sort: chartForm.sort
    },
    filters: []
  }
  if (chartForm.id) {
    await BiApi.updateChart(data)
    message.success('已保存（版本 +1）')
  } else {
    const id = await BiApi.createChart(data)
    message.success('已创建')
    chartForm.id = id
  }
  chartFormVisible.value = false
  await loadCharts()
  const created = chartList.value.find((c) => c.id === chartForm.id)
  if (created) await handleChartChange(created)
}

const removeChart = async (row: BiApi.ChartVO) => {
  try {
    await message.delConfirm(`确认删除图表「${row.name}」？`)
    await BiApi.deleteChart(row.id!)
    message.success('已删除')
    if (currentChart.value?.id === row.id) {
      currentChart.value = undefined
      chartData.value = undefined
    }
    await loadCharts()
  } catch {}
}

onMounted(async () => {
  dict.value = await BiApi.getBiDict()
  await loadDataViews()
  await loadCharts()
})
</script>
