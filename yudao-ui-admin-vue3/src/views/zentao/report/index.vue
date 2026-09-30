<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="报表是「读别人的数据算出来」的模块"
      description="这个页面不产生任何数据：年度数据、每日提醒、产出统计都是从 zt_action / zt_story / zt_task / zt_bug / zt_case / zt_todo / zt_effort / zt_product / zt_project 现算的。禅道唯一那张 zt_report 是旧版自定义报表的壳（v20 起被 BI 取代），本实现不做它 —— 要自定义报表请用「数据视图」。"
    />

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- ==================== 年度数据 ==================== -->
      <el-tab-pane label="年度数据" name="annual">
        <el-form class="-mb-15px" :inline="true" label-width="70px">
          <el-form-item label="年份">
            <el-select v-model="query.year" class="!w-140px" @change="loadAnnual">
              <el-option v-for="y in options.years" :key="y" :label="y" :value="y" />
            </el-select>
          </el-form-item>
          <el-form-item label="部门">
            <el-select v-model="query.dept" clearable placeholder="全公司" class="!w-200px"
                       @change="handleDeptChange">
              <el-option v-for="d in options.depts" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="人员">
            <el-select v-model="query.account" clearable filterable placeholder="所有人" class="!w-200px"
                       @change="loadAnnual">
              <el-option v-for="u in options.users" :key="u.account"
                         :label="`${u.name}（${u.account}）`" :value="u.account" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="loadAnnual"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
            <el-tag type="info" class="ml-5px">{{ modeLabel }}</el-tag>
          </el-form-item>
        </el-form>

        <el-row :gutter="15" class="mb-15px mt-15px">
          <el-col :span="4">
            <el-card shadow="never" class="text-center">
              <div class="text-24px font-bold text-primary">{{ annual.mode === 'user' ? (annual.logins ?? 0) : (annual.users ?? 0) }}</div>
              <div class="text-12px text-gray-500">{{ annual.mode === 'user' ? '登录次数' : '参与人数' }}</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="never" class="text-center">
              <div class="text-24px font-bold">{{ annual.actions ?? 0 }}</div>
              <div class="text-12px text-gray-500">动作数</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="never" class="text-center">
              <div class="text-24px font-bold text-orange-500">{{ annual.todos?.undone ?? 0 }}/{{ annual.todos?.count ?? 0 }}</div>
              <div class="text-12px text-gray-500">未完成 / 待办总数</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="never" class="text-center">
              <div class="text-24px font-bold text-green-600">{{ annual.consumed ?? 0 }}</div>
              <div class="text-12px text-gray-500">本年消耗工时</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="never" class="text-center">
              <div class="text-24px font-bold text-purple-500">{{ annual.contributionCount ?? 0 }}</div>
              <div class="text-12px text-gray-500">贡献数</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="never" class="text-center">
              <div class="text-24px font-bold">
                {{ annual.overview?.story?.replace('共 ', '').split('，')[0] || 0 }}
              </div>
              <div class="text-12px text-gray-500">需求（{{ annual.overview?.story || '-' }}）</div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="15">
          <el-col :span="12">
            <el-card shadow="never" class="mb-15px">
              <template #header><span class="font-bold">贡献分布（{{ annual.year }}）</span></template>
              <el-table :data="contributionRows" size="small" max-height="360">
                <el-table-column label="对象" prop="objectTypeName" width="100" />
                <el-table-column label="动作" prop="actionName" width="90" />
                <el-table-column label="条数" prop="count" width="80" align="center" />
                <el-table-column label="占比" min-width="120">
                  <template #default="{ row }">
                    <el-progress :percentage="percentOf(row.count)" :stroke-width="10" />
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never" class="mb-15px">
              <template #header><span class="font-bold">历年雷达对比（产品 / 执行 / 研发 / 测试）</span></template>
              <el-table :data="radarRows" size="small">
                <el-table-column label="年份" prop="year" width="90" />
                <el-table-column v-for="t in RADAR_TYPES" :key="t.value" :label="t.label" align="center">
                  <template #default="{ row }">{{ row[t.value] }}</template>
                </el-table-column>
              </el-table>
            </el-card>
            <el-card shadow="never">
              <template #header><span class="font-bold">全量状态分布（不限年份）</span></template>
              <div v-for="(item, type) in annual.statusStat" :key="type" class="mb-8px">
                <span class="inline-block w-60px text-gray-500">{{ OBJECT_LABEL[type] || type }}</span>
                <el-tag v-for="(count, status) in item" :key="status" class="mr-5px" size="small">
                  {{ statusLabel(status) }} {{ count }}
                </el-tag>
              </div>
              <el-empty v-if="Object.keys(annual.statusStat || {}).length === 0" description="全量状态分布只在「全公司」视角给出" :image-size="60" />
            </el-card>
          </el-col>
        </el-row>

        <el-card shadow="never" class="mb-15px">
          <template #header><span class="font-bold">月度趋势（每行一个动作，12 列是 12 个月）</span></template>
          <el-tabs v-model="trendTab" type="card">
            <el-tab-pane label="需求" name="story" />
            <el-tab-pane label="任务" name="task" />
            <el-tab-pane label="缺陷" name="bug" />
            <el-tab-pane label="用例" name="case" />
          </el-tabs>
          <el-table :data="trendRows" size="small">
            <el-table-column label="动作" prop="action" width="90" fixed />
            <el-table-column v-for="m in annual.months" :key="m" :label="m.slice(5)" width="60" align="center">
              <template #default="{ row }">{{ row[m] }}</template>
            </el-table-column>
            <el-table-column label="合计" prop="total" width="70" align="center" fixed="right" />
          </el-table>
        </el-card>

        <el-row :gutter="15">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header><span class="font-bold">本年有动静的产品</span></template>
              <el-table :data="annual.productStat || []" size="small" empty-text="本年没有产品产出">
                <el-table-column label="编号" prop="id" width="80" align="center" />
                <el-table-column label="产品" prop="name" min-width="140" show-overflow-tooltip />
                <el-table-column label="计划" prop="plan" width="60" align="center" />
                <el-table-column label="研发需求" prop="story" width="80" align="center" />
                <el-table-column label="用户需求" prop="requirement" width="80" align="center" />
                <el-table-column label="业务需求" prop="epic" width="80" align="center" />
                <el-table-column label="已关闭" prop="closed" width="70" align="center" />
              </el-table>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>
                <span class="font-bold">本年有动静的执行</span>
                <span class="ml-8px text-12px text-gray-400">只统计「多迭代项目下的迭代」（multiple=1）</span>
              </template>
              <el-table :data="annual.executionStat || []" size="small" empty-text="本年没有迭代产出">
                <el-table-column label="编号" prop="id" width="80" align="center" />
                <el-table-column label="执行" prop="name" min-width="140" show-overflow-tooltip />
                <el-table-column label="完成任务" prop="task" width="80" align="center" />
                <el-table-column label="完成需求" prop="story" width="80" align="center" />
                <el-table-column label="解决缺陷" prop="bug" width="80" align="center" />
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- ==================== 每日提醒 ==================== -->
      <el-tab-pane label="每日提醒" name="reminder">
        <el-form class="-mb-15px" :inline="true">
          <el-form-item>
            <el-button @click="loadReminder"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
            <span class="ml-8px text-12px text-gray-500">
              禅道在这里直接给人发提醒邮件；本实现只产出数据（发信交给 yudao 的通知能力）
            </span>
          </el-form-item>
        </el-form>

        <el-empty v-if="reminders.length === 0" description="没有需要提醒的事项" />
        <el-collapse v-else v-model="openedReminders" class="mt-15px">
          <el-collapse-item v-for="r in reminders" :key="r.account" :name="r.account">
            <template #title>
              <strong>{{ r.realname }}</strong>
              <span class="ml-8px text-gray-400">{{ r.account }}</span>
              <el-tag v-if="r.bugs.length" type="danger" size="small" class="ml-8px">缺陷 {{ r.bugs.length }}</el-tag>
              <el-tag v-if="r.tasks.length" type="warning" size="small" class="ml-5px">任务 {{ r.tasks.length }}</el-tag>
              <el-tag v-if="r.todos.length" size="small" class="ml-5px">待办 {{ r.todos.length }}</el-tag>
              <el-tag v-if="r.testTasks.length" type="success" size="small" class="ml-5px">测试单 {{ r.testTasks.length }}</el-tag>
              <el-tag v-if="r.cards.length" type="info" size="small" class="ml-5px">卡片 {{ r.cards.length }}</el-tag>
            </template>
            <el-table v-if="r.bugs.length" :data="r.bugs" size="small" class="mb-10px">
              <el-table-column label="缺陷" prop="title" min-width="240" show-overflow-tooltip />
              <el-table-column label="编号" prop="id" width="80" align="center" />
              <el-table-column label="截止" prop="deadline" width="110" align="center" />
            </el-table>
            <el-table v-if="r.tasks.length" :data="r.tasks" size="small" class="mb-10px">
              <el-table-column label="任务" prop="name" min-width="240" show-overflow-tooltip />
              <el-table-column label="编号" prop="id" width="80" align="center" />
              <el-table-column label="截止" prop="deadline" width="110" align="center" />
            </el-table>
            <el-table v-if="r.todos.length" :data="r.todos" size="small" class="mb-10px">
              <el-table-column label="待办" prop="name" min-width="240" show-overflow-tooltip />
              <el-table-column label="日期" prop="date" width="110" align="center" />
              <el-table-column label="状态" prop="status" width="90" align="center" />
            </el-table>
            <el-table v-if="r.testTasks.length" :data="r.testTasks" size="small" class="mb-10px">
              <el-table-column label="测试单" prop="name" min-width="240" show-overflow-tooltip />
              <el-table-column label="状态" prop="status" width="90" align="center" />
              <el-table-column label="起止" width="200" align="center">
                <template #default="{ row }">{{ row.begin }} ~ {{ row.end }}</template>
              </el-table-column>
            </el-table>
            <el-table v-if="r.cards.length" :data="r.cards" size="small">
              <el-table-column label="看板卡片" prop="name" min-width="240" show-overflow-tooltip />
              <el-table-column label="截止" prop="deadline" width="110" align="center" />
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </el-tab-pane>

      <!-- ==================== 产出统计 ==================== -->
      <el-tab-pane label="产出统计" name="output">
        <el-form class="-mb-15px" :inline="true" label-width="70px">
          <el-form-item label="年份">
            <el-select v-model="query.year" class="!w-140px" @change="loadOutput">
              <el-option v-for="y in options.years" :key="y" :label="y" :value="y" />
            </el-select>
          </el-form-item>
          <el-form-item label="人员">
            <el-select v-model="query.account" clearable filterable placeholder="所有人" class="!w-200px"
                       @change="loadOutput">
              <el-option v-for="u in options.users" :key="u.account"
                         :label="`${u.name}（${u.account}）`" :value="u.account" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="loadOutput"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="outputs" class="mt-15px" empty-text="这一年没有任何产出">
          <el-table-column label="对象" prop="objectTypeName" width="110" />
          <el-table-column label="合计" prop="total" width="80" align="center" />
          <el-table-column label="动作明细" min-width="400">
            <template #default="{ row }">
              <el-tag v-for="a in row.actions" :key="a.code" class="mr-5px" size="small">
                {{ a.name }} {{ a.total }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <el-card shadow="never" class="mt-15px">
          <template #header><span class="font-bold">我参与的项目状态总览</span></template>
          <template v-if="Object.keys(projectStatus).length">
            <el-tag v-for="(count, status) in projectStatus" :key="status" class="mr-5px">
              {{ statusLabel(status) }} {{ count }}
            </el-tag>
          </template>
          <span v-else class="text-gray-400">没有我参与的项目</span>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as ReportApi from '@/api/zentao/report'

defineOptions({ name: 'ZentaoReport' })

const activeTab = ref('annual')
const query = reactive<{ year: string; dept?: number; account?: string }>({ year: '' })
const options = ref<ReportApi.ReportOptionVO>({ years: [], current: '', depts: [], users: [] })
const annual = ref<Partial<ReportApi.AnnualDataVO>>({})
const reminders = ref<ReportApi.ReminderVO[]>([])
const outputs = ref<ReportApi.ReportOutputVO[]>([])
const projectStatus = ref<Record<string, number>>({})
const openedReminders = ref<string[]>([])
const trendTab = ref('story')

const OBJECT_LABEL: Record<string, string> = {
  product: '产品', story: '需求', productplan: '计划', release: '发布',
  project: '项目', execution: '执行', task: '任务', bug: '缺陷',
  case: '用例', testtask: '测试单', doc: '文档'
}
const RADAR_TYPES = [
  { value: 'product', label: '产品' },
  { value: 'execution', label: '执行' },
  { value: 'devel', label: '研发' },
  { value: 'qa', label: '测试' }
]
const STATUS_LABEL: Record<string, string> = {
  draft: '草稿', reviewing: '评审中', active: '激活', closed: '已关闭', changed: '已变更',
  wait: '未开始', doing: '进行中', done: '已完成', pause: '已暂停', cancel: '已取消',
  resolved: '已解决', suspended: '已挂起'
}
const statusLabel = (status: string) => STATUS_LABEL[status] || status

const modeLabel = computed(() => {
  if (query.account) return `个人视角：${query.account}`
  if (query.dept) return `部门视角：${options.value.depts.find((d) => d.id === query.dept)?.name ?? query.dept}`
  return '公司视角：全体员工'
})

// 贡献：摊平成「对象 / 动作 / 条数」三列，占比按本年最大单项算
const contributionRows = computed(() => {
  const rows: { objectType: string; objectTypeName: string; actionName: string; count: number }[] = []
  for (const [objectType, actions] of Object.entries(annual.value.contributions || {})) {
    for (const [actionName, count] of Object.entries(actions)) {
      rows.push({ objectType, objectTypeName: OBJECT_LABEL[objectType] || objectType, actionName, count })
    }
  }
  return rows.sort((a, b) => b.count - a.count)
})
const percentOf = (count: number) => {
  const max = contributionRows.value[0]?.count || 1
  return Math.round((count / max) * 100)
}

// 历年雷达：年份升序
const radarRows = computed(() =>
  Object.entries(annual.value.contributionGroups || {})
    .map(([year, data]) => ({ year, ...data }))
    .sort((a, b) => a.year.localeCompare(b.year))
)

// 月度趋势：当前页签对应的 actionStat（每行一个动作 + 12 个月的条数 + 合计）
const trendRows = computed(() => {
  const source =
    trendTab.value === 'story' ? annual.value.storyStat?.actionStat
      : trendTab.value === 'task' ? annual.value.taskStat?.actionStat
      : trendTab.value === 'bug' ? annual.value.bugStat?.actionStat
      : annual.value.caseStat?.actionStat
  return Object.entries(source || {}).map(([action, months]) => {
    const row: Record<string, any> = { action }
    let total = 0
    for (const [month, count] of Object.entries(months)) {
      row[month] = count
      total += count
    }
    row.total = total
    return row
  })
})

const loadOptions = async () => {
  options.value = await ReportApi.getReportOptions()
  if (!query.year) query.year = options.value.current
}

const loadAnnual = async () => {
  annual.value = await ReportApi.getAnnualData({
    year: query.year,
    dept: query.dept,
    account: query.account || undefined
  })
}

// 部门与人员是互斥视角：选了部门就清掉人员，反之亦然（禅道的 company/dept/user 三选一）
const handleDeptChange = async () => {
  if (query.dept) query.account = undefined
  await loadAnnual()
}

const loadReminder = async () => {
  reminders.value = await ReportApi.getReminderList()
  openedReminders.value = reminders.value.slice(0, 3).map((r) => r.account)
}

const loadOutput = async () => {
  outputs.value = await ReportApi.getReportOutput({ year: query.year, account: query.account || undefined })
  projectStatus.value = await ReportApi.getProjectStatusOverview(query.account || undefined)
}

const handleTabChange = async (name: string) => {
  if (name === 'annual') return loadAnnual()
  if (name === 'reminder') return loadReminder()
  if (name === 'output') return loadOutput()
}

onMounted(async () => {
  await loadOptions()
  await loadAnnual()
})
</script>
