<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="测试单：测试链闭环的地方"
      description="用例排进测试单 → 逐条执行 → 结果回写三处：执行历史（zt_testresult）、测试单里的记录（zt_testrun）、以及用例自身的「最近执行结果」。用例级结果由步骤结果算出：默认通过，遇到非通过/忽略的就以它为准，遇到失败直接结束（失败优先，不是多数派）。"
    />

    <el-form class="-mb-15px" :inline="true" label-width="86px">
      <el-form-item label="所属产品">
        <el-select v-model="queryParams.product" class="!w-180px" filterable placeholder="全部产品" @change="handleQuery">
          <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" class="!w-140px" clearable placeholder="全部" @change="handleQuery">
          <el-option v-for="o in TEST_TASK_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="queryParams.type" class="!w-140px" clearable placeholder="全部" @change="handleQuery">
          <el-option v-for="o in TEST_TASK_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入名称关键词"
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:testtask:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新建测试单
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" empty-text="没有符合条件的测试单">
      <el-table-column label="名称" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="openRuns(row)">{{ row.name }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="构建" prop="buildName" width="130" show-overflow-tooltip />
      <el-table-column label="类型" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ typeNames(row.type) }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="{ row }">
          <el-tag :type="tagOf(TEST_TASK_STATUS_OPTIONS, row.status) as any">{{ row.statusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="用例进度" align="center" width="200">
        <template #default="{ row }">
          <span class="text-gray-500">
            {{ row.runCount }}/{{ row.caseCount }}
          </span>
          <el-tag v-if="row.passCount" type="success" size="small" class="ml-5px">通过 {{ row.passCount }}</el-tag>
          <el-tag v-if="row.failCount" type="danger" size="small" class="ml-5px">失败 {{ row.failCount }}</el-tag>
          <el-tag v-if="row.blockedCount" type="warning" size="small" class="ml-5px">阻塞 {{ row.blockedCount }}</el-tag>
          <el-tag v-if="row.unexecutedCount" type="info" size="small" class="ml-5px">未执行 {{ row.unexecutedCount }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="负责人" align="center" prop="owner" width="100" />
      <el-table-column label="计划起止" align="center" width="200">
        <template #default="{ row }">{{ row.begin || '-' }} ~ {{ row.end || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="290" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openRuns(row)">用例</el-button>
          <el-button
            v-if="row.status === 'wait' || row.status === 'blocked'"
            link
            type="success"
            @click="handleStart(row)"
            v-hasPermi="['zentao:testtask:update']"
          >
            开始
          </el-button>
          <el-button
            v-if="row.status === 'doing'"
            link
            type="warning"
            @click="handleBlock(row)"
            v-hasPermi="['zentao:testtask:update']"
          >
            阻塞
          </el-button>
          <el-button
            v-if="row.status === 'doing' || row.status === 'blocked'"
            link
            type="success"
            @click="openClose(row)"
            v-hasPermi="['zentao:testtask:update']"
          >
            关闭
          </el-button>
          <el-button
            v-if="row.status === 'done' || row.status === 'blocked'"
            link
            type="primary"
            @click="handleActivate(row)"
            v-hasPermi="['zentao:testtask:update']"
          >
            激活
          </el-button>
          <el-button link type="primary" @click="openForm('edit', row)" v-hasPermi="['zentao:testtask:update']">
            编辑
          </el-button>
          <el-button link type="danger" @click="handleDelete(row)" v-hasPermi="['zentao:testtask:delete']">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 新建/编辑 -->
  <el-dialog v-model="formVisible" :title="form.id ? '编辑测试单' : '新建测试单'" width="640px" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="所属产品" prop="product">
        <el-select v-model="form.product" class="w-full" filterable>
          <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="如：V1.0 冒烟测试" maxlength="255" />
      </el-form-item>
      <el-form-item label="所属构建">
        <el-select v-model="form.build" class="w-full" clearable filterable placeholder="不关联构建">
          <el-option v-for="b in buildList" :key="b.id" :label="b.name" :value="b.id!" />
        </el-select>
        <div class="text-12px text-gray-400">测的是哪个包；关联后列表里能直接看到构建名</div>
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="类型">
            <el-select v-model="form.type" class="w-full" multiple collapse-tags placeholder="可多选">
              <el-option v-for="o in TEST_TASK_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="负责人">
            <el-input v-model="form.owner" placeholder="缺省为当前账号" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="优先级">
            <el-select v-model="form.pri" class="w-full">
              <el-option v-for="i in 4" :key="i" :label="String(i)" :value="i" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="计划开始" prop="begin">
            <el-date-picker v-model="form.begin" type="date" value-format="YYYY-MM-DD" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="计划结束" prop="end">
            <el-date-picker v-model="form.end" type="date" value-format="YYYY-MM-DD" class="w-full" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="描述">
        <el-input v-model="form.desc" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>

  <!-- 关闭测试单 -->
  <el-dialog v-model="closeVisible" title="关闭测试单" width="480px">
    <el-form label-width="100px">
      <el-form-item label="实际完成时间" required>
        <el-date-picker v-model="closeForm.realFinishedDate" type="datetime" class="w-full" />
        <div class="text-12px text-gray-400">不能早于计划开始日期，也不能晚于明天（禅道原规则）</div>
      </el-form-item>
      <el-form-item label="测试总结">
        <el-input v-model="closeForm.report" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="closeVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleClose">确定</el-button>
    </template>
  </el-dialog>

  <!-- 用例编排抽屉 -->
  <el-drawer v-model="runVisible" :title="`测试单：${taskRow?.name ?? ''}`" size="68%">
    <div v-loading="runLoading">
      <div class="mb-10px">
        <el-button type="primary" plain size="small" @click="openLink">
          <Icon icon="ep:plus" class="mr-5px" />排入用例
        </el-button>
        <span class="text-12px text-gray-400 ml-8px">
          「用例已变更」= 排进来之后这条用例又改过步骤（执行时按用例当前版本判定）
        </span>
      </div>
      <el-table :data="runs" border size="small">
        <el-table-column label="用例编号" align="center" prop="caseId" width="90" />
        <el-table-column label="用例标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.caseTitle }}
            <el-tag v-if="row.caseChanged" type="warning" size="small" class="ml-5px">用例已变更</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" align="center" width="90">
          <template #default="{ row }">v{{ row.caseVersion }} → v{{ row.latestCaseVersion }}</template>
        </el-table-column>
        <el-table-column label="步骤数" align="center" prop="stepCount" width="80" />
        <el-table-column label="指派" align="center" prop="assignedTo" width="90" />
        <el-table-column label="最近结果" align="center" width="100">
          <template #default="{ row }">
            <el-tag :type="tagOf(TEST_RESULT_OPTIONS, row.lastRunResult ?? '') as any">
              {{ row.lastRunResultName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行人" align="center" prop="lastRunner" width="90" />
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRun(row)">执行</el-button>
            <el-button link type="primary" @click="openResults(row)">历史</el-button>
            <el-button link type="danger" @click="handleUnlink(row)" v-hasPermi="['zentao:testtask:update']">
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="runs.length === 0" description="还没有排入用例" />
    </div>
  </el-drawer>

  <!-- 排入用例 -->
  <el-dialog v-model="linkVisible" title="排入用例" width="720px" append-to-body>
    <el-input v-model="linkSearch" placeholder="按标题搜索" clearable class="mb-10px" @input="loadLinkable" />
    <el-table
      ref="linkTableRef"
      :data="linkable"
      border
      size="small"
      max-height="380"
      @selection-change="(rows: TestRunVO[]) => (linkSelected = rows)"
    >
      <el-table-column type="selection" width="46" />
      <el-table-column label="编号" prop="caseId" width="90" align="center" />
      <el-table-column label="标题" prop="caseTitle" min-width="240" show-overflow-tooltip />
      <el-table-column label="类型" prop="caseType" width="110" align="center" />
      <el-table-column label="步骤数" prop="stepCount" width="80" align="center" />
    </el-table>
    <el-empty v-if="linkable.length === 0" description="没有可排入的用例（同产品的用例都已经排过）" />
    <template #footer>
      <el-button @click="linkVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="linkSelected.length === 0" @click="handleLink">
        排入 {{ linkSelected.length }} 条
      </el-button>
    </template>
  </el-dialog>

  <!-- 执行用例 -->
  <el-dialog v-model="runDialogVisible" :title="`执行用例：${runRow?.caseTitle ?? ''}`" width="760px" append-to-body>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-10px"
      title="用例级结果由步骤结果算出来"
      description="默认「通过」；某个步骤给了非通过/非忽略的结果，就以它为准；遇到「失败」直接结束。所以是失败优先，而不是多数派。"
    />
    <el-table :data="runSteps" border size="small">
      <el-table-column label="编号" prop="name" width="80" align="center" />
      <el-table-column label="步骤" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag v-if="row.type === 'group'" type="warning" size="small" class="mr-5px">组</el-tag>
          {{ row.desc }}
        </template>
      </el-table-column>
      <el-table-column label="结果" width="300">
        <template #default="{ row, $index }">
          <el-radio-group v-if="row.type !== 'group'" v-model="stepResults[$index]" size="small">
            <el-radio-button v-for="o in STEP_RESULT_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio-button>
          </el-radio-group>
          <span v-else class="text-gray-400">步骤组不判定</span>
        </template>
      </el-table-column>
    </el-table>
    <div class="mt-10px text-13px">
      算出来的用例结果：
      <el-tag :type="tagOf(TEST_RESULT_OPTIONS, computedResult) as any">{{ labelOf(TEST_RESULT_OPTIONS, computedResult) }}</el-tag>
    </div>
    <template #footer>
      <el-button @click="runDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleRun">提交结果</el-button>
    </template>
  </el-dialog>

  <!-- 执行历史 -->
  <el-dialog v-model="resultVisible" title="执行历史" width="680px" append-to-body>
    <el-table :data="results" border size="small">
      <el-table-column label="结果" align="center" width="100">
        <template #default="{ row }">
          <el-tag :type="tagOf(TEST_RESULT_OPTIONS, row.caseResult) as any">{{ row.caseResultName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="用例版本" align="center" prop="version" width="90" />
      <el-table-column label="执行人" align="center" prop="lastRunner" width="100" />
      <el-table-column label="执行时间" align="center" width="180">
        <template #default="{ row }">{{ formatDate(row.date) }}</template>
      </el-table-column>
    </el-table>
    <el-empty v-if="results.length === 0" description="还没有执行过" />
  </el-dialog>
</template>

<script lang="ts" setup>
import * as TestTaskApi from '@/api/zentao/testtask'
import * as ProductApi from '@/api/zentao/product'
import * as BuildApi from '@/api/zentao/build'
import * as CaseApi from '@/api/zentao/testcase'
import { formatDate } from '@/utils/formatTime'
import {
  TEST_TASK_STATUS_OPTIONS,
  TEST_RESULT_OPTIONS,
  STEP_RESULT_OPTIONS,
  TEST_TASK_TYPE_OPTIONS,
  labelOf,
  tagOf,
  typeNames
} from './constants'

defineOptions({ name: 'ZentaoTesttask' })

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const list = ref<TestTaskApi.TestTaskVO[]>([])
const total = ref(0)
const productList = ref<any[]>([])
const buildList = ref<any[]>([])
const formRef = ref()

const queryParams = reactive({
  pageNo: 1,
  pageSize: 20,
  product: undefined as number | undefined,
  status: undefined as string | undefined,
  type: undefined as string | undefined,
  name: ''
})

const getList = async () => {
  loading.value = true
  try {
    const data = await TestTaskApi.getTestTaskPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  queryParams.pageNo = 1
  return getList()
}
const resetQuery = () => {
  queryParams.product = undefined
  queryParams.status = undefined
  queryParams.type = undefined
  queryParams.name = ''
  return handleQuery()
}

// ==================== 新建 / 编辑 ====================
const formVisible = ref(false)
const form = reactive<TestTaskApi.TestTaskVO & { typeList?: string[] }>({
  id: undefined,
  product: undefined,
  name: '',
  build: undefined,
  owner: '',
  pri: 3,
  begin: '',
  end: '',
  desc: ''
})
const typeList = ref<string[]>([])
const rules = {
  product: [{ required: true, message: '请选择所属产品', trigger: 'change' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

const openForm = async (mode: 'create' | 'edit', row?: TestTaskApi.TestTaskVO) => {
  formVisible.value = true
  if (mode === 'edit' && row) {
    Object.assign(form, row)
    typeList.value = (row.type ?? '').split(',').filter(Boolean)
  } else {
    Object.assign(form, {
      id: undefined,
      product: queryParams.product,
      name: '',
      build: undefined,
      owner: '',
      pri: 3,
      begin: '',
      end: '',
      desc: ''
    })
    typeList.value = []
  }
  if (form.product) buildList.value = await BuildApi.getBuildListByProduct(form.product)
}

const handleSubmit = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form, type: typeList.value.join(',') }
    if (form.id) {
      await TestTaskApi.updateTestTask(payload)
      message.success('修改成功')
    } else {
      await TestTaskApi.createTestTask(payload)
      message.success('新建成功')
    }
    formVisible.value = false
    await getList()
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row: TestTaskApi.TestTaskVO) => {
  await message.delConfirm(`确认删除测试单「${row.name}」？排入的用例与执行历史会一起清理`)
  await TestTaskApi.deleteTestTask(row.id!)
  message.success('删除成功')
  await getList()
}

// ==================== 状态流转 ====================
const handleStart = async (row: TestTaskApi.TestTaskVO) => {
  await message.confirm(`确认开始测试单「${row.name}」？`)
  await TestTaskApi.startTestTask(row.id!)
  message.success('已开始')
  await getList()
}
const handleBlock = async (row: TestTaskApi.TestTaskVO) => {
  const { value } = await ElMessageBox.prompt('请输入阻塞原因', '阻塞测试单', { inputValue: '' })
  await TestTaskApi.blockTestTask(row.id!, value)
  message.success('已阻塞')
  await getList()
}
const handleActivate = async (row: TestTaskApi.TestTaskVO) => {
  await message.confirm(`确认激活「${row.name}」？会清空完成时间与测试总结`)
  await TestTaskApi.activateTestTask(row.id!)
  message.success('已激活')
  await getList()
}

const closeVisible = ref(false)
const closeRow = ref<TestTaskApi.TestTaskVO>()
const closeForm = reactive({ realFinishedDate: new Date(), report: '' })
const openClose = (row: TestTaskApi.TestTaskVO) => {
  closeRow.value = row
  closeForm.realFinishedDate = new Date()
  closeForm.report = row.report ?? ''
  closeVisible.value = true
}
const handleClose = async () => {
  saving.value = true
  try {
    const d = closeForm.realFinishedDate as any
    const pad = (n: number) => String(n).padStart(2, '0')
    const text = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`
    await TestTaskApi.closeTestTask(closeRow.value!.id!, { realFinishedDate: text, report: closeForm.report })
    message.success('已关闭')
    closeVisible.value = false
    await getList()
  } finally {
    saving.value = false
  }
}

// ==================== 用例编排 ====================
const runVisible = ref(false)
const runLoading = ref(false)
const taskRow = ref<TestTaskApi.TestTaskVO>()
const runs = ref<TestTaskApi.TestRunVO[]>([])

const loadRuns = async () => {
  runLoading.value = true
  try {
    runs.value = await TestTaskApi.getRunList(taskRow.value!.id!)
  } finally {
    runLoading.value = false
  }
}
const openRuns = async (row: TestTaskApi.TestTaskVO) => {
  taskRow.value = row
  runVisible.value = true
  await loadRuns()
}

const linkVisible = ref(false)
const linkable = ref<TestTaskApi.TestRunVO[]>([])
const linkSelected = ref<TestTaskApi.TestRunVO[]>([])
const linkSearch = ref('')
const openLink = async () => {
  linkVisible.value = true
  linkSearch.value = ''
  linkSelected.value = []
  await loadLinkable()
}
const loadLinkable = async () => {
  linkable.value = await TestTaskApi.getLinkableList({
    taskId: taskRow.value!.id!,
    title: linkSearch.value || undefined
  })
}
const handleLink = async () => {
  saving.value = true
  try {
    const count = await TestTaskApi.linkCase({
      taskId: taskRow.value!.id!,
      caseIds: linkSelected.value.map((r) => r.caseId!)
    })
    message.success(`已排入 ${count} 条用例`)
    linkVisible.value = false
    await Promise.all([loadRuns(), getList()])
  } finally {
    saving.value = false
  }
}
const handleUnlink = async (row: TestTaskApi.TestRunVO) => {
  await message.delConfirm(`确认把用例 #${row.caseId} 从测试单移出？执行历史会一起清理`)
  await TestTaskApi.unlinkCase(row.id!)
  message.success('已移除')
  await Promise.all([loadRuns(), getList()])
}

// ==================== 执行 ====================
const runDialogVisible = ref(false)
const runRow = ref<TestTaskApi.TestRunVO>()
const runSteps = ref<CaseApi.CaseStepVO[]>([])
const stepResults = ref<string[]>([])

const openRun = async (row: TestTaskApi.TestRunVO) => {
  runRow.value = row
  runSteps.value = await CaseApi.getCaseStepList(row.caseId!)
  // 默认全「通过」——执行用例时绝大多数步骤都是过的，省得每步都点
  stepResults.value = runSteps.value.map((s) => (s.type === 'group' ? '' : 'pass'))
  runDialogVisible.value = true
}

/** 本地按同样的规则预览一下算出来的结果，提交后以后端为准 */
const computedResult = computed(() => {
  // 与后端 aggregateResult 同样的规则：默认 pass；非 pass/n-a 以它为准；遇到 fail 立即结束
  let result = 'pass'
  for (let index = 0; index < runSteps.value.length; index++) {
    const step = runSteps.value[index]
    if (step.type === 'group') continue
    const r = stepResults.value[index]
    if (!r || r === 'n/a' || r === 'pass') continue
    result = r
    if (r === 'fail') break
  }
  return result
})

const handleRun = async () => {
  saving.value = true
  try {
    const payload = runSteps.value
      .map((step, index) => ({ step, index }))
      .filter(({ step }) => step.type !== 'group')
      .map(({ step, index }) => ({ id: step.id!, result: stepResults.value[index] }))
    const result = await TestTaskApi.runCase({ runId: runRow.value!.id!, stepResults: payload })
    message.success(`已提交，用例级结果为：${labelOf(TEST_RESULT_OPTIONS, result)}`)
    runDialogVisible.value = false
    await Promise.all([loadRuns(), getList()])
  } finally {
    saving.value = false
  }
}

const resultVisible = ref(false)
const results = ref<TestTaskApi.TestResultVO[]>([])
const openResults = async (row: TestTaskApi.TestRunVO) => {
  resultVisible.value = true
  results.value = await TestTaskApi.getResultList(row.id!)
}

onMounted(async () => {
  productList.value = await ProductApi.getProductSimpleList()
  await getList()
})
</script>
