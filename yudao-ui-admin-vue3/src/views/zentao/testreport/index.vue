<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="测试报告是「汇总」，用例集是「打包」"
      description="两个模块都不产生新的执行数据：报告把一段时间内若干测试单的执行结果汇总出来（用例数/通过/失败都是读的时候现算的，不会过期失真）；用例集把用例打包，排进测试单时一次选完。汇总规则有一条容易忽略：一条用例在区间里跑过多次，只取最后一次结果 —— 否则「先通过后失败」会被算成一次通过加一次失败。"
    />

    <el-tabs v-model="activeTab">
      <!-- ==================== 测试报告 ==================== -->
      <el-tab-pane label="测试报告" name="report">
        <el-form class="-mb-15px" :inline="true" label-width="86px">
          <el-form-item label="所属产品">
            <el-select v-model="reportQuery.product" class="!w-180px" filterable placeholder="全部产品" @change="loadReports">
              <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!" />
            </el-select>
          </el-form-item>
          <el-form-item label="标题">
            <el-input
              v-model="reportQuery.title"
              placeholder="请输入标题关键词"
              clearable
              class="!w-200px"
              @keyup.enter="loadReports"
            />
          </el-form-item>
          <el-form-item>
            <el-button @click="loadReports"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
            <el-button type="primary" plain @click="openReportForm()" v-hasPermi="['zentao:testreport:create']">
              <Icon icon="ep:plus" class="mr-5px" />新建报告
            </el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="reportLoading" :data="reports" class="mt-15px" empty-text="还没有测试报告">
          <el-table-column label="标题" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <el-link type="primary" :underline="false" @click="openReportDetail(row)">{{ row.title }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="汇总的测试单" prop="taskNames" min-width="200" show-overflow-tooltip />
          <el-table-column label="统计区间" align="center" width="200">
            <template #default="{ row }">{{ row.begin }} ~ {{ row.end }}</template>
          </el-table-column>
          <el-table-column label="汇总结果" align="center" width="240">
            <template #default="{ row }">
              <span class="text-gray-500">用例 {{ row.caseCount }}，执行 {{ row.resultCount }} 次</span>
              <el-tag v-if="row.passCount" type="success" size="small" class="ml-5px">通过 {{ row.passCount }}</el-tag>
              <el-tag v-if="row.failCount" type="danger" size="small" class="ml-5px">失败 {{ row.failCount }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="负责人" align="center" prop="owner" width="100" />
          <el-table-column label="操作" align="center" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openReportDetail(row)">查看</el-button>
              <el-button link type="primary" @click="openReportForm(row)" v-hasPermi="['zentao:testreport:update']">
                编辑
              </el-button>
              <el-button link type="danger" @click="handleDeleteReport(row)" v-hasPermi="['zentao:testreport:delete']">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          :total="reportTotal"
          v-model:page="reportQuery.pageNo"
          v-model:limit="reportQuery.pageSize"
          @pagination="loadReports"
        />
      </el-tab-pane>

      <!-- ==================== 用例集 ==================== -->
      <el-tab-pane label="用例集" name="suite">
        <el-form class="-mb-15px" :inline="true" label-width="86px">
          <el-form-item label="所属产品">
            <el-select v-model="suiteQuery.product" class="!w-180px" filterable placeholder="全部产品" @change="loadSuites">
              <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!" />
            </el-select>
          </el-form-item>
          <el-form-item label="名称">
            <el-input v-model="suiteQuery.name" placeholder="请输入名称关键词" clearable class="!w-200px" @keyup.enter="loadSuites" />
          </el-form-item>
          <el-form-item>
            <el-button @click="loadSuites"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
            <el-button type="primary" plain @click="openSuiteForm()" v-hasPermi="['zentao:testsuite:create']">
              <Icon icon="ep:plus" class="mr-5px" />新建用例集
            </el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="suiteLoading" :data="suites" class="mt-15px" empty-text="还没有用例集">
          <el-table-column label="名称" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <el-link type="primary" :underline="false" @click="openSuiteCases(row)">{{ row.name }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="描述" prop="desc" min-width="220" show-overflow-tooltip />
          <el-table-column label="类型" align="center" width="90">
            <template #default="{ row }">{{ row.type === 'private' ? '私有' : '公共' }}</template>
          </el-table-column>
          <el-table-column label="用例数" align="center" prop="caseCount" width="90" />
          <el-table-column label="创建人" align="center" prop="addedBy" width="100" />
          <el-table-column label="操作" align="center" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openSuiteCases(row)">用例</el-button>
              <el-button link type="primary" @click="openSuiteForm(row)" v-hasPermi="['zentao:testsuite:update']">
                编辑
              </el-button>
              <el-button link type="danger" @click="handleDeleteSuite(row)" v-hasPermi="['zentao:testsuite:delete']">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          :total="suiteTotal"
          v-model:page="suiteQuery.pageNo"
          v-model:limit="suiteQuery.pageSize"
          @pagination="loadSuites"
        />
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <!-- 报告详情 -->
  <el-drawer v-model="reportDetailVisible" :title="`测试报告：${reportDetail?.title ?? ''}`" size="62%">
    <div v-loading="reportDetailLoading">
      <el-descriptions :column="2" border class="mb-15px">
        <el-descriptions-item label="汇总的测试单" :span="2">{{ reportDetail?.taskNames }}</el-descriptions-item>
        <el-descriptions-item label="统计区间">{{ reportDetail?.begin }} ~ {{ reportDetail?.end }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ reportDetail?.owner }}</el-descriptions-item>
        <el-descriptions-item label="涉及需求">{{ reportDetail?.stories || '-' }}</el-descriptions-item>
        <el-descriptions-item label="涉及缺陷">{{ reportDetail?.bugs || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用例数 / 已执行">{{ reportDetail?.caseCount }} / {{ reportDetail?.runCaseCount }}</el-descriptions-item>
        <el-descriptions-item label="执行次数">{{ reportDetail?.resultCount }}</el-descriptions-item>
        <el-descriptions-item label="结论" :span="2">{{ reportDetail?.report || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="mb-10px">
        <el-tag type="success">通过 {{ reportDetail?.passCount ?? 0 }}</el-tag>
        <el-tag type="danger" class="ml-5px">失败 {{ reportDetail?.failCount ?? 0 }}</el-tag>
        <span class="text-12px text-gray-400 ml-8px">
          每条用例在区间里跑过多次时只取最后一次结果
        </span>
      </div>

      <el-table :data="reportDetail?.caseSummaries ?? []" border size="small">
        <el-table-column label="用例编号" align="center" prop="caseId" width="90" />
        <el-table-column label="标题" prop="caseTitle" min-width="200" show-overflow-tooltip />
        <el-table-column label="执行次数" align="center" prop="runCount" width="90" />
        <el-table-column label="最后结果" align="center" width="100">
          <template #default="{ row }">
            <el-tag :type="row.lastResult === 'pass' ? 'success' : 'danger'">{{ row.lastResultName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行人" align="center" prop="lastRunner" width="100" />
        <el-table-column label="最后执行时间" align="center" width="170">
          <template #default="{ row }">{{ formatDate(row.lastRunDate) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </el-drawer>

  <!-- 报告表单 -->
  <el-dialog v-model="reportFormVisible" :title="reportForm.id ? '编辑报告' : '新建报告'" width="680px" append-to-body>
    <el-form ref="reportFormRef" :model="reportForm" :rules="reportRules" label-width="110px">
      <el-form-item label="所属产品" prop="product">
        <el-select v-model="reportForm.product" class="w-full" filterable @change="onReportProductChange">
          <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="汇总的测试单" prop="tasks">
        <el-select v-model="taskIds" class="w-full" multiple filterable placeholder="可多选" @change="preview">
          <el-option v-for="t in taskOptions" :key="t.id" :label="t.name" :value="t.id!" />
        </el-select>
        <div class="text-12px text-gray-400">只能汇总与本报告同产品的测试单</div>
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="统计开始" prop="begin">
            <el-date-picker v-model="reportForm.begin" type="date" value-format="YYYY-MM-DD" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="统计结束" prop="end">
            <el-date-picker v-model="reportForm.end" type="date" value-format="YYYY-MM-DD" class="w-full" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="标题" prop="title">
        <el-input v-model="reportForm.title" placeholder="如：V1.0 测试报告" />
      </el-form-item>
      <el-form-item label="负责人" prop="owner">
        <el-input v-model="reportForm.owner" placeholder="缺省为当前账号" />
      </el-form-item>
      <el-form-item label="结论">
        <el-input v-model="reportForm.report" type="textarea" :rows="3" />
      </el-form-item>
      <el-alert v-if="previewResult" type="success" :closable="false" show-icon>
        <template #title>
          汇总预览：用例 {{ previewResult.caseCount }}，已执行 {{ previewResult.runCaseCount }}，
          执行 {{ previewResult.resultCount }} 次，通过 {{ previewResult.passCount }}，失败 {{ previewResult.failCount }}
        </template>
      </el-alert>
    </el-form>
    <template #footer>
      <el-button @click="reportFormVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmitReport">确定</el-button>
    </template>
  </el-dialog>

  <!-- 用例集表单 -->
  <el-dialog v-model="suiteFormVisible" :title="suiteForm.id ? '编辑用例集' : '新建用例集'" width="520px" append-to-body>
    <el-form ref="suiteFormRef" :model="suiteForm" :rules="suiteRules" label-width="90px">
      <el-form-item label="所属产品" prop="product">
        <el-select v-model="suiteForm.product" class="w-full" filterable>
          <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="suiteForm.name" placeholder="如：冒烟用例集" />
      </el-form-item>
      <el-form-item label="类型">
        <el-radio-group v-model="suiteForm.type">
          <el-radio-button value="public">公共</el-radio-button>
          <el-radio-button value="private">私有</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="suiteForm.desc" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="suiteFormVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmitSuite">确定</el-button>
    </template>
  </el-dialog>

  <!-- 用例集里的用例 -->
  <el-drawer v-model="suiteCaseVisible" :title="`用例集：${suiteRow?.name ?? ''}`" size="62%">
    <div v-loading="suiteCaseLoading">
      <div class="mb-10px">
        <el-button type="primary" plain size="small" @click="openLinkCases">
          <Icon icon="ep:plus" class="mr-5px" />加入用例
        </el-button>
      </div>
      <el-table :data="suiteCases" border size="small">
        <el-table-column label="编号" align="center" prop="id" width="90" />
        <el-table-column label="标题" prop="title" min-width="240" show-overflow-tooltip />
        <el-table-column label="类型" prop="typeName" width="110" align="center" />
        <el-table-column label="版本" align="center" width="80">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="100">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleUnlinkCase(row)" v-hasPermi="['zentao:testsuite:update']">
              移出
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="suiteCases.length === 0" description="集合里还没有用例" />
    </div>
  </el-drawer>

  <!-- 加入用例 -->
  <el-dialog v-model="linkCaseVisible" title="加入用例" width="720px" append-to-body>
    <el-input v-model="linkSearch" placeholder="按标题搜索" clearable class="mb-10px" @input="loadLinkableCases" />
    <el-table :data="linkableCases" border size="small" max-height="360" @selection-change="(rows: any[]) => (linkSelected = rows)">
      <el-table-column type="selection" width="46" />
      <el-table-column label="编号" prop="id" width="90" align="center" />
      <el-table-column label="标题" prop="title" min-width="260" show-overflow-tooltip />
      <el-table-column label="类型" prop="typeName" width="110" align="center" />
    </el-table>
    <el-empty v-if="linkableCases.length === 0" description="没有可加入的用例（同产品的用例都已经加过）" />
    <template #footer>
      <el-button @click="linkCaseVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="linkSelected.length === 0" @click="handleLinkCases">
        加入 {{ linkSelected.length }} 条
      </el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as ReportApi from '@/api/zentao/testreport'
import * as ProductApi from '@/api/zentao/product'
import * as TestTaskApi from '@/api/zentao/testtask'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'ZentaoTestreport' })

const message = useMessage()
const activeTab = ref('report')
const saving = ref(false)
const productList = ref<any[]>([])

// ==================== 报告 ====================
const reportLoading = ref(false)
const reports = ref<ReportApi.TestReportVO[]>([])
const reportTotal = ref(0)
const reportQuery = reactive({
  pageNo: 1,
  pageSize: 20,
  product: undefined as number | undefined,
  title: ''
})

const loadReports = async () => {
  reportLoading.value = true
  try {
    const data = await ReportApi.getReportPage(reportQuery)
    reports.value = data.list
    reportTotal.value = data.total
  } finally {
    reportLoading.value = false
  }
}

const reportDetailVisible = ref(false)
const reportDetailLoading = ref(false)
const reportDetail = ref<ReportApi.TestReportVO>()
const openReportDetail = async (row: ReportApi.TestReportVO) => {
  reportDetailVisible.value = true
  reportDetailLoading.value = true
  try {
    reportDetail.value = await ReportApi.getReport(row.id!)
  } finally {
    reportDetailLoading.value = false
  }
}

const reportFormVisible = ref(false)
const reportFormRef = ref()
const taskOptions = ref<any[]>([])
const taskIds = ref<number[]>([])
const previewResult = ref<ReportApi.TestReportVO>()
const reportForm = reactive<ReportApi.TestReportVO>({
  id: undefined,
  product: undefined,
  title: '',
  begin: '',
  end: '',
  owner: 'admin',
  report: ''
})
const reportRules = {
  product: [{ required: true, message: '请选择所属产品', trigger: 'change' }],
  tasks: [{ required: true, message: '请选择要汇总的测试单', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  begin: [{ required: true, message: '请选择统计开始日期', trigger: 'change' }],
  end: [{ required: true, message: '请选择统计结束日期', trigger: 'change' }],
  owner: [{ required: true, message: '请输入负责人', trigger: 'blur' }]
}

const onReportProductChange = async (product: number) => {
  taskIds.value = []
  previewResult.value = undefined
  // 只能汇总同产品的测试单，所以下拉按产品过滤
  taskOptions.value = product ? await TestTaskApi.getTestTaskSimpleList(product) : []
}

/** 建报告前先预览一下数字，避免建完才发现选错了测试单 */
const preview = async () => {
  reportForm.tasks = taskIds.value.join(',')
  previewResult.value = undefined
  if (!reportForm.product || !taskIds.value.length) return
  previewResult.value = await ReportApi.previewReport({
    product: reportForm.product,
    tasks: reportForm.tasks,
    begin: reportForm.begin || undefined,
    end: reportForm.end || undefined
  })
}

const openReportForm = async (row?: ReportApi.TestReportVO) => {
  reportFormVisible.value = true
  previewResult.value = undefined
  if (row) {
    Object.assign(reportForm, row)
    taskIds.value = (row.tasks ?? '').split(',').filter(Boolean).map(Number)
  } else {
    Object.assign(reportForm, {
      id: undefined,
      product: reportQuery.product,
      title: '',
      begin: '',
      end: '',
      owner: 'admin',
      report: ''
    })
    taskIds.value = []
  }
  if (reportForm.product) {
    taskOptions.value = await TestTaskApi.getTestTaskSimpleList(reportForm.product)
  }
}

const handleSubmitReport = async () => {
  await reportFormRef.value.validate()
  reportForm.tasks = taskIds.value.join(',')
  saving.value = true
  try {
    if (reportForm.id) {
      await ReportApi.updateReport(reportForm)
      message.success('修改成功')
    } else {
      await ReportApi.createReport(reportForm)
      message.success('新建成功')
    }
    reportFormVisible.value = false
    await loadReports()
  } finally {
    saving.value = false
  }
}

const handleDeleteReport = async (row: ReportApi.TestReportVO) => {
  await message.delConfirm(`确认删除报告「${row.title}」？`)
  await ReportApi.deleteReport(row.id!)
  message.success('删除成功')
  await loadReports()
}

// ==================== 用例集 ====================
const suiteLoading = ref(false)
const suites = ref<ReportApi.TestSuiteVO[]>([])
const suiteTotal = ref(0)
const suiteQuery = reactive({
  pageNo: 1,
  pageSize: 20,
  product: undefined as number | undefined,
  name: ''
})

const loadSuites = async () => {
  suiteLoading.value = true
  try {
    const data = await ReportApi.getSuitePage(suiteQuery)
    suites.value = data.list
    suiteTotal.value = data.total
  } finally {
    suiteLoading.value = false
  }
}

const suiteFormVisible = ref(false)
const suiteFormRef = ref()
const suiteForm = reactive<ReportApi.TestSuiteVO>({
  id: undefined,
  product: undefined,
  name: '',
  type: 'public',
  desc: ''
})
const suiteRules = {
  product: [{ required: true, message: '请选择所属产品', trigger: 'change' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

const openSuiteForm = (row?: ReportApi.TestSuiteVO) => {
  suiteFormVisible.value = true
  if (row) {
    Object.assign(suiteForm, row)
  } else {
    Object.assign(suiteForm, { id: undefined, product: suiteQuery.product, name: '', type: 'public', desc: '' })
  }
}

const handleSubmitSuite = async () => {
  await suiteFormRef.value.validate()
  saving.value = true
  try {
    if (suiteForm.id) {
      await ReportApi.updateSuite(suiteForm)
      message.success('修改成功')
    } else {
      await ReportApi.createSuite(suiteForm)
      message.success('新建成功')
    }
    suiteFormVisible.value = false
    await loadSuites()
  } finally {
    saving.value = false
  }
}

const handleDeleteSuite = async (row: ReportApi.TestSuiteVO) => {
  await message.delConfirm(`确认删除用例集「${row.name}」？集合里还有用例时会被拒绝`)
  await ReportApi.deleteSuite(row.id!)
  message.success('删除成功')
  await loadSuites()
}

const suiteCaseVisible = ref(false)
const suiteCaseLoading = ref(false)
const suiteRow = ref<ReportApi.TestSuiteVO>()
const suiteCases = ref<any[]>([])
const openSuiteCases = async (row: ReportApi.TestSuiteVO) => {
  suiteRow.value = row
  suiteCaseVisible.value = true
  await loadSuiteCases()
}
const loadSuiteCases = async () => {
  suiteCaseLoading.value = true
  try {
    suiteCases.value = await ReportApi.getSuiteCaseList(suiteRow.value!.id!)
  } finally {
    suiteCaseLoading.value = false
  }
}

const linkCaseVisible = ref(false)
const linkSearch = ref('')
const linkableCases = ref<any[]>([])
const linkSelected = ref<any[]>([])
const openLinkCases = async () => {
  linkCaseVisible.value = true
  linkSearch.value = ''
  linkSelected.value = []
  await loadLinkableCases()
}
const loadLinkableCases = async () => {
  linkableCases.value = await ReportApi.getSuiteUnlinkedCaseList({
    suiteId: suiteRow.value!.id!,
    title: linkSearch.value || undefined
  })
}
const handleLinkCases = async () => {
  saving.value = true
  try {
    const count = await ReportApi.linkSuiteCase({
      suiteId: suiteRow.value!.id!,
      caseIds: linkSelected.value.map((c) => c.id)
    })
    message.success(`已加入 ${count} 条用例`)
    linkCaseVisible.value = false
    await Promise.all([loadSuiteCases(), loadSuites()])
  } finally {
    saving.value = false
  }
}
const handleUnlinkCase = async (row: any) => {
  await ReportApi.unlinkSuiteCase(suiteRow.value!.id!, row.id)
  message.success('已移出')
  await Promise.all([loadSuiteCases(), loadSuites()])
}

onMounted(async () => {
  productList.value = await ProductApi.getProductSimpleList()
  await Promise.all([loadReports(), loadSuites()])
})
</script>
