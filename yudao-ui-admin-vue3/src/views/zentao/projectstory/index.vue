<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="项目需求范围：先关联产品，再把需求纳入范围"
      description="禅道用 zt_projectproduct 记录项目关联了哪些产品，用 zt_projectstory 记录具体纳入了哪些需求。关联时会把「需求当时的版本」记下来，需求后续正式变更不会改掉已排期的内容。"
    />

    <el-form class="-mb-15px" :inline="true" label-width="90px">
      <el-form-item label="项目/执行">
        <el-select v-model="projectId" placeholder="请选择项目或执行" filterable class="!w-280px" @change="handleProjectChange">
          <el-option-group label="项目">
            <el-option v-for="p in projectList" :key="'p' + p.id" :label="p.name" :value="p.id!" />
          </el-option-group>
          <el-option-group label="执行">
            <el-option v-for="e in executionList" :key="'e' + e.id" :label="e.name" :value="e.id!" />
          </el-option-group>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" plain :disabled="!projectId" @click="openLinkProduct">
          <Icon icon="ep:plus" class="mr-5px" /> 关联产品
        </el-button>
        <el-button type="primary" :disabled="!projectId || linkedProducts.length === 0" @click="linkDrawerRef?.open()">
          <Icon icon="ep:connection" class="mr-5px" /> 纳入需求
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap title="关联的产品">
    <el-table v-loading="loading" :data="linkedProducts" empty-text="该项目还没有关联任何产品">
      <el-table-column label="产品" prop="productName" min-width="180" show-overflow-tooltip />
      <el-table-column label="分支/平台" align="center" prop="branchName" width="130" />
      <el-table-column label="限定计划" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ (row.planNames || []).join(', ') || '不限' }}</template>
      </el-table-column>
      <el-table-column label="已纳入需求" align="center" prop="storyCount" width="110" />
      <el-table-column label="操作" align="center" width="120">
        <template #default="{ row }">
          <el-button link type="danger" @click="handleUnlinkProduct(row)"
                     v-hasPermi="['zentao:projectstory:update']">解除关联</el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <ContentWrap :title="`需求范围（${list.length}）`">
    <el-table v-loading="loading" :data="list" empty-text="还没有纳入需求">
      <el-table-column label="排序" align="center" prop="order" width="70" />
      <el-table-column label="编号" align="center" prop="story" width="80" />
      <el-table-column label="需求标题" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.title }}</span>
          <el-tag v-if="row.versionChanged" size="small" class="ml-8px" type="warning">版本已变更</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="产品" align="center" prop="productName" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" align="center" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagOf(row.status) as any">
            {{ statusLabelOf(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="阶段" align="center" width="100">
        <template #default="{ row }">{{ stageLabelOf(row.stage) }}</template>
      </el-table-column>
      <el-table-column label="版本" align="center" width="130">
        <template #default="{ row }">
          <el-tooltip :content="`关联时版本 v${row.linkVersion}，当前版本 v${row.currentVersion}`">
            <span :class="row.versionChanged ? 'text-orange-500' : ''">
              v{{ row.linkVersion }}<template v-if="row.versionChanged"> → v{{ row.currentVersion }}</template>
            </span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="pri" width="80" />
      <el-table-column label="指派给" align="center" prop="assignedTo" width="100" />
      <el-table-column label="同时被关联" align="center" min-width="120">
        <template #default="{ row }">
          <el-tag v-for="pid in (row.relatedProjects || []).filter((x: number) => x !== row.project)"
                  :key="pid" size="small" class="mr-4px" type="info">#{{ pid }}</el-tag>
          <span v-if="(row.relatedProjects || []).filter((x: number) => x !== row.project).length === 0"
                class="text-gray-400">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" @click="handleUnlinkStory(row)"
                     v-hasPermi="['zentao:projectstory:delete']">移出范围</el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <!-- 关联产品弹窗 -->
  <Dialog v-model="productDialogVisible" title="关联产品" width="520">
    <el-form :model="productForm" label-width="90px">
      <el-form-item label="产品" required>
        <el-select v-model="productForm.product" placeholder="请选择产品" filterable class="w-full">
          <el-option v-for="p in productOptions" :key="p.id" :label="p.name" :value="p.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="分支/平台">
        <el-select v-model="productForm.branch" placeholder="主干" clearable filterable class="w-full">
          <el-option label="主干" :value="0" />
          <el-option v-for="b in branchOptions" :key="b.id" :label="b.name" :value="b.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="限定计划">
        <el-select v-model="productForm.plans" multiple filterable placeholder="不限（吃该产品全部需求）" class="w-full">
          <el-option v-for="p in planOptions" :key="p.id" :label="p.title" :value="p.id!" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="submitProduct">确 定</el-button>
      <el-button @click="productDialogVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <ProjectStoryLinkDrawer ref="linkDrawerRef" :project-id="projectId" :linked-products="linkedProducts"
                          @success="loadData" />
</template>

<script lang="ts" setup>
import * as PsApi from '@/api/zentao/projectstory'
import * as ProjectApi from '@/api/zentao/project'
import * as ExecutionApi from '@/api/zentao/execution'
import * as ProductApi from '@/api/zentao/product'
import * as BranchApi from '@/api/zentao/branch'
import * as PlanApi from '@/api/zentao/plan'
import ProjectStoryLinkDrawer from './ProjectStoryLinkDrawer.vue'
import { STORY_STATUS_OPTIONS, STORY_STAGE_OPTIONS, labelOf, tagOf } from '@/views/zentao/story/constants'

defineOptions({ name: 'ZentaoProjectStory' })

const message = useMessage()

const loading = ref(false)
const projectId = ref<number>()
const projectList = ref<ProjectApi.ProjectVO[]>([])
const executionList = ref<ExecutionApi.ExecutionVO[]>([])
const linkedProducts = ref<PsApi.ProjectProductVO[]>([])
const list = ref<PsApi.ProjectStoryVO[]>([])
const linkDrawerRef = ref()

const productDialogVisible = ref(false)
const productForm = reactive<{ product?: number; branch?: number; plans: number[] }>({ plans: [] })
const productOptions = ref<ProductApi.ProductSimpleVO[]>([])
const branchOptions = ref<BranchApi.BranchVO[]>([])
const planOptions = ref<PlanApi.PlanVO[]>([])

const statusLabelOf = (status?: string) => labelOf(STORY_STATUS_OPTIONS, status)
const statusTagOf = (status?: string) => tagOf(status)
const stageLabelOf = (stage?: string) => labelOf(STORY_STAGE_OPTIONS, stage)

const loadData = async () => {
  if (!projectId.value) {
    linkedProducts.value = []
    list.value = []
    return
  }
  loading.value = true
  try {
    const [products, stories] = await Promise.all([
      PsApi.getProductList(projectId.value),
      PsApi.getStoryList(projectId.value)
    ])
    linkedProducts.value = products
    list.value = stories
  } finally {
    loading.value = false
  }
}

const handleProjectChange = () => loadData()

const handleUnlinkStory = async (row: PsApi.ProjectStoryVO) => {
  try {
    await message.delConfirm(`确认把「${row.title}」移出项目范围？`)
    await PsApi.unlinkStory(projectId.value!, row.story!)
    message.success('已移出范围')
    await loadData()
  } catch {}
}

const handleUnlinkProduct = async (row: PsApi.ProjectProductVO) => {
  try {
    await message.delConfirm(`确认解除与产品「${row.productName}」的关联？`)
    await PsApi.unlinkProduct(projectId.value!, row.product!, row.branch)
    message.success('已解除关联')
    await loadData()
  } catch {}
}

const openLinkProduct = async () => {
  productForm.product = undefined
  productForm.branch = 0
  productForm.plans = []
  productDialogVisible.value = true
  productOptions.value = await ProductApi.getProductSimpleList()
}

watch(
  () => productForm.product,
  async (val) => {
    planOptions.value = val ? await PlanApi.getPlanListByProduct(val) : []
    const hit = productOptions.value.find((p) => p.id === val)
    branchOptions.value =
      hit && (hit.type === 'branch' || hit.type === 'platform')
        ? await BranchApi.getBranchListByProduct(val!)
        : []
  }
)

const submitProduct = async () => {
  if (!productForm.product) {
    message.warning('请选择产品')
    return
  }
  try {
    await PsApi.linkProduct({
      project: projectId.value!,
      product: productForm.product,
      branch: productForm.branch ?? 0,
      plans: productForm.plans
    })
    message.success('已关联产品')
    productDialogVisible.value = false
    await loadData()
  } catch {}
}

onMounted(async () => {
  projectList.value = await ProjectApi.getProjectSimpleList()
  const execPage = await ExecutionApi.getExecutionPage({ pageNo: 1, pageSize: 100 })
  executionList.value = execPage.list
  // 默认选中第一个项目，省一次点击（也方便直接看到演示数据）
  projectId.value = projectList.value[0]?.id
  await loadData()
})
</script>
