<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="发布 = 对外交付版本"
      description="引用构建与计划，维护三份清单：完成的需求 / 解决的 Bug / 遗留的 Bug。版本号在禅道里是全局唯一的，创建发布会自动生成一个同名的「影子构建」。"
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
      <el-form-item label="版本号" prop="name">
        <el-input v-model="queryParams.name" placeholder="版本号关键词" clearable class="!w-160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="!w-140px">
          <el-option v-for="o in RELEASE_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:release:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新建发布
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" align="center" prop="id" width="70" />
      <el-table-column label="版本号" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.name }}</span>
          <el-tag v-if="row.marker === 1" size="small" class="ml-8px" type="warning">里程碑</el-tag>
          <el-tag v-if="row.included" size="small" class="ml-8px" type="info">被包含</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="产品" align="center" prop="productName" min-width="140" show-overflow-tooltip />
      <el-table-column v-if="branchVisible" label="分支/平台" align="center" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.branchName || '主干' }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="{ row }">
          <el-tag :type="releaseTagOf(row.status) as any">
            {{ row.statusName || releaseLabelOf(RELEASE_STATUS_OPTIONS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="包含构建" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">{{ (row.buildNames || []).join(', ') || '-' }}</template>
      </el-table-column>
      <el-table-column label="计划发布" align="center" prop="date" width="110" />
      <el-table-column label="实际发布" align="center" prop="releasedDate" width="170" />
      <el-table-column label="需求" align="center" width="80">
        <template #default="{ row }">
          <el-button link type="primary" @click="openLink(row, 'story')">{{ row.storyCount ?? 0 }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="解决 Bug" align="center" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="openLink(row, 'bug')">{{ row.bugCount ?? 0 }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="遗留 Bug" align="center" width="90">
        <template #default="{ row }">
          <el-button link type="danger" @click="openLink(row, 'leftBug')">{{ row.leftBugCount ?? 0 }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="260" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status !== 'normal'" link type="success"
                     @click="handlePublish(row)" v-hasPermi="['zentao:release:update']">发布</el-button>
          <el-dropdown class="ml-8px" @command="(cmd: string) => handleAction(cmd, row)">
            <el-button link type="primary">更多<Icon icon="ep:arrow-down" class="ml-2px" /></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="link">发布清单</el-dropdown-item>
                <el-dropdown-item command="edit" v-hasPermi="['zentao:release:update']">编辑</el-dropdown-item>
                <el-dropdown-item command="terminate" :disabled="row.status === 'terminate'"
                                   v-hasPermi="['zentao:release:update']">标记停止维护</el-dropdown-item>
                <el-dropdown-item command="fail" :disabled="row.status === 'fail'" divided
                                   v-hasPermi="['zentao:release:update']">标记发布失败</el-dropdown-item>
                <el-dropdown-item command="delete" divided
                                   v-hasPermi="['zentao:release:delete']">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize"
                @pagination="getList" />
  </ContentWrap>

  <ReleaseForm ref="formRef" @success="getList" />
  <ReleaseLinkDrawer ref="linkRef" @success="getList" />
</template>

<script lang="ts" setup>
import * as ReleaseApi from '@/api/zentao/release'
import * as ProductApi from '@/api/zentao/product'
import * as BranchApi from '@/api/zentao/branch'
import ReleaseForm from './ReleaseForm.vue'
import ReleaseLinkDrawer from './ReleaseLinkDrawer.vue'
import { RELEASE_STATUS_OPTIONS, releaseLabelOf, releaseTagOf } from './constants'

defineOptions({ name: 'ZentaoRelease' })

const message = useMessage()
const { t } = useI18n()

const loading = ref(true)
const total = ref(0)
const list = ref<ReleaseApi.ReleaseVO[]>([])
const productList = ref<ProductApi.ProductSimpleVO[]>([])
const branchList = ref<BranchApi.BranchVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  product: undefined as number | undefined,
  branch: undefined as number | undefined,
  name: '',
  status: undefined as string | undefined
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
    const data = await ReleaseApi.getReleasePage(queryParams)
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
  queryParams.status = undefined
  branchList.value = []
  handleQuery()
}

const openForm = (type: string, id?: number) => formRef.value.open(type, id, queryParams.product)
const openLink = (row: ReleaseApi.ReleaseVO, tab: string) => linkRef.value.open(row, tab)

const handlePublish = async (row: ReleaseApi.ReleaseVO) => {
  try {
    await message.confirm('确认发布该版本？将写入实际发布日期，并把关联需求的阶段推进到「已发布」')
    await ReleaseApi.publishRelease(row.id!)
    message.success('已发布')
    await getList()
  } catch {}
}

const handleAction = async (cmd: string, row: ReleaseApi.ReleaseVO) => {
  const id = row.id!
  try {
    switch (cmd) {
      case 'link':
        openLink(row, 'story')
        return
      case 'edit':
        openForm('update', id)
        return
      case 'terminate':
        await message.confirm('确认标记为「停止维护」？')
        await ReleaseApi.changeReleaseStatus(id, 'terminate')
        message.success('已标记停止维护')
        break
      case 'fail':
        await message.confirm('确认标记为「发布失败」？')
        await ReleaseApi.changeReleaseStatus(id, 'fail')
        message.success('已标记发布失败')
        break
      case 'delete':
        await message.delConfirm(`确认删除发布「${row.name}」？影子构建会一并删除。`)
        await ReleaseApi.deleteRelease(id)
        message.success(t('common.delSuccess'))
        break
    }
    await getList()
  } catch {}
}

onMounted(async () => {
  productList.value = await ProductApi.getProductSimpleList()
  getList()
})
</script>
