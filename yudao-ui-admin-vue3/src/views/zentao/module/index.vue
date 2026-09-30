<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="禅道的模块树是「一张表 + 一个类型」"
      description="zt_module 用 (root, type, branch) 定位一棵树：需求/缺陷/用例树挂产品，任务树挂执行，产品线的 root 恒为 0。path 是逗号格式（,5,6,），一级模块 grade=1。"
    />

    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="90px">
      <el-form-item label="树类型" prop="type">
        <el-select v-model="queryParams.type" class="!w-160px" @change="handleTypeChange">
          <el-option v-for="t in typeList" :key="t.type" :label="t.name" :value="t.type" />
        </el-select>
      </el-form-item>
      <el-form-item :label="rootLabel" prop="root">
        <el-select v-model="queryParams.root" placeholder="请选择" filterable class="!w-220px" @change="getList">
          <el-option v-for="o in rootOptions" :key="o.id" :label="o.name" :value="o.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="branchAware" label="分支/平台" prop="branch">
        <el-select v-model="queryParams.branch" placeholder="不限" clearable class="!w-180px" @change="getList">
          <el-option label="主干" :value="0" />
          <el-option v-for="b in branchList" :key="b.id" :label="b.name" :value="b.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称" prop="keyword">
        <el-input v-model="keyword" placeholder="本地过滤名称" clearable class="!w-180px" />
      </el-form-item>
      <el-form-item>
        <el-button @click="getList"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:module:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新建一级模块
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table
      v-loading="loading"
      :data="filteredTree"
      row-key="id"
      default-expand-all
      :tree-props="{ children: 'children' }"
    >
      <el-table-column label="模块名称" prop="name" min-width="260">
        <template #default="{ row }">
          <span>{{ row.name }}</span>
          <el-tag v-if="row.shortName" size="small" class="ml-8px" type="info">{{ row.shortName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="层级" align="center" prop="grade" width="70" />
      <el-table-column label="path" prop="path" width="150" show-overflow-tooltip />
      <el-table-column label="排序" prop="order" width="70" align="center" />
      <el-table-column label="负责人" prop="owner" width="100" align="center">
        <template #default="{ row }">{{ row.owner || '-' }}</template>
      </el-table-column>
      <el-table-column label="子模块" align="center" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.childCount > 0 ? 'primary' : 'info'">{{ row.childCount || 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm('create', undefined, row.id)"
                     v-hasPermi="['zentao:module:create']">添加子模块</el-button>
          <el-button link type="primary" @click="openForm('update', row.id)"
                     v-hasPermi="['zentao:module:update']">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)"
                     v-hasPermi="['zentao:module:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && filteredTree.length === 0" description="该树下还没有模块" />
  </ContentWrap>

  <ModuleForm ref="formRef" @success="getList" />
</template>

<script lang="ts" setup>
import * as ModuleApi from '@/api/zentao/module'
import * as ProductApi from '@/api/zentao/product'
import * as ExecutionApi from '@/api/zentao/execution'
import * as BranchApi from '@/api/zentao/branch'
import ModuleForm from './ModuleForm.vue'
import { isBranchAware, isProductRoot } from './constants'

defineOptions({ name: 'ZentaoModule' })

const message = useMessage()
const { t } = useI18n()

const loading = ref(true)
const typeList = ref<ModuleApi.ModuleTypeVO[]>([])
const productList = ref<ProductApi.ProductSimpleVO[]>([])
const executionList = ref<ExecutionApi.ExecutionVO[]>([])
const branchList = ref<BranchApi.BranchVO[]>([])
const tree = ref<ModuleApi.ModuleVO[]>([])
const keyword = ref('')
const queryParams = reactive({
  type: 'story',
  root: undefined as number | undefined,
  branch: 0 as number | undefined
})
const queryFormRef = ref()
const formRef = ref()

const branchAware = computed(() => isBranchAware(queryParams.type))
const rootLabel = computed(() => (isProductRoot(queryParams.type) ? '所属产品' : '所属执行'))
const rootOptions = computed(() => {
  if (queryParams.type === 'line') return [{ id: 0, name: '产品线（root=0）' }]
  if (isProductRoot(queryParams.type)) return productList.value.map((p) => ({ id: p.id, name: p.name }))
  return executionList.value.map((e) => ({ id: e.id!, name: e.name || '' }))
})

// 关键词过滤是纯前端的：过滤后保留命中节点的祖先链，避免树结构断裂
const filteredTree = computed(() => {
  const kw = keyword.value.trim()
  if (!kw) return tree.value
  const filter = (nodes: ModuleApi.ModuleVO[]): ModuleApi.ModuleVO[] =>
    nodes
      .map((n) => ({ ...n, children: filter(n.children || []) }))
      .filter((n) => (n.name || '').includes(kw) || (n.children && n.children.length > 0))
  return filter(tree.value)
})

const getList = async () => {
  loading.value = true
  try {
    if (!queryParams.root && queryParams.type !== 'line') {
      tree.value = []
      return
    }
    tree.value = await ModuleApi.getModuleTree({
      root: queryParams.type === 'line' ? 0 : queryParams.root!,
      type: queryParams.type,
      branch: branchAware.value ? queryParams.branch ?? 0 : undefined
    })
    if (branchAware.value && queryParams.root) {
      branchList.value = await BranchApi.getBranchListByProduct(queryParams.root)
    } else {
      branchList.value = []
    }
  } finally {
    loading.value = false
  }
}

const handleTypeChange = async () => {
  queryParams.root = undefined
  queryParams.branch = 0
  tree.value = []
  // 默认选第一个根对象，省一次点击
  if (queryParams.type === 'line') {
    queryParams.root = 0
  } else if (isProductRoot(queryParams.type)) {
    queryParams.root = productList.value[0]?.id
  } else {
    queryParams.root = executionList.value[0]?.id
  }
  await getList()
}

const openForm = (type: string, id?: number, parentId?: number) => formRef.value.open(type, id, parentId)

const handleDelete = async (row: ModuleApi.ModuleVO) => {
  try {
    await message.confirm(
      `确认删除模块「${row.name}」？它的 ${row.childCount || 0} 个子模块会一并删除，` +
        '原本挂在它下面的需求/任务/缺陷会自动改挂到上级模块。'
    )
    await ModuleApi.deleteModule(row.id!)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

onMounted(async () => {
  typeList.value = await ModuleApi.getModuleTypeList()
  productList.value = await ProductApi.getProductSimpleList()
  const execPage = await ExecutionApi.getExecutionPage({ pageNo: 1, pageSize: 100 })
  executionList.value = execPage.list
  queryParams.root = productList.value[0]?.id
  await getList()
})
</script>
