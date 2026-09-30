<template>
  <ContentWrap>
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="产品名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="名称关键词" clearable class="!w-200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="产品代号" prop="code">
        <el-input v-model="queryParams.code" placeholder="代号" clearable class="!w-160px" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="!w-140px">
          <el-option v-for="o in PRODUCT_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:product:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增产品
        </el-button>
        <el-button type="danger" plain :disabled="checkedIds.length === 0" @click="handleDeleteBatch"
                   v-hasPermi="['zentao:product:delete']">
          <Icon icon="ep:delete" class="mr-5px" /> 批量删除
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" @selection-change="handleRowCheckboxChange">
      <el-table-column type="selection" width="55" />
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="产品名称" prop="name" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row.id)">{{ row.name }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="代号" align="center" prop="code" width="110" />
      <el-table-column label="类型" align="center" width="100">
        <template #default="{ row }">{{ productLabelOf(PRODUCT_TYPE_OPTIONS, row.type) }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="{ row }">
          <el-tag :type="productTagOf(row.status) as any">
            {{ productLabelOf(PRODUCT_STATUS_OPTIONS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="产品经理" align="center" prop="PO" width="100" />
      <el-table-column label="测试负责人" align="center" prop="QD" width="110" />
      <el-table-column label="研发负责人" align="center" prop="RD" width="110" />
      <el-table-column label="创建人" align="center" prop="createdBy" width="100" />
      <el-table-column label="操作" align="center" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">统计</el-button>
          <el-button link type="primary" @click="openForm('update', row.id)"
                     :disabled="row.status === 'closed'" v-hasPermi="['zentao:product:update']">编辑</el-button>
          <el-dropdown class="ml-8px" @command="(cmd: string) => handleAction(cmd, row)">
            <el-button link type="primary">更多<Icon icon="ep:arrow-down" class="ml-2px" /></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="stories">查看需求</el-dropdown-item>
                <el-dropdown-item command="bugs">查看缺陷</el-dropdown-item>
                <el-dropdown-item command="close" :disabled="row.status === 'closed'" divided
                                   v-hasPermi="['zentao:product:update']">关闭</el-dropdown-item>
                <el-dropdown-item command="activate" :disabled="row.status !== 'closed'"
                                   v-hasPermi="['zentao:product:update']">激活</el-dropdown-item>
                <el-dropdown-item command="delete" divided
                                   v-hasPermi="['zentao:product:delete']">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize"
                @pagination="getList" />
  </ContentWrap>

  <!-- 统计抽屉 -->
  <el-drawer v-model="statsVisible" :title="`产品统计 · ${statsData.name ?? ''}`" size="420px">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="需求总数">{{ statsData.stats?.totalStories ?? 0 }}</el-descriptions-item>
      <el-descriptions-item label="激活需求">{{ statsData.stats?.activeStories ?? 0 }}</el-descriptions-item>
      <el-descriptions-item label="已关闭需求">{{ statsData.stats?.closedStories ?? 0 }}</el-descriptions-item>
      <el-descriptions-item label="缺陷总数">{{ statsData.stats?.totalBugs ?? 0 }}</el-descriptions-item>
      <el-descriptions-item label="未解决缺陷">{{ statsData.stats?.unresolvedBugs ?? 0 }}</el-descriptions-item>
      <el-descriptions-item label="已关闭缺陷">{{ statsData.stats?.closedBugs ?? 0 }}</el-descriptions-item>
    </el-descriptions>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mt-15px"
      title="实时统计"
      description="这些数字是查询时实时聚合出来的，不是冗余存储。禅道用 20 多个计数器列冗余保存，需要业务代码维护一致性；本实现选择了永不漂移的实时统计。"
    />
  </el-drawer>

  <ProductForm ref="formRef" @success="getList" />
</template>

<script lang="ts" setup>
import { useRouter } from 'vue-router'
import * as ProductApi from '@/api/zentao/product'
import ProductForm from './ProductForm.vue'
import {
  PRODUCT_STATUS_OPTIONS,
  PRODUCT_TYPE_OPTIONS,
  productLabelOf,
  productTagOf
} from './constants'

defineOptions({ name: 'ZentaoProduct' })

const message = useMessage()
const { t } = useI18n()
const router = useRouter()

const loading = ref(true)
const total = ref(0)
const list = ref<ProductApi.ProductVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: '',
  code: '',
  status: undefined as string | undefined
})
const queryFormRef = ref()
const formRef = ref()

const statsVisible = ref(false)
const statsData = ref<ProductApi.ProductVO>({})

const getList = async () => {
  loading.value = true
  try {
    const data = await ProductApi.getProductPage(queryParams)
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
  queryParams.status = undefined
  handleQuery()
}

const openForm = (type: string, id?: number) => formRef.value.open(type, id)

/** 查看统计 */
const openDetail = async (id: number) => {
  statsData.value = await ProductApi.getProduct(id)
  statsVisible.value = true
}

const handleAction = async (cmd: string, row: ProductApi.ProductVO) => {
  const id = row.id!
  try {
    switch (cmd) {
      case 'stories':
        await router.push({ path: '/zentao/story', query: { product: id } })
        return
      case 'bugs':
        await router.push({ path: '/zentao/bug', query: { product: id } })
        return
      case 'close':
        await message.confirm('确认关闭该产品？关闭后不能再修改，需要先激活')
        await ProductApi.closeProduct(id)
        message.success('产品已关闭')
        break
      case 'activate':
        await message.confirm('确认激活该产品？')
        await ProductApi.activateProduct(id)
        message.success('产品已激活')
        break
      case 'delete':
        await message.delConfirm()
        await ProductApi.deleteProduct(id)
        message.success(t('common.delSuccess'))
        break
    }
    await getList()
  } catch {}
}

const checkedIds = ref<number[]>([])
const handleRowCheckboxChange = (rows: ProductApi.ProductVO[]) => {
  checkedIds.value = rows.map((row) => row.id!)
}

const handleDeleteBatch = async () => {
  try {
    await message.delConfirm()
    await ProductApi.deleteProductList(checkedIds.value)
    checkedIds.value = []
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

onMounted(() => {
  getList()
})
</script>
