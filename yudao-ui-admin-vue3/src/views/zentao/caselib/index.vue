<template>
  <ContentWrap>
    <el-row :gutter="12">
      <!-- 左：用例库列表 -->
      <el-col :span="8">
        <div class="mb-10px flex items-center justify-between">
          <span class="font-bold">用例库</span>
          <span>
            <el-button
              type="primary"
              plain
              size="small"
              @click="openLibForm('create')"
              v-hasPermi="['zentao:caselib:create']"
            >
              <Icon icon="ep:plus" class="mr-3px" /> 新建用例库
            </el-button>
            <el-button size="small" @click="loadLibs(true)"><Icon icon="ep:refresh" /></el-button>
          </span>
        </div>
        <el-table
          v-loading="libLoading"
          :data="libList"
          highlight-current-row
          :current-row-key="activeLibId"
          row-key="id"
          @current-change="handleLibChange"
        >
          <el-table-column label="名称" prop="name" min-width="140" show-overflow-tooltip />
          <el-table-column label="用例数" align="center" width="80" prop="caseCount" />
          <el-table-column label="操作" align="center" width="120">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                @click.stop="openLibForm('update', row)"
                v-hasPermi="['zentao:caselib:update']"
              >
                编辑
              </el-button>
              <el-button
                link
                type="danger"
                @click.stop="handleDeleteLib(row)"
                v-hasPermi="['zentao:caselib:delete']"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="mt-8px text-12px text-gray-400">
          用例库与用例集是同一张表（zt_testsuite）的两类记录：库是 product=0 + type=library，
          库内用例挂在 zt_case 的 lib 字段上。
        </div>
      </el-col>

      <!-- 右：库内用例 -->
      <el-col :span="16">
        <div class="mb-10px flex items-center justify-between">
          <span class="font-bold">
            库内用例
            <el-tag v-if="activeLib" type="info" size="small" class="ml-5px">{{ activeLib.name }}</el-tag>
          </span>
          <span v-if="activeLibId">
            <el-button
              type="primary"
              plain
              size="small"
              :disabled="!activeLibId"
              @click="openCaseForm"
              v-hasPermi="['zentao:caselib:create']"
            >
              <Icon icon="ep:plus" class="mr-3px" /> 新建用例
            </el-button>
            <el-button
              type="success"
              plain
              size="small"
              :disabled="!activeLibId"
              @click="openImport"
              v-hasPermi="['zentao:caselib:create']"
            >
              <Icon icon="ep:download" class="mr-3px" /> 从产品导入
            </el-button>
          </span>
        </div>

        <div v-if="!activeLibId" class="py-40px text-center text-gray-400">
          先选左边的一个用例库
        </div>
        <template v-else>
          <el-form inline class="-mb-15px">
            <el-form-item label="标题">
              <el-input
                v-model="caseQuery.title"
                placeholder="标题关键词"
                clearable
                class="!w-180px"
                @keyup.enter="loadCases"
              />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="caseQuery.status" placeholder="全部" clearable class="!w-140px">
                <el-option v-for="o in CASE_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button @click="loadCases"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="caseLoading" :data="caseList">
            <el-table-column label="编号" align="center" prop="id" width="80" />
            <el-table-column label="标题" prop="title" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ row.title }}</span>
                <!-- 库内用例的来源与「源用例已更新」（与需求/用例的版本冻结同一套路） -->
                <el-tag v-if="row.fromCaseID" type="info" size="small" class="ml-5px">来自 #{{ row.fromCaseID }}</el-tag>
                <el-tag v-if="row.sourceChanged" type="danger" size="small" class="ml-5px">源用例已更新</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="类型" align="center" width="100">
              <template #default="{ row }">{{ labelOf(CASE_TYPE_OPTIONS, row.type) }}</template>
            </el-table-column>
            <el-table-column label="状态" align="center" width="100">
              <template #default="{ row }">
                <el-tag :type="tagOf(CASE_STATUS_OPTIONS, row.status) as any">
                  {{ labelOf(CASE_STATUS_OPTIONS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="优先级" align="center" prop="pri" width="80" />
            <el-table-column label="版本" align="center" width="80">
              <template #default="{ row }">v{{ row.version }}</template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openCaseDetail(row.id)">详情</el-button>
                <el-button
                  link
                  type="danger"
                  @click="handleDeleteCase(row)"
                  v-hasPermi="['zentao:caselib:delete']"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <Pagination
            :total="caseTotal"
            v-model:page="caseQuery.pageNo"
            v-model:limit="caseQuery.pageSize"
            @pagination="loadCases"
          />
        </template>
      </el-col>
    </el-row>
  </ContentWrap>

  <!-- 用例库表单 -->
  <Dialog v-model="libFormVisible" :title="libFormTitle" width="520">
    <el-form ref="libFormRef" v-loading="libFormLoading" :model="libForm" :rules="libRules" label-width="90px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="libForm.name" placeholder="用例库名称（全局唯一）" maxlength="255" />
      </el-form-item>
      <el-form-item label="描述" prop="desc">
        <el-input v-model="libForm.desc" type="textarea" :rows="3" placeholder="这个库放什么用例" />
      </el-form-item>
      <el-form-item label="排序" prop="order">
        <el-input-number v-model="libForm.order" :min="0" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitLibForm" type="primary" :disabled="libFormLoading">确 定</el-button>
      <el-button @click="libFormVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <!-- 库内用例表单（带步骤） -->
  <Dialog v-model="caseFormVisible" :title="caseFormTitle" width="760">
    <el-form ref="caseFormRef" v-loading="caseFormLoading" :model="caseForm" :rules="caseRules" label-width="90px">
      <el-form-item label="标题" prop="title">
        <el-input v-model="caseForm.title" placeholder="用例标题" maxlength="255" />
      </el-form-item>
      <el-row>
        <el-col :span="12">
          <el-form-item label="类型" prop="type">
            <el-select v-model="caseForm.type" class="w-full">
              <el-option v-for="o in CASE_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="优先级" prop="pri">
            <el-select v-model="caseForm.pri" class="w-full">
              <el-option v-for="o in CASE_PRI_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="12">
          <el-form-item label="适用环节">
            <el-select v-model="caseForm.stage" clearable class="w-full">
              <el-option v-for="o in CASE_STAGE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="所属模块">
            <ModuleSelect v-model="caseForm.module" :root="activeLibId" type="caselib" :branch="0" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="前置条件">
        <el-input v-model="caseForm.precondition" placeholder="执行这条用例之前要满足什么" />
      </el-form-item>
      <el-form-item label="步骤">
        <el-table :data="caseForm.steps" size="small" border>
          <el-table-column label="步骤" min-width="200">
            <template #default="{ row }">
              <el-input v-model="row.desc" placeholder="操作步骤" />
            </template>
          </el-table-column>
          <el-table-column label="预期" min-width="200">
            <template #default="{ row }">
              <el-input v-model="row.expect" placeholder="预期结果" />
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="70">
            <template #default="{ $index }">
              <el-button link type="danger" @click="caseForm.steps.splice($index, 1)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button class="mt-8px" size="small" @click="caseForm.steps.push({ desc: '', expect: '' })">
          <Icon icon="ep:plus" class="mr-3px" /> 加一步
        </el-button>
        <div class="text-12px text-gray-400">步骤有变化时用例版本 +1 并打回「待评审」（与用例模块同一套规则）</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitCaseForm" type="primary" :disabled="caseFormLoading">确 定</el-button>
      <el-button @click="caseFormVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <!-- 从产品导入用例 -->
  <Dialog v-model="importVisible" title="从产品导入用例" width="820">
    <el-form inline>
      <el-form-item label="产品">
        <el-select v-model="importQuery.product" class="!w-220px" filterable @change="loadImportCases">
          <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题">
        <el-input v-model="importQuery.title" class="!w-200px" clearable @keyup.enter="loadImportCases" />
      </el-form-item>
      <el-form-item>
        <el-button @click="loadImportCases"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
      </el-form-item>
    </el-form>
    <el-table
      v-loading="importLoading"
      :data="importList"
      @selection-change="(rows: CaseApi.CaseVO[]) => (importChecked = rows)"
    >
      <el-table-column type="selection" width="55" />
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="标题" prop="title" min-width="220" show-overflow-tooltip />
      <el-table-column label="类型" align="center" width="100">
        <template #default="{ row }">{{ labelOf(CASE_TYPE_OPTIONS, row.type) }}</template>
      </el-table-column>
      <el-table-column label="版本" align="center" width="80">
        <template #default="{ row }">v{{ row.version }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="{ row }">{{ labelOf(CASE_STATUS_OPTIONS, row.status) }}</template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="importTotal"
      v-model:page="importQuery.pageNo"
      v-model:limit="importQuery.pageSize"
      @pagination="loadImportCases"
    />
    <div class="mt-8px text-12px text-gray-400">
      导入会把用例与步骤复制进库，并记下来源编号与来源版本；已经导入过的用例不会再出现在这个列表里
      （禅道 testcase/getCanImportCases 按 fromCaseID 判重）。
    </div>
    <template #footer>
      <el-button type="primary" :disabled="importChecked.length === 0" @click="submitImport">
        导入选中的 {{ importChecked.length }} 条
      </el-button>
      <el-button @click="importVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <!-- 用例详情 -->
  <Dialog v-model="detailVisible" title="用例详情" width="720">
    <el-descriptions v-if="detail" :column="2" border>
      <el-descriptions-item label="编号">{{ detail.id }}</el-descriptions-item>
      <el-descriptions-item label="版本">v{{ detail.version }}</el-descriptions-item>
      <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
      <el-descriptions-item label="类型">{{ labelOf(CASE_TYPE_OPTIONS, detail.type) }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ labelOf(CASE_STATUS_OPTIONS, detail.status) }}</el-descriptions-item>
      <el-descriptions-item label="前置条件" :span="2">{{ detail.precondition || '-' }}</el-descriptions-item>
      <el-descriptions-item label="来源" :span="2">
        <span v-if="detail.fromCaseID">
          从产品用例 #{{ detail.fromCaseID }} 导入（导入时版本 v{{ detail.fromCaseVersion }}）
          <el-tag v-if="detail.sourceChanged" type="danger" size="small" class="ml-5px">源用例已更新</el-tag>
        </span>
        <span v-else>库内手工新建</span>
      </el-descriptions-item>
    </el-descriptions>
    <el-table v-if="detail?.steps?.length" :data="detail.steps" size="small" border class="mt-12px">
      <el-table-column label="步骤" prop="name" width="90" />
      <el-table-column label="操作" prop="desc" min-width="200" />
      <el-table-column label="预期" prop="expect" min-width="200" />
    </el-table>
  </Dialog>
</template>

<script lang="ts" setup>
import * as CaseLibApi from '@/api/zentao/caselib'
import * as CaseApi from '@/api/zentao/testcase'
import * as ProductApi from '@/api/zentao/product'
import ModuleSelect from '@/views/zentao/components/ModuleSelect.vue'
import { CASE_TYPE_OPTIONS, CASE_STATUS_OPTIONS, CASE_STAGE_OPTIONS, CASE_PRI_OPTIONS, labelOf, tagOf } from '@/views/zentao/testcase/constants'

defineOptions({ name: 'ZentaoCaselib' })

const message = useMessage()
const { t } = useI18n()

const libLoading = ref(false)
const libList = ref<CaseLibApi.CaseLibVO[]>([])
const activeLibId = ref<number | undefined>(undefined)
const activeLib = computed(() => libList.value.find((l) => l.id === activeLibId.value))

const caseLoading = ref(false)
const caseList = ref<CaseLibApi.CaseLibCaseVO[]>([])
const caseTotal = ref(0)
const caseQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  title: '',
  status: undefined as string | undefined
})

