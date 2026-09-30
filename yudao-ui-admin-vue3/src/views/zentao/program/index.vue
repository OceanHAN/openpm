<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="项目集、项目、执行是同一张表（zt_project）"
      description="禅道 config/zentaopms.php 里 TABLE_PROGRAM / TABLE_PROJECT / TABLE_EXECUTION 三个常量都指向 zt_project，靠 type 区分：program=项目集、project=项目、sprint/stage/kanban=执行。项目用 parent 指向所属项目集，path 是逗号格式的层级链（,9001,9002,），顶级项目集 grade=1。产品用 zt_product.program 归属项目集。"
    />

    <el-form class="-mb-15px" :inline="true" label-width="86px">
      <el-form-item label="名称">
        <el-input v-model="queryParams.name" placeholder="名称关键词" clearable class="!w-180px" @keyup.enter="getList" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部" class="!w-140px" @change="getList">
          <el-option v-for="o in PROGRAM_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="负责人">
        <el-input v-model="queryParams.PM" placeholder="账号" clearable class="!w-140px" @keyup.enter="getList" />
      </el-form-item>
      <el-form-item>
        <el-button @click="getList"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:program:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新建项目集
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="tree" row-key="id" default-expand-all
              :tree-props="{ children: 'children' }" empty-text="没有符合条件的项目集">
      <el-table-column label="项目集" prop="name" min-width="240">
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.name }}</el-link>
          <el-tag v-if="row.code" size="small" type="info" class="ml-8px">{{ row.code }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="层级" align="center" prop="grade" width="70" />
      <el-table-column label="path" prop="path" width="150" show-overflow-tooltip />
      <el-table-column label="状态" align="center" width="90">
        <template #default="{ row }">
          <el-tag :type="programTagOf(row.status) as any">{{ programLabelOf(PROGRAM_STATUS_OPTIONS, row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="负责人" align="center" prop="PM" width="100" />
      <el-table-column label="下级项目集" align="center" prop="childCount" width="90" />
      <el-table-column label="项目" align="center" width="70">
        <template #default="{ row }">{{ row.projectCount }}</template>
      </el-table-column>
      <el-table-column label="产品" align="center" width="70">
        <template #default="{ row }">{{ row.productCount }}</template>
      </el-table-column>
      <el-table-column label="计划起止" align="center" width="190">
        <template #default="{ row }">{{ row.begin || '-' }} ~ {{ row.end || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="250" fixed="right">
        <template #default="{ row }">
          <el-button v-if="['wait','suspended'].includes(row.status)" link type="success"
                     @click="handleAction('start', row)" v-hasPermi="['zentao:program:update']">开始</el-button>
          <el-button v-if="row.status === 'doing'" link type="warning"
                     @click="handleAction('suspend', row)" v-hasPermi="['zentao:program:update']">挂起</el-button>
          <el-button v-if="row.status === 'suspended'" link type="primary"
                     @click="handleAction('activate', row)" v-hasPermi="['zentao:program:update']">激活</el-button>
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="primary" @click="stakeholderRef.open(row, 'program')">干系人</el-button>
          <el-dropdown class="ml-8px" @command="(cmd: string) => handleAction(cmd, row)">
            <el-button link type="primary">更多<Icon icon="ep:arrow-down" class="ml-2px" /></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit" :disabled="row.status === 'closed'"
                                   v-hasPermi="['zentao:program:update']">编辑</el-dropdown-item>
                <el-dropdown-item command="create-child"
                                   v-hasPermi="['zentao:program:create']">新建子项目集</el-dropdown-item>
                <el-dropdown-item command="close" :disabled="row.status === 'closed'"
                                   v-hasPermi="['zentao:program:update']">关闭</el-dropdown-item>
                <el-dropdown-item command="delete" divided
                                   v-hasPermi="['zentao:program:delete']">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <!-- 详情：项目集下的项目 / 产品 -->
  <el-drawer v-model="detailVisible" :title="`项目集详情：${detail?.name || ''}`" size="720px" append-to-body>
    <el-descriptions :column="2" border class="mb-15px">
      <el-descriptions-item label="编号">{{ detail?.id }}</el-descriptions-item>
      <el-descriptions-item label="层级路径">{{ detail?.path }}（grade={{ detail?.grade }}）</el-descriptions-item>
      <el-descriptions-item label="上级项目集">{{ detail?.parentName || '（顶级）' }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="programTagOf(detail?.status) as any">{{ programLabelOf(PROGRAM_STATUS_OPTIONS, detail?.status) }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="负责人">{{ detail?.PM || '-' }}</el-descriptions-item>
      <el-descriptions-item label="预算">{{ detail?.budget ?? 0 }} {{ detail?.budgetUnit || 'CNY' }}</el-descriptions-item>
    </el-descriptions>

    <el-tabs v-model="detailTab">
      <el-tab-pane label="项目" name="project">
        <el-table :data="detailProjects" border size="small" empty-text="这个项目集下还没有项目">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="项目名称" prop="name" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" prop="status" width="90" align="center" />
          <el-table-column label="path" prop="path" width="150" show-overflow-tooltip />
          <el-table-column label="负责人" prop="PM" width="90" align="center" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="产品" name="product">
        <el-table :data="detailProducts" border size="small" empty-text="这个项目集下还没有产品">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="产品名称" prop="name" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" prop="status" width="90" align="center" />
          <el-table-column label="类型" prop="type" width="100" align="center" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>

  <ProgramForm ref="formRef" @success="getList" />
  <StakeholderPanel ref="stakeholderRef" />
</template>

<script lang="ts" setup>
import * as ProgramApi from '@/api/zentao/program'
import ProgramForm from './ProgramForm.vue'
import StakeholderPanel from '../components/StakeholderPanel.vue'
import { PROGRAM_STATUS_OPTIONS, programLabelOf, programTagOf } from './constants'

defineOptions({ name: 'ZentaoProgram' })

const message = useMessage()
const loading = ref(false)
const list = ref<ProgramApi.ProgramVO[]>([])
const formRef = ref()
const stakeholderRef = ref()
const queryParams = reactive({
  name: '',
  status: undefined as string | undefined,
  PM: ''
})

/** 后端给的是平铺列表，这里按 parent 建成树（项目集就是一张自关联的表） */
const tree = computed(() => {
  const nodes = new Map<number, ProgramApi.ProgramVO>()
  list.value.forEach((p) => nodes.set(p.id!, { ...p, children: [] }))
  const roots: ProgramApi.ProgramVO[] = []
  nodes.forEach((node) => {
    const parent = node.parent ? nodes.get(node.parent) : undefined
    if (parent) parent.children!.push(node)
    else roots.push(node)
  })
  return roots
})

const getList = async () => {
  loading.value = true
  try {
    // 页面用 /list（含统计）而不是 /page：项目集数量不大，一次取完在前端建树更直观
    list.value = await ProgramApi.getProgramList()
    if (queryParams.name) {
      list.value = list.value.filter((p) => (p.name || '').includes(queryParams.name))
    }
    if (queryParams.status) {
      list.value = list.value.filter((p) => p.status === queryParams.status)
    }
    if (queryParams.PM) {
      list.value = list.value.filter((p) => p.PM === queryParams.PM)
    }
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  queryParams.name = ''
  queryParams.status = undefined
  queryParams.PM = ''
  return getList()
}

const openForm = (mode: 'create' | 'edit', row?: ProgramApi.ProgramVO, defaultParent?: number) =>
  formRef.value.open(mode, row, defaultParent)

// ==================== 详情 ====================
const detailVisible = ref(false)
const detail = ref<ProgramApi.ProgramVO>()
const detailTab = ref('project')
const detailProjects = ref<any[]>([])
const detailProducts = ref<any[]>([])

const openDetail = async (row: ProgramApi.ProgramVO) => {
  detail.value = row
  detailTab.value = 'project'
  detailVisible.value = true
  detailProjects.value = await ProgramApi.getProgramProjectList(row.id!)
  detailProducts.value = await ProgramApi.getProgramProductList(row.id!)
}

// ==================== 状态流转 / 删除 ====================
const handleAction = async (cmd: string, row: ProgramApi.ProgramVO) => {
  const id = row.id!
  try {
    switch (cmd) {
      case 'edit':
        openForm('edit', row)
        return
      case 'create-child':
        openForm('create', undefined, id)
        return
      case 'start':
        await message.confirm(`确认开始项目集「${row.name}」？`)
        await ProgramApi.startProgram(id)
        message.success('项目集已开始')
        break
      case 'suspend':
        await message.confirm(`确认挂起项目集「${row.name}」？`)
        await ProgramApi.suspendProgram(id)
        message.success('项目集已挂起')
        break
      case 'activate':
        await message.confirm(`确认激活项目集「${row.name}」？`)
        await ProgramApi.activateProgram(id)
        message.success('项目集已激活')
        break
      case 'close':
        await message.confirm(`确认关闭项目集「${row.name}」？`)
        await ProgramApi.closeProgram(id, 'done')
        message.success('项目集已关闭')
        break
      case 'delete':
        await message.delConfirm(`确认删除项目集「${row.name}」？下级还有项目集/项目/产品时会拒绝`)
        await ProgramApi.deleteProgram(id)
        message.success('项目集已删除')
        break
    }
    await getList()
  } catch {}
}

onMounted(getList)
</script>
