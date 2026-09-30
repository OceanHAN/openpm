<template>
  <el-drawer v-model="visible" :title="`燃尽图：${title}`" size="900px" append-to-body>
    <div v-loading="loading">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb-15px"
        title="燃尽图画的是「每天的快照」，不是实时值"
        description="每天（或手动点「重新计算」）把当天所有任务的 原计划/剩余/已消耗 汇总成一行写进 zt_burn；历史快照不再改，所以任务后来被改、被删，已经画出来的曲线不会跟着变。某天没算过就用前一个有值的日期补上。"
      />

      <el-form :inline="true" class="-mb-15px">
        <el-form-item label="曲线">
          <el-select v-model="burnBy" class="!w-160px" @change="load">
            <el-option label="剩余工时" value="left" />
            <el-option label="原计划工时" value="estimate" />
            <el-option label="已消耗工时" value="consumed" />
            <el-option label="需求规模" value="storyPoint" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-select v-model="type" class="!w-160px" @change="load">
            <el-option label="跳过周末" value="noweekend" />
            <el-option label="含周末" value="weekend" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleCompute" v-hasPermi="['zentao:execution:burn']">
            <Icon icon="ep:refresh" class="mr-5px" /> 重新计算
          </el-button>
        </el-form-item>
      </el-form>

      <div class="mb-10px text-12px text-gray-500">
        区间 {{ data.begin }} ~ {{ data.end }}，共 {{ data.labels?.length || 0 }} 个点<template v-if="data.interval">（每 {{ data.interval + 1 }} 天一个点）</template>；
        理想线从 {{ data.firstValue }} 线性降到计划结束日为 0；快照 {{ data.rows?.length || 0 }} 条
      </div>

      <Echart v-if="chartOption" :options="chartOption" height="340px" />

      <el-table :data="data.rows || []" size="small" class="mt-15px" empty-text="还没有任何快照，点上面的「重新计算」生成今天的">
        <el-table-column label="日期" prop="date" width="120" align="center" />
        <el-table-column label="原计划" prop="estimate" width="100" align="center" />
        <el-table-column label="剩余" prop="left" width="100" align="center" />
        <el-table-column label="已消耗" prop="consumed" width="100" align="center" />
        <el-table-column label="需求规模" prop="storyPoint" align="center" />
      </el-table>
    </div>
  </el-drawer>
</template>

<script lang="ts" setup>
import type { EChartsOption } from 'echarts'
import { Echart } from '@/components/Echart'
import * as ExecutionApi from '@/api/zentao/execution'

defineOptions({ name: 'BurnChart' })

const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const title = ref('')
const executionId = ref<number>()
const burnBy = ref('left')
const type = ref('noweekend')
const data = ref<Partial<ExecutionApi.BurnChartVO>>({})

const load = async () => {
  if (!executionId.value) return
  loading.value = true
  try {
    data.value = await ExecutionApi.getBurnData({
      id: executionId.value,
      type: type.value,
      burnBy: burnBy.value
    })
  } finally {
    loading.value = false
  }
}

/**
 * 打开某个执行的燃尽图
 * @param row {id, name}
 */
const open = async (row: { id?: number; name?: string }) => {
  executionId.value = row.id
  title.value = row.name || `#${row.id}`
  visible.value = true
  await load()
}

const handleCompute = async () => {
  const count = await ExecutionApi.computeBurn(executionId.value)
  message.success(`已写入 ${count} 条快照（今天）`)
  await load()
}

const BURN_LABEL: Record<string, string> = {
  left: '剩余工时',
  estimate: '原计划工时',
  consumed: '已消耗工时',
  storyPoint: '需求规模'
}

// ECharts 的 line 数据允许 null（断点），这里保留 null 让「今天之后」显示为断开
const chartOption = computed<EChartsOption | null>(() => {
  const labels = data.value.labels || []
  if (labels.length === 0) return null
  const series: any[] = [
    {
      name: BURN_LABEL[data.value.burnBy || 'left'] || '实际',
      type: 'line',
      smooth: true,
      showSymbol: false,
      connectNulls: false,
      data: data.value.burnLine || [],
      lineStyle: { width: 3, color: '#0075A9' },
      itemStyle: { color: '#0075A9' }
    },
    {
      name: '理想',
      type: 'line',
      smooth: false,
      showSymbol: false,
      data: data.value.baseLine || [],
      lineStyle: { width: 2, type: 'dashed', color: '#22AC38' },
      itemStyle: { color: '#22AC38' }
    }
  ]
  if (data.value.delayLine) {
    series.push({
      name: '延期段',
      type: 'line',
      smooth: true,
      showSymbol: false,
      connectNulls: false,
      data: data.value.delayLine,
      lineStyle: { width: 3, color: '#CAAC32' },
      itemStyle: { color: '#CAAC32' }
    })
  }
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: series.map((s) => s.name) },
    grid: { left: 50, right: 20, top: 40, bottom: 40 },
    xAxis: { type: 'category', boundaryGap: false, data: labels },
    yAxis: { type: 'value', name: '工时' },
    series
  } as EChartsOption
})

defineExpose({ open })
</script>
