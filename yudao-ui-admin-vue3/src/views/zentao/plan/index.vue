<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="计划是产品维度的排期单元"
      description="需求通过「所属计划」挂到计划上，发布再引用计划。禅道特色：分支/平台可多选、日期可「待定」（哨兵值 2030-01-01）、有子计划的父计划不能删除。"
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
      <el-form-item label="计划名称" prop="title">
        <el-input v-model="queryParams.title" placeholder="名称关键词" clearable class="!w-180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="!w-140px">
          <el-option v-for="o in PLAN_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:plan:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新建计划
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编号" align="center" prop="id" width="70" />
      <el-table-column label="计划名称" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.title }}</span>
          <el-tag v-if="row.parent === -1" size="small" class="ml-8px" type="warning">父计划</el-tag>
          <el-tag v-else-if="row.parent > 0" size="small" class="ml-8px" type="info">子计划</el-tag>
          <el-tag v-if="row.future" size="small" class="ml-8px">待定</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="产品" align="center" prop="productName" min-width="140" show-overflow-tooltip />
      <el-table-column v-if="branchVisible" label="分支/平台" align="center" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.branchName || '主干' }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="{ row }">
          <el-tag :type="planTagOf(row.status) as any">{{ row.statusName || planLabelOf(PLAN_STATUS_OPTIONS, row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="周期" align="center" width="200">
        <template #default="{ row }">{{ planPeriod(row) }}</template>
      </el-table-column>
      <el-table-column label="需求数" align="center" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="openLink(row)">{{ row.storyCount ?? 0 }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="Bug 数" align="center" prop="bugCount" width="90" />
      <el-table-column label="子计划" align="center" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.childCount > 0 ? 'warning' : 'info'">{{ row.childCount || 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建人" align="center" prop="createdBy" width="100" />
      <el-table-column label="操作" align="center" width="280" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'wait'" link type="success"
                     @click="handleAction('start', row)" v-hasPermi="['zentao:plan:update']">开始</el-button>
          <el-button v-if="['wait', 'doing'].includes(row.status)" link type="primary"
                     @click="handleAction('finish', row)" v-hasPermi="['zentao:plan:update']">完成</el-button>
          <el-button v-if="row.status === 'closed'" link type="success"
                     @click="handleAction('activate', row)" v-hasPermi="['zentao:plan:update']">激活</el-button>
          <el-dropdown class="ml-8px" @command="(cmd: string) => handleAction(cmd, row)">
            <el-button link type="primary">更多<Icon icon="ep:arrow-down" class="ml-2px" /></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="stories">关联需求</el-dropdown-item>
                <el-dropdown-item command="edit" :disabled="row.status === 'closed'"
                                   v-hasPermi="['zentao:plan:update']">编辑</el-dropdown-item>
                <el-dropdown-item command="close-done" :disabled="row.status === 'closed'" divided
                                   v-hasPermi="['zentao:plan:update']">关闭（已完成）</el-dropdown-item>
                <el-dropdown-item command="close-cancel" :disabled="row.status === 'closed'"
                                   v-hasPermi="['zentao:plan:update']">关闭（已取消）</el-dropdown-item>
                <el-dropdown-item command="delete" divided
                                   v-hasPermi="['zentao:plan:delete']">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize"
                @pagination="getList" />
  </ContentWrap>

  <PlanForm ref="formRef" @success="getList" />
  <PlanLinkStoryDrawer ref="linkRef" @success="getList" />
</template>

<script lang="ts" setup>
import * as PlanApi from '@/api/zentao/plan'
import * as ProductApi from '@/api/zentao/product'
import * as BranchApi from '@/api/zentao/branch'
import PlanForm from './PlanForm.vue'
import PlanLinkStoryDrawer from './PlanLinkStoryDrawer.vue'
import { PLAN_STATUS_OPTIONS, planLabelOf, planTagOf, planPeriod } from './constants'

defineOptions({ name: 'ZentaoPlan' })

const message = useMessage()
const { t } = useI18n()

const loading = ref(true)
const total = ref(0)
const list = ref<PlanApi.PlanVO[]>([])
const productList = ref<ProductApi.ProductSimpleVO[]>([])
const branchList = ref<BranchApi.BranchVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  product: undefined as number | undefined,
  branch: undefined as number | undefined,
  title: '',
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
    const data = await PlanApi.getPlanPage(queryParams)
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
const openLink = (row: PlanApi.PlanVO) => linkRef.value.open(row)

const handleAction = async (cmd: string, row: PlanApi.PlanVO) => {
  const id = row.id!
  try {
    switch (cmd) {
      case 'stories':
        openLink(row)
        return
      case 'edit':
        openForm('update', id)
        return
      case 'start':
        await message.confirm('确认开始该计划？')
        await PlanApi.startPlan(id)
        message.success('计划已开始')
        break
      case 'finish':
        await message.confirm('确认完成该计划？将写入完成时间')
        await PlanApi.finishPlan(id)
        message.success('计划已完成')
        break
      case 'close-done':
        await message.confirm('确认关闭该计划？关闭原因记为「已完成」，并写入完成时间')
        await PlanApi.closePlan(id, 'done')
        message.success('计划已关闭')
        break
      case 'close-cancel':
        await message.confirm('确认关闭该计划？关闭原因记为「已取消」')
        await PlanApi.closePlan(id, 'cancel')
        message.success('计划已关闭')
        break
      case 'activate':
        await message.confirm('确认激活该计划？激活后状态会变为「进行中」（禅道语义）')
        await PlanApi.activatePlan(id)
        message.success('计划已激活')
        break
      case 'delete':
        await message.delConfirm()
        await PlanApi.deletePlan(id)
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
