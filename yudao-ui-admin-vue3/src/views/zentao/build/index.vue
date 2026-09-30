<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="构建 = 一次打包记录"
      description="记录本次完成的需求与解决的 Bug；缺陷的「解决版本」存的就是构建编号，所以这里是「Bug 何时修好」的锚点。集成构建用「包含构建」合并多个子构建。"
    />

    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="所属产品" prop="product">
        <el-select v-model="queryParams.product" placeholder="全部产品" clearable filterable class="!w-200px"
                   @change="handleProductChange">
          <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="branchVisible" :label="branchLabel" prop="branch">
        <el-select v-model="queryParams.branch" placeholder="全部" clearable filterable class="!w-160px"
                   @change="handleQuery">
          <el-option label="主干" :value="0" />
          <el-option v-for="b in branchList" :key="b.id" :label="b.name" :value="b.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="所属执行" prop="execution">
        <el-select v-model="queryParams.execution" placeholder="全部执行" clearable filterable class="!w-200px"
                   @change="handleQuery">
          <el-option v-for="e in executionList" :key="e.id" :label="e.name" :value="e.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="名称关键词" clearable class="!w-160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:build:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新建构建
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" align="center" prop="id" width="70" />
      <el-table-column label="构建名称" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.name }}</span>
          <el-tag v-if="row.integrated" size="small" class="ml-8px" type="warning">集成</el-tag>
          <el-tag v-if="row.child" size="small" class="ml-8px" type="info">被引用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="产品" align="center" prop="productName" min-width="140" show-overflow-tooltip />
      <el-table-column v-if="branchVisible" label="分支/平台" align="center" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.branchName || '主干' }}</template>
      </el-table-column>
      <el-table-column label="执行" align="center" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.integrated ? '集成构建' : row.executionName || '-' }}</template>
      </el-table-column>
      <el-table-column label="包含构建" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ (row.buildNames || []).join(', ') || '-' }}</template>
      </el-table-column>
      <el-table-column label="打包日期" align="center" prop="date" width="110" />
      <el-table-column label="构建者" align="center" prop="builder" width="100" />
      <el-table-column label="需求数" align="center" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="openLink(row, 'story')">{{ row.storyCount ?? 0 }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="Bug 数" align="center" width="90">
        <template #default="{ row }">
          <el-button link type="danger" @click="openLink(row, 'bug')">{{ row.bugCount ?? 0 }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openLink(row, 'story')" v-hasPermi="['zentao:build:update']">
            关联
          </el-button>
          <el-button link type="primary" @click="openForm('update', row.id)"
                     v-hasPermi="['zentao:build:update']">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)"
                     v-hasPermi="['zentao:build:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize"
                @pagination="getList" />
  </ContentWrap>

  <BuildForm ref="formRef" @success="getList" />
  <BuildLinkDrawer ref="linkRef" @success="getList" />
</template>

<script lang="ts" setup>
import * as BuildApi from '@/api/zentao/build'
import * as ProductApi from '@/api/zentao/product'
import * as BranchApi from '@/api/zentao/branch'
import * as ExecutionApi from '@/api/zentao/execution'
import BuildForm from './BuildForm.vue'
import BuildLinkDrawer from './BuildLinkDrawer.vue'

defineOptions({ name: 'ZentaoBuild' })

const message = useMessage()
const { t } = useI18n()

const loading = ref(true)
const total = ref(0)
const list = ref<BuildApi.BuildVO[]>([])
const productList = ref<ProductApi.ProductSimpleVO[]>([])
const branchList = ref<BranchApi.BranchVO[]>([])
const executionList = ref<ExecutionApi.ExecutionVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  product: undefined as number | undefined,
  branch: undefined as number | undefined,
  execution: undefined as number | undefined,
  name: ''
})
const queryFormRef = ref()
const formRef = ref()
const linkRef = ref()

const currentProduct = computed(() => productList.value.find((p) => p.id === queryParams.product))
const branchVisible = computed(
  () => currentProduct.value?.type === 'branch' || currentProduct.value?.type === 'platform'
)
const branchLabel = computed(() => (currentProduct.value?.type === 'platform' ? '平台' : '分支'))

const getList = async () => {
  loading.value = true
  try {
    const data = await BuildApi.getBuildPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const loadBranch = async () => {
  if (!queryParams.product || !branchVisible.value) {
    branchList.value = []
    return
  }
  branchList.value = await BranchApi.getBranchListByProduct(queryParams.product)
}

const handleProductChange = async () => {
  queryParams.branch = undefined
  await loadBranch()
  handleQuery()
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  queryParams.product = undefined
  queryParams.branch = undefined
  queryParams.execution = undefined
  branchList.value = []
  handleQuery()
}

const openForm = (type: string, id?: number) => formRef.value.open(type, id, queryParams.product)
const openLink = (row: BuildApi.BuildVO, tab: string) => linkRef.value.open(row, tab)

const handleDelete = async (row: BuildApi.BuildVO) => {
  try {
    await message.delConfirm(`确认删除构建「${row.name}」？`)
    await BuildApi.deleteBuild(row.id!)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

onMounted(async () => {
  productList.value = await ProductApi.getProductSimpleList()
  const page = await ExecutionApi.getExecutionPage({ pageNo: 1, pageSize: 100 })
  executionList.value = page.list
  getList()
})
</script>