const productList = ref<ProductApi.ProductSimpleVO[]>([])

/** 库列表 */
const loadLibs = async (keepActive = true) => {
  libLoading.value = true
  try {
    const data = await CaseLibApi.getCaseLibPage({ pageNo: 1, pageSize: 100 })
    libList.value = data.list
    if (!keepActive || !libList.value.some((l) => l.id === activeLibId.value)) {
      activeLibId.value = libList.value[0]?.id
    }
    if (activeLibId.value) await loadCases()
  } finally {
    libLoading.value = false
  }
}

const handleLibChange = async (row: CaseLibApi.CaseLibVO | null) => {
  if (!row || row.id === activeLibId.value) return
  activeLibId.value = row.id
  caseQuery.pageNo = 1
  await loadCases()
}

/** 库内用例 */
const loadCases = async () => {
  if (!activeLibId.value) return
  caseLoading.value = true
  try {
    const data = await CaseLibApi.getCaseLibCasePage(activeLibId.value, caseQuery)
    caseList.value = data.list
    caseTotal.value = data.total
  } finally {
    caseLoading.value = false
  }
}

/** ---------- 用例库表单 ---------- */
const libFormVisible = ref(false)
const libFormLoading = ref(false)
const libFormTitle = ref('')
const libFormRef = ref()
const libFormType = ref<'create' | 'update'>('create')
const libForm = reactive<CaseLibApi.CaseLibVO>({ id: undefined, name: '', desc: '', order: 0 })
const libRules = {
  name: [{ required: true, message: '用例库名称不能为空', trigger: 'blur' }]
}

