<template>
  <ContentWrap>
    <!-- 搜索工作栏 -->
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="80px"
    >
      <el-form-item label="所属产品" prop="product">
        <el-select
          v-model="queryParams.product"
          placeholder="全部产品"
          clearable
          filterable
          class="!w-200px"
          @change="handleProductChange"
        >
          <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="branchVisible" label="分支/平台" prop="branch">
        <el-select v-model="queryParams.branch" placeholder="主干" clearable filterable class="!w-160px" @change="handleBranchChange">
          <el-option label="主干" :value="0" />
          <el-option v-for="b in branchList" :key="b.id" :label="b.name" :value="b.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="所属模块" prop="module">
        <el-tree-select
          v-model="queryParams.module"
          :data="moduleTree"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          node-key="id"
          check-strictly
          clearable
          filterable
          placeholder="全部模块"
          class="!w-200px"
          @change="handleQuery"
        />
      </el-form-item>
      <el-form-item label="所属计划" prop="plan">
        <el-select v-model="queryParams.plan" placeholder="全部计划" clearable filterable class="!w-180px" @change="handleQuery">
          <el-option v-for="p in planList" :key="p.id" :label="p.title" :value="p.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="需求标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入标题关键词"
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="!w-160px">
          <el-option v-for="o in STORY_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="阶段" prop="stage">
        <el-select v-model="queryParams.stage" placeholder="全部阶段" clearable class="!w-160px">
          <el-option v-for="o in STORY_STAGE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button
          type="primary"
          plain
          @click="openForm('create')"
          v-hasPermi="['zentao:story:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新增需求
        </el-button>
        <el-button
          type="danger"
          plain
          :disabled="checkedIds.length === 0"
          @click="handleDeleteBatch"
          v-hasPermi="['zentao:story:delete']"
        >
          <Icon icon="ep:delete" class="mr-5px" /> 批量删除
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <!-- 需求分层页签：业务需求 / 用户需求 / 研发需求（禅道把三层放在同一张表里，靠 type 区分） -->
    <div class="mb-12px flex items-center justify-between">
      <el-radio-group v-model="activeType" :disabled="treeMode" @change="handleTypeChange">
        <el-radio-button :value="''">
          全部<span v-if="typeCounts" class="ml-2px text-12px">({{ totalOfAll }})</span>
        </el-radio-button>
        <el-radio-button v-for="t in storyTypes" :key="t.type" :value="t.type">
          {{ t.name }}<span class="ml-2px text-12px">({{ typeCounts?.[t.type] ?? 0 }})</span>
        </el-radio-button>
      </el-radio-group>
      <el-button :type="treeMode ? 'primary' : 'default'" plain @click="toggleTree">
        <Icon :icon="treeMode ? 'ep:list' : 'ep:share'" class="mr-5px" />
        {{ treeMode ? '返回列表' : '分层视图' }}
      </el-button>    </div>

    <!-- 分层视图：业务需求 → 用户需求 → 研发需求（父子需求，父的工时是子之和） -->
    <el-table
      v-if="treeMode"
      v-loading="treeLoading"
      :data="treeList"
      row-key="id"
      default-expand-all
      :tree-props="{ children: 'children' }"
    >
      <el-table-column label="需求标题" prop="title" min-width="320" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row.id)">{{ row.title }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="需求类型" align="center" width="120">
        <template #default="{ row }">
          <el-tag :type="typeTagOf(row.type) as any" size="small">{{ row.typeName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="层级" align="center" prop="grade" width="80" />
      <el-table-column label="状态" align="center" width="100">
        <template #default="{ row }">
          <el-tag :type="tagOf(row.status) as any">
            {{ labelOf(STORY_STATUS_OPTIONS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="阶段" align="center" width="110">
        <template #default="{ row }">{{ labelOf(STORY_STAGE_OPTIONS, row.stage) }}</template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="pri" width="80" />
      <el-table-column label="预计工时" align="center" width="100">
        <template #default="{ row }">{{ row.estimate ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="子需求" align="center" prop="childCount" width="80" />
      <el-table-column label="操作" align="center" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-table
      v-else
      v-loading="loading"
      :data="list"
      @selection-change="handleRowCheckboxChange"
    >
      <el-table-column type="selection" width="55" />
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="需求标题" prop="title" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row.id)">{{ row.title }}</el-link>
          <!-- 父需求：已分解 / 父需求升版后需要确认（与 projectstory 的「版本已变更」同思路） -->
          <el-tag v-if="row.isParent" type="warning" size="small" class="ml-5px">父</el-tag>
          <el-tag v-if="row.parentChanged" type="danger" size="small" class="ml-5px">父需求已变更</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="需求类型" align="center" width="110">
        <template #default="{ row }">
          <el-tag :type="typeTagOf(row.type) as any" size="small">{{ typeName(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="产品" align="center" width="150">
        <template #default="{ row }">{{ productName(row.product) }}</template>
      </el-table-column>
      <el-table-column label="模块" align="center" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ moduleName(row.module) }}</template>
      </el-table-column>
      <el-table-column label="计划" align="center" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ planName(row.plan) }}</template>
      </el-table-column>
      <el-table-column v-if="branchVisible" label="分支/平台" align="center" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ branchName(row.branch) }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="{ row }">
          <el-tag :type="tagOf(row.status) as any">
            {{ labelOf(STORY_STATUS_OPTIONS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="阶段" align="center" width="110">
        <template #default="{ row }">{{ labelOf(STORY_STAGE_OPTIONS, row.stage) }}</template>
      </el-table-column>
      <el-table-column label="版本" align="center" width="80">
        <template #default="{ row }">
          <el-tag type="info" size="small">v{{ row.version }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="pri" width="80" />
      <el-table-column label="指派给" align="center" prop="assignedTo" width="110" />
      <el-table-column label="创建人" align="center" prop="openedBy" width="100" />
      <el-table-column
        label="创建时间"
        align="center"
        prop="openedDate"
        width="170"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" align="center" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button
            link
            type="primary"
            @click="openForm('update', row.id)"
            v-hasPermi="['zentao:story:update']"
            :disabled="row.status === 'closed'"
          >
            编辑
          </el-button>
          <el-dropdown class="ml-8px" @command="(cmd: string) => handleCommand(cmd, row)">
            <el-button link type="primary">
              更多<Icon icon="ep:arrow-down" class="ml-2px" />
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  command="change"
                  :disabled="row.status === 'closed'"
                  v-hasPermi="['zentao:story:update']"
                >
                  变更（产生新版本）
                </el-dropdown-item>
                <el-dropdown-item
                  command="review"
                  :disabled="row.status !== 'active' && row.status !== 'draft'"
                  v-hasPermi="['zentao:story:update']"
                >
                  提交评审
                </el-dropdown-item>
                <el-dropdown-item
                  command="activate"
                  :disabled="row.status !== 'closed'"
                  v-hasPermi="['zentao:story:update']"
                >
                  激活
                </el-dropdown-item>
                <el-dropdown-item
                  command="close"
                  :disabled="row.status === 'closed'"
                  divided
                  v-hasPermi="['zentao:story:update']"
                >
                  关闭
                </el-dropdown-item>
                <el-dropdown-item
                  command="delete"
                  divided
                  v-hasPermi="['zentao:story:delete']"
                >
                  删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页 -->
    <Pagination
      v-if="!treeMode"
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 各个弹窗 -->
  <StoryForm ref="formRef" @success="getList" />
  <StoryChangeForm ref="changeFormRef" @success="getList" />
  <StoryReviewStartForm ref="reviewStartRef" @success="getList" />
  <StoryCloseForm ref="closeFormRef" @success="getList" />
  <StoryDetailDrawer ref="detailRef" @success="getList" />
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import * as StoryApi from '@/api/zentao/story'
import * as ProductApi from '@/api/zentao/product'
import * as ModuleApi from '@/api/zentao/module'
import * as BranchApi from '@/api/zentao/branch'
import * as PlanApi from '@/api/zentao/plan'
import StoryForm from './StoryForm.vue'
import StoryChangeForm from './StoryChangeForm.vue'
import StoryReviewStartForm from './StoryReviewStartForm.vue'
import StoryCloseForm from './StoryCloseForm.vue'
import StoryDetailDrawer from './StoryDetailDrawer.vue'
import {
  STORY_STATUS_OPTIONS,
  STORY_STAGE_OPTIONS,
  labelOf,
  tagOf
} from './constants'

defineOptions({ name: 'ZentaoStory' })

const message = useMessage()
const { t } = useI18n()

const loading = ref(true)
const total = ref(0)
const list = ref<StoryApi.StoryVO[]>([])
const productList = ref<ProductApi.ProductSimpleVO[]>([])
const moduleTree = ref<ModuleApi.ModuleVO[]>([])
const planList = ref<PlanApi.PlanVO[]>([])
const branchList = ref<BranchApi.BranchVO[]>([])

// 产品类型决定要不要展示「分支/平台」；normal 类型的产品没有分支
const branchVisible = computed(() => {
  const hit = productList.value.find((p) => p.id === queryParams.product)
  return hit?.type === 'branch' || hit?.type === 'platform'
})

// 模块/分支下拉都跟着「当前选中的产品」走：不选产品时是全部模块的空状态
const loadModuleAndBranch = async () => {
  if (!queryParams.product) {
    moduleTree.value = []
    branchList.value = []
    planList.value = []
    await loadTypeData()
    return
  }
  const params = { root: queryParams.product, type: 'story', branch: queryParams.branch ?? 0 }
  moduleTree.value = await ModuleApi.getModuleTree(params)
  planList.value = await PlanApi.getPlanListByProduct(queryParams.product)
  branchList.value = branchVisible.value
    ? await BranchApi.getBranchListByProduct(queryParams.product)
    : []
  await loadTypeData()
}

// 切换产品：分支与模块都要重置（不同产品的模块树是两棵树）
const handleProductChange = async () => {
  queryParams.branch = undefined
  queryParams.module = undefined
  await loadModuleAndBranch()
  handleQuery()
}

// 切换分支：模块树是按分支隔离的，要重新拉
const handleBranchChange = async () => {
  queryParams.module = undefined
  await loadModuleAndBranch()
  handleQuery()
}

// 需求上存的是计划编号（可能是逗号列表），翻译成计划名
const planName = (plan?: string) => {
  if (!plan) return '未排期'
  return plan
    .split(',')
    .map((id) => planList.value.find((p) => String(p.id) === id.trim())?.title || `#${id}`)
    .join(',')
}

// 产品 id → 模块名 / 分支名（列表展示用）
const moduleName = (id?: number) => {
  if (!id) return '-'
  const walk = (nodes: ModuleApi.ModuleVO[]): string => {
    for (const n of nodes) {
      if (n.id === id) return n.name || ''
      const hit = walk(n.children || [])
      if (hit) return hit
    }
    return ''
  }
  return walk(moduleTree.value) || `#${id}`
}
const branchName = (id?: number) => {
  if (!id) return '主干'
  const hit = branchList.value.find((b) => b.id === id)
  return hit ? hit.name || '' : `#${id}`
}
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  product: undefined as number | undefined,
  branch: undefined as number | undefined,
  module: undefined as number | undefined,
  plan: undefined as number | undefined,
  title: '',
  status: undefined as string | undefined,
  stage: undefined as string | undefined,
  // 需求分层类型：'' 表示全部（不传），否则 epic/requirement/story
  type: undefined as string | undefined
})
const queryFormRef = ref()

// ==================== 需求分层（业务需求 / 用户需求 / 研发需求） ====================
// 禅道把三层需求放在同一张 zt_story 表里、靠 type 区分，页面用页签切换 + 分层视图看父子链路
const storyTypes = ref<StoryApi.StoryTypeVO[]>([])
const typeCounts = ref<Record<string, number> | undefined>(undefined)
const activeType = ref('')
const treeMode = ref(false)
const treeLoading = ref(false)
const treeList = ref<StoryApi.StoryTreeNodeVO[]>([])

/** 「全部」页签角标：三个类型数量之和 */
const totalOfAll = computed(() =>
  storyTypes.value.reduce((sum, t) => sum + (typeCounts.value?.[t.type] ?? 0), 0)
)

/** 需求类型名（列表里直接用后端字典翻译，避免前端再维护一份文案） */
const typeName = (type?: string) => {
  if (!type) return '研发需求'
  return storyTypes.value.find((t) => t.type === type)?.name || type
}
/** 层级配色：业务需求最粗、研发需求最细 */
const typeTagOf = (type?: string) => {
  if (type === 'epic') return 'danger'
  if (type === 'requirement') return 'warning'
  return 'info'
}

/** 拉类型字典 + 当前产品的各类型数量 */
const loadTypeData = async () => {
  if (storyTypes.value.length === 0) {
    storyTypes.value = await StoryApi.getStoryTypeList()
  }
  typeCounts.value = queryParams.product
    ? await StoryApi.getStoryTypeSummary(queryParams.product)
    : undefined
}

/** 切换需求类型页签 */
const handleTypeChange = async () => {
  queryParams.type = activeType.value || undefined
  if (treeMode.value) {
    await loadTree()
    return
  }
  handleQuery()
}

/** 列表 / 分层视图切换 */
const toggleTree = async () => {
  treeMode.value = !treeMode.value
  if (treeMode.value) {
    // 分层视图固定展示三层完整链路，类型筛选在列表模式下用（页签在树模式里是禁用的）
    activeType.value = ''
    queryParams.type = undefined
    await loadTree()
  } else {
    await getList()
  }
}

/** 分层视图：一次取回整棵需求森林（业务需求 → 用户需求 → 研发需求） */
const loadTree = async () => {
  if (!queryParams.product) {
    treeList.value = []
    message.warning('分层视图需要先选择「所属产品」')
    return
  }
  treeLoading.value = true
  try {
    treeList.value = await StoryApi.getStoryTypeTree(queryParams.product)
  } finally {
    treeLoading.value = false
  }
}

/** 产品 id → 名称 */
const productName = (id?: number) => {
  const hit = productList.value.find((p) => p.id === id)
  return hit ? hit.name : id ?? '-'
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await StoryApi.getStoryPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 搜索 */
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

/** 重置 */
const resetQuery = () => {
  queryFormRef.value.resetFields()
  queryParams.product = undefined
  queryParams.branch = undefined
  queryParams.module = undefined
  queryParams.plan = undefined
  queryParams.status = undefined
  queryParams.stage = undefined
  queryParams.type = undefined
  activeType.value = ''
  typeCounts.value = undefined
  moduleTree.value = []
  branchList.value = []
  planList.value = []
  handleQuery()
}

/** 弹窗 refs */
const formRef = ref()
const changeFormRef = ref()
const reviewStartRef = ref()
const closeFormRef = ref()
const detailRef = ref()

const openForm = (type: string, id?: number) => formRef.value.open(type, id)
const openDetail = (id: number) => detailRef.value.open(id)

/** 更多操作 */
const handleCommand = async (cmd: string, row: StoryApi.StoryVO) => {
  const id = row.id!
  switch (cmd) {
    case 'change':
      changeFormRef.value.open(id)
      break
    case 'review':
      reviewStartRef.value.open(id)
      break
    case 'activate':
      try {
        await message.confirm('确认激活该需求？激活后状态将变为「激活」')
        await StoryApi.activateStory(id)
        message.success('已激活')
        await getList()
      } catch {}
      break
    case 'close':
      closeFormRef.value.open(id)
      break
    case 'delete':
      try {
        await message.delConfirm()
        await StoryApi.deleteStory(id)
        message.success(t('common.delSuccess'))
        await getList()
      } catch {}
      break
  }
}

/** 批量删除 */
const checkedIds = ref<number[]>([])
const handleRowCheckboxChange = (rows: StoryApi.StoryVO[]) => {
  checkedIds.value = rows.map((row) => row.id!)
}

const handleDeleteBatch = async () => {
  try {
    await message.delConfirm()
    await StoryApi.deleteStoryList(checkedIds.value)
    checkedIds.value = []
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

/** 初始化 */
onMounted(async () => {
  productList.value = await ProductApi.getProductSimpleList()
  await loadTypeData()
  await getList()
})
</script>
