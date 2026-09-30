<template>
  <ContentWrap>
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="缺陷标题" prop="title">
        <el-input v-model="queryParams.title" placeholder="标题关键词" clearable class="!w-200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="所属产品" prop="product">
        <el-select v-model="queryParams.product" placeholder="全部产品" clearable filterable class="!w-200px"
                   @change="handleProductChange">
          <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!" />
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
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="!w-140px">
          <el-option v-for="o in BUG_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="严重程度" prop="severity">
        <el-select v-model="queryParams.severity" placeholder="全部" clearable class="!w-140px">
          <el-option v-for="o in BUG_SEVERITY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:bug:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 提 Bug
        </el-button>
        <el-button type="danger" plain :disabled="checkedIds.length === 0" @click="handleDeleteBatch"
                   v-hasPermi="['zentao:bug:delete']">
          <Icon icon="ep:delete" class="mr-5px" /> 批量删除
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" @selection-change="handleRowCheckboxChange">
      <el-table-column type="selection" width="55" />
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="缺陷标题" prop="title" min-width="220" show-overflow-tooltip />
      <el-table-column label="模块" align="center" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ moduleName(row.module) }}</template>
      </el-table-column>
      <el-table-column v-if="branchVisible" label="分支/平台" align="center" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ branchName(row.branch) }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="{ row }">
          <el-tag :type="bugTagOf(row.status) as any">
            {{ bugLabelOf(BUG_STATUS_OPTIONS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="严重程度" align="center" width="100">
        <template #default="{ row }">
          <el-tag :type="severityTagOf(row.severity) as any" effect="plain">
            {{ bugLabelOf(BUG_SEVERITY_OPTIONS, row.severity) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="pri" width="70" />
      <el-table-column label="类型" align="center" width="110">
        <template #default="{ row }">{{ bugLabelOf(BUG_TYPE_OPTIONS, row.type) }}</template>
      </el-table-column>
      <el-table-column label="解决方案" align="center" width="110">
        <template #default="{ row }">
          <span v-if="row.resolution">{{ bugLabelOf(BUG_RESOLUTION_OPTIONS, row.resolution) }}</span>
          <span v-else class="text-gray-400">-</span>
        </template>
      </el-table-column>
      <el-table-column label="激活次数" align="center" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.activatedCount > 0" type="warning" size="small">{{ row.activatedCount }}</el-tag>
          <span v-else class="text-gray-400">0</span>
        </template>
      </el-table-column>
      <el-table-column label="指派给" align="center" prop="assignedTo" width="100" />
      <el-table-column label="创建人" align="center" prop="openedBy" width="100" />
      <el-table-column label="操作" align="center" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'active'" link type="primary"
                     @click="resolveRef.open(row.id, row.product)" v-hasPermi="['zentao:bug:update']">解决</el-button>
          <el-button v-if="row.status === 'resolved'" link type="success"
                     @click="handleAction('close', row)" v-hasPermi="['zentao:bug:update']">关闭</el-button>
          <el-dropdown class="ml-8px" @command="(cmd: string) => handleAction(cmd, row)">
            <el-button link type="primary">更多<Icon icon="ep:arrow-down" class="ml-2px" /></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit" :disabled="row.status === 'closed'"
                                   v-hasPermi="['zentao:bug:update']">编辑</el-dropdown-item>
                <el-dropdown-item command="activate" :disabled="row.status === 'active'"
                                   v-hasPermi="['zentao:bug:update']">重新激活</el-dropdown-item>
                <el-dropdown-item command="delete" divided
                                   v-hasPermi="['zentao:bug:delete']">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize"
                @pagination="getList" />
  </ContentWrap>

  <BugForm ref="formRef" @success="getList" />
  <BugResolveForm ref="resolveRef" @success="getList" />
</template>

<script lang="ts" setup>
import * as BugApi from '@/api/zentao/bug'
import * as ProductApi from '@/api/zentao/product'
import * as ModuleApi from '@/api/zentao/module'
import * as BranchApi from '@/api/zentao/branch'
import BugForm from './BugForm.vue'
import BugResolveForm from './BugResolveForm.vue'
import {
  BUG_STATUS_OPTIONS,
  BUG_SEVERITY_OPTIONS,
  BUG_TYPE_OPTIONS,
  BUG_RESOLUTION_OPTIONS,
  bugLabelOf,
  bugTagOf,
  severityTagOf
} from './constants'

defineOptions({ name: 'ZentaoBug' })

const message = useMessage()
const { t } = useI18n()

const loading = ref(true)
const total = ref(0)
const list = ref<BugApi.BugVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  title: '',
  product: undefined as number | undefined,
  branch: undefined as number | undefined,
  module: undefined as number | undefined,
  status: undefined as string | undefined,
  severity: undefined as number | undefined
})
const queryFormRef = ref()
const formRef = ref()
const resolveRef = ref()
const productList = ref<ProductApi.ProductSimpleVO[]>([])
const moduleTree = ref<ModuleApi.ModuleVO[]>([])
const branchList = ref<BranchApi.BranchVO[]>([])

// 产品类型决定要不要展示「分支/平台」
const branchVisible = computed(() => {
  const hit = productList.value.find((p) => p.id === queryParams.product)
  return hit?.type === 'branch' || hit?.type === 'platform'
})

const loadModuleAndBranch = async () => {
  if (!queryParams.product) {
    moduleTree.value = []
    branchList.value = []
    return
  }
  moduleTree.value = await ModuleApi.getModuleTree({
    root: queryParams.product,
    type: 'bug',
    branch: queryParams.branch ?? 0
  })
  branchList.value = branchVisible.value
    ? await BranchApi.getBranchListByProduct(queryParams.product)
    : []
}

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

const handleProductChange = async () => {
  queryParams.branch = undefined
  queryParams.module = undefined
  await loadModuleAndBranch()
  handleQuery()
}
const handleBranchChange = async () => {
  queryParams.module = undefined
  await loadModuleAndBranch()
  handleQuery()
}

const getList = async () => {
  loading.value = true
  try {
    const data = await BugApi.getBugPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  queryParams.product = undefined
  queryParams.branch = undefined
  queryParams.module = undefined
  moduleTree.value = []
  branchList.value = []
  queryParams.status = undefined
  queryParams.severity = undefined
  handleQuery()
}

const openForm = (type: string, id?: number) => formRef.value.open(type, id)

const handleAction = async (cmd: string, row: BugApi.BugVO) => {
  const id = row.id!
  try {
    switch (cmd) {
      case 'edit':
        openForm('update', id)
        return
      case 'close':
        await message.confirm('确认关闭该缺陷？')
        await BugApi.closeBug(id)
        message.success('缺陷已关闭')
        break
      case 'activate':
        await message.confirm('确认重新激活该缺陷？激活次数会 +1')
        await BugApi.activateBug(id, '重新激活')
        message.success('缺陷已激活')
        break
      case 'delete':
        await message.delConfirm()
        await BugApi.deleteBug(id)
        message.success(t('common.delSuccess'))
        break
    }
    await getList()
  } catch {}
}

const checkedIds = ref<number[]>([])
const handleRowCheckboxChange = (rows: BugApi.BugVO[]) => {
  checkedIds.value = rows.map((row) => row.id!)
}

const handleDeleteBatch = async () => {
  try {
    await message.delConfirm()
    await BugApi.deleteBugList(checkedIds.value)
    checkedIds.value = []
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

onMounted(async () => {
  productList.value = await ProductApi.getProductSimpleList()
  getList()
})
</script>