const openLibForm = (type: 'create' | 'update', row?: CaseLibApi.CaseLibVO) => {
  libFormType.value = type
  libFormTitle.value = type === 'create' ? '新建用例库' : '编辑用例库'
  libFormVisible.value = true
  libForm.id = row?.id
  libForm.name = row?.name || ''
  libForm.desc = row?.desc || ''
  libForm.order = row?.order ?? 0
}

const submitLibForm = async () => {
  await libFormRef.value.validate()
  libFormLoading.value = true
  try {
    let newId: number | undefined
    if (libFormType.value === 'create') {
      newId = await CaseLibApi.createCaseLib(libForm)
      message.success(t('common.createSuccess'))
    } else {
      await CaseLibApi.updateCaseLib(libForm)
      message.success(t('common.updateSuccess'))
    }
    libFormVisible.value = false
    // 建完直接选中新库；改完保持当前选中（loadLibs(true) 不会把选中重置回第一个库）
    await loadLibs(true)
    if (newId) {
      activeLibId.value = newId
      caseQuery.pageNo = 1
      await loadCases()
    }
  } finally {
    libFormLoading.value = false
  }
}

const handleDeleteLib = async (row: CaseLibApi.CaseLibVO) => {
  try {
    await message.delConfirm(`确认删除用例库「${row.name}」？库内还有用例时会被拒绝。`)
    await CaseLibApi.deleteCaseLib(row.id!)
    message.success(t('common.delSuccess'))
    await loadLibs(false)
  } catch {}
}

