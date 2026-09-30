<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="分支 / 平台是产品维度的概念"
      description="禅道约定 id=0 是虚拟「主干」（不落库、不能删除）；产品类型为 branch 时叫「分支」，为 platform 时叫「平台」，normal 类型的产品没有分支。"
    />

    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="所属产品" prop="product">
        <el-select v-model="queryParams.product" placeholder="全部产品" clearable filterable class="!w-220px"
                   @change="handleQuery">
          <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!">
            <span>{{ p.name }}</span>
            <el-tag v-if="p.type === 'platform'" size="small" class="ml-8px" type="warning">平台</el-tag>
            <el-tag v-else-if="p.type === 'branch'" size="small" class="ml-8px" type="success">分支</el-tag>
            <el-tag v-else size="small" class="ml-8px" type="info">普通</el-tag>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="名称关键词" clearable class="!w-180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="!w-140px">
          <el-option v-for="o in BRANCH_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:branch:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新建分支/平台
        </el-button>
        <el-button type="danger" plain :disabled="checkedIds.length === 0" @click="handleDeleteBatch"
                   v-hasPermi="['zentao:branch:delete']">
          <Icon icon="ep:delete" class="mr-5px" /> 批量删除
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" @selection-change="handleRowCheckboxChange">
      <el-table-column type="selection" width="55" :selectable="(row: BranchApi.BranchVO) => !row.mainBranch" />
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="名称" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.name }}</span>
          <el-tag v-if="row.mainBranch" size="small" class="ml-8px" type="info">主干</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" align="center" width="90">
        <template #default="{ row }">{{ row.branchLabel || '分支' }}</template>
      </el-table-column>
      <el-table-column label="所属产品" prop="productName" min-width="160" show-overflow-tooltip />
      <el-table-column label="默认" align="center" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.defaultFlag === 1" size="small" type="success">默认</el-tag>
          <span v-else class="text-gray-400">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="{ row }">
          <el-tag :type="branchTagOf(row.status) as any">
            {{ branchLabelOf(BRANCH_STATUS_OPTIONS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="描述" prop="desc" min-width="160" show-overflow-tooltip />
      <el-table-column label="创建时间" align="center" prop="createdDate" width="170" :formatter="dateFormatter" />
      <el-table-column label="关闭时间" align="center" prop="closedDate" width="170" :formatter="dateFormatter" />
      <el-table-column label="排序" align="center" prop="order" width="70" />
      <el-table-column label="操作" align="center" width="260" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'active'" link type="warning"
                     @click="handleAction('close', row)" v-hasPermi="['zentao:branch:update']">关闭</el-button>
          <el-button v-if="row.status === 'closed'" link type="success"
                     @click="handleAction('activate', row)" v-hasPermi="['zentao:branch:update']">激活</el-button>
          <el-button v-if="row.defaultFlag !== 1" link type="primary"
                     @click="handleAction('default', row)" v-hasPermi="['zentao:branch:update']">设为默认</el-button>
          <el-button v-if="!row.mainBranch" link type="primary"
                     @click="openForm('update', row.id, row.product)" v-hasPermi="['zentao:branch:update']">编辑</el-button>
          <el-button v-if="!row.mainBranch" link type="danger"
                     @click="handleAction('delete', row)" v-hasPermi="['zentao:branch:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize"
                @pagination="getList" />
  </ContentWrap>

  <BranchForm ref="formRef" @success="getList" />
</template>

<script lang="ts" setup>
import * as BranchApi from '@/api/zentao/branch'
import * as ProductApi from '@/api/zentao/product'
import { dateFormatter } from '@/utils/formatTime'
import BranchForm from './BranchForm.vue'
import { BRANCH_STATUS_OPTIONS, branchLabelOf, branchTagOf } from './constants'

defineOptions({ name: 'ZentaoBranch' })

const message = useMessage()
const { t } = useI18n()

const loading = ref(true)
const total = ref(0)
const list = ref<BranchApi.BranchVO[]>([])
const productList = ref<ProductApi.ProductSimpleVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  product: undefined as number | undefined,
  name: '',
  status: undefined as string | undefined
})
const queryFormRef = ref()
const formRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await BranchApi.getBranchPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const loadProducts = async () => {
  try {
    productList.value = await ProductApi.getProductSimpleList()
  } catch {
    productList.value = []
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  queryParams.product = undefined
  queryParams.status = undefined
  handleQuery()
}

const openForm = (type: string, id?: number, product?: number) => formRef.value.open(type, id, product)

const handleAction = async (cmd: string, row: BranchApi.BranchVO) => {
  const id = row.id!
  try {
    switch (cmd) {
      case 'close':
        await message.confirm(`确认关闭该${row.branchLabel || '分支'}？关闭会同时取消它的默认标记`)
        await BranchApi.closeBranch(id)
        message.success('已关闭')
        break
      case 'activate':
        await message.confirm(`确认激活该${row.branchLabel || '分支'}？`)
        await BranchApi.activateBranch(id)
        message.success('已激活')
        break
      case 'default':
        await message.confirm(
          `确认把「${row.name}」设为默认${row.branchLabel || '分支'}？默认${row.branchLabel || '分支'}会被计划、发布列表默认选中`
        )
        // 主干传 0：把默认还原到主干，即清空其他分支的默认标记
        await BranchApi.setDefaultBranch(row.product!, row.mainBranch ? 0 : id)
        message.success('已设为默认')
        break
      case 'delete':
        await message.delConfirm()
        await BranchApi.deleteBranch(id)
        message.success(t('common.delSuccess'))
        break
    }
    await getList()
  } catch {}
}

const checkedIds = ref<number[]>([])
const handleRowCheckboxChange = (rows: BranchApi.BranchVO[]) => {
  checkedIds.value = rows.map((row) => row.id!)
}

const handleDeleteBatch = async () => {
  try {
    await message.delConfirm()
    await BranchApi.deleteBranchList(checkedIds.value)
    checkedIds.value = []
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

onMounted(async () => {
  await loadProducts()
  getList()
})
</script>