/** ---------- 库内用例表单 ---------- */
const caseFormVisible = ref(false)
const caseFormLoading = ref(false)
const caseFormTitle = ref('新建库内用例')
const caseFormRef = ref()
const caseForm = reactive<CaseApi.CaseVO & { steps: CaseApi.CaseStepVO[] }>({
  title: '',
  type: 'feature',
  pri: 3,
  stage: undefined,
  module: undefined,
  precondition: '',
  steps: [{ desc: '', expect: '' }]
})
const caseRules = {
  title: [{ required: true, message: '用例标题不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '用例类型不能为空', trigger: 'change' }]
}

const openCaseForm = () => {
  caseFormVisible.value = true
  caseForm.id = undefined
  caseForm.title = ''
  caseForm.type = 'feature'
  caseForm.pri = 3
  caseForm.stage = undefined
  caseForm.module = undefined
  caseForm.precondition = ''
  caseForm.steps = [{ desc: '', expect: '' }]
}

const submitCaseForm = async () => {
  await caseFormRef.value.validate()
  caseFormLoading.value = true
  try {
    await CaseLibApi.createCaseLibCase(activeLibId.value!, {
      ...caseForm,
      // 只提交有内容/有期望的步骤，空行直接丢掉（后端也会跳过 desc 为空的步骤）
      steps: caseForm.steps.filter((s) => s.desc || s.expect)
    } as CaseApi.CaseVO)
    message.success(t('common.createSuccess'))
    caseFormVisible.value = false
    // 保留当前选中的库（loadLibs(false) 会把选中重置回第一个库，用例就"跑"到别的库里去了）
    await loadLibs(true)
  } finally {
    caseFormLoading.value = false
  }
}

const handleDeleteCase = async (row: CaseLibApi.CaseLibCaseVO) => {
  try {
    await message.delConfirm(`确认删除库内用例「${row.title}」？`)
    await CaseApi.deleteCase(row.id!)
    message.success(t('common.delSuccess'))
    await loadLibs(true)
  } catch {}
}

/** ---------- 从产品导入 ---------- */
const importVisible = ref(false)
const importLoading = ref(false)
const importList = ref<CaseLibApi.CaseLibCaseVO[]>([])
const importTotal = ref(0)
const importChecked = ref<CaseApi.CaseVO[]>([])
const importQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  product: undefined as number | undefined,
  title: ''
})

const openImport = async () => {
  importVisible.value = true
  importChecked.value = []
  importQuery.pageNo = 1
  importQuery.title = ''
  if (productList.value.length === 0) {
    productList.value = await ProductApi.getProductSimpleList()
  }
  importQuery.product = importQuery.product || productList.value[0]?.id
  await loadImportCases()
}

const loadImportCases = async () => {
  if (!activeLibId.value || !importQuery.product) return
  importLoading.value = true
  try {
    const data = await CaseLibApi.getCanImportCasePage(activeLibId.value, importQuery)
    importList.value = data.list
    importTotal.value = data.total
  } finally {
    importLoading.value = false
  }
}

const submitImport = async () => {
  const ids = importChecked.value.map((c) => c.id!).filter(Boolean)
  if (ids.length === 0) return
  await CaseLibApi.importToLib(activeLibId.value!, ids)
  message.success(`已导入 ${ids.length} 条用例`)
  importVisible.value = false
  await loadLibs(true)
}

/** ---------- 详情 ---------- */
const detailVisible = ref(false)
const detail = ref<CaseLibApi.CaseLibCaseVO>()
const openCaseDetail = async (id: number) => {
  detail.value = await CaseLibApi.getCaseLibCase(id)
  detailVisible.value = true
}

/** 初始化 */
onMounted(async () => {
  await loadLibs(false)
})
</script>
