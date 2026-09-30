<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="执行（迭代 / 阶段 / 看板）与项目共用 zt_project 表"
      description="禅道的执行没有独立数据表，和项目同表、靠 type 区分。本页只展示 type 为 sprint/stage/kanban 的记录，项目请到「项目管理」查看。"
    />

    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="执行名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="名称关键词" clearable class="!w-200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="所属项目" prop="project">
        <el-select v-model="queryParams.project" placeholder="全部项目" clearable filterable class="!w-220px">
          <el-option v-for="p in projectList" :key="p.id" :label="p.name" :value="p.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-select v-model="queryParams.type" placeholder="全部类型" clearable class="!w-140px">
          <el-option v-for="o in EXECUTION_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="!w-140px">
          <el-option v-for="o in EXECUTION_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:execution:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增执行
        </el-button>
        <el-button type="danger" plain :disabled="checkedIds.length === 0" @click="handleDeleteBatch"
                   v-hasPermi="['zentao:execution:delete']">
          <Icon icon="ep:delete" class="mr-5px" /> 批量删除
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" @selection-change="handleRowCheckboxChange">
      <el-table-column type="selection" width="55" />
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="执行名称" prop="name" min-width="180" show-overflow-tooltip />
      <el-table-column label="所属项目" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ projectNameOf(row.project) }}</template>
      </el-table-column>
      <el-table-column label="类型" align="center" width="100">
        <template #default="{ row }">
          <el-tag :type="executionTypeTagOf(row.type) as any">
            {{ executionLabelOf(EXECUTION_TYPE_OPTIONS, row.type) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="{ row }">
          <el-tag :type="executionTagOf(row.status) as any">
            {{ executionLabelOf(EXECUTION_STATUS_OPTIONS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="周期" align="center" width="200">
        <template #default="{ row }">{{ row.begin || '-' }} ~ {{ row.end || '-' }}</template>
      </el-table-column>
      <el-table-column label="预计" align="center" prop="estimate" width="80" />
      <el-table-column label="已消耗" align="center" prop="consumed" width="80" />
      <el-table-column label="剩余" align="center" width="80">
        <template #default="{ row }">
          <span :class="Number(row.left) > 0 ? 'text-orange-500' : 'text-green-600'">{{ row.left }}</span>
        </template>
      </el-table-column>
      <el-table-column label="进度" align="center" width="90">
        <template #default="{ row }">{{ row.progress }}%</template>
      </el-table-column>
      <el-table-column label="负责人" align="center" prop="PM" width="100" />
      <el-table-column label="团队" align="center" width="80">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.teamCount }} 人</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="240" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'wait'" link type="success"
                     @click="handleAction('start', row)" v-hasPermi="['zentao:execution:update']">开始</el-button>
          <el-button v-if="row.status === 'doing'" link type="warning"
                     @click="handleAction('suspend', row)" v-hasPermi="['zentao:execution:update']">挂起</el-button>
          <el-button v-if="row.status === 'suspended'" link type="success"
                     @click="handleAction('activate', row)" v-hasPermi="['zentao:execution:update']">激活</el-button>
          <el-button link type="primary" @click="burnRef.open(row)"
                     v-hasPermi="['zentao:execution:burn']">燃尽图</el-button>
          <el-button link type="primary" @click="teamRef.open(row, 'execution')">团队</el-button>
          <el-dropdown class="ml-8px" @command="(cmd: string) => handleAction(cmd, row)">
            <el-button link type="primary">更多<Icon icon="ep:arrow-down" class="ml-2px" /></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit" :disabled="row.status === 'closed'"
                                   v-hasPermi="['zentao:execution:update']">编辑</el-dropdown-item>
                <el-dropdown-item command="tasks">查看任务</el-dropdown-item>
                <el-dropdown-item command="close" :disabled="row.status === 'closed'" divided
                                   v-hasPermi="['zentao:execution:update']">关闭</el-dropdown-item>
                <el-dropdown-item command="delete" divided
                                   v-hasPermi="['zentao:execution:delete']">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize"
                @pagination="getList" />
  </ContentWrap>

  <ExecutionForm ref="formRef" @success="getList" />
  <BurnChart ref="burnRef" />
  <TeamPanel ref="teamRef" @success="getList" />
</template>

<script lang="ts" setup>
import { useRouter } from 'vue-router'
import * as ExecutionApi from '@/api/zentao/execution'
import * as ProjectApi from '@/api/zentao/project'
import ExecutionForm from './ExecutionForm.vue'
import BurnChart from '../components/BurnChart.vue'
import TeamPanel from '../components/TeamPanel.vue'
import {
  EXECUTION_TYPE_OPTIONS,
  EXECUTION_STATUS_OPTIONS,
  executionLabelOf,
  executionTagOf,
  executionTypeTagOf
} from './constants'

defineOptions({ name: 'ZentaoExecution' })

const message = useMessage()
const { t } = useI18n()
const router = useRouter()

const burnRef = ref()
const loading = ref(true)
const total = ref(0)
const list = ref<ExecutionApi.ExecutionVO[]>([])
const projectList = ref<ProjectApi.ProjectVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: '',
  project: undefined as number | undefined,
  type: undefined as string | undefined,
  status: undefined as string | undefined
})
const queryFormRef = ref()
const formRef = ref()
const teamRef = ref()

// 所属项目名称：执行表里只有 project 外键，用项目下拉数据做本地映射
const projectNameOf = (project?: number) => {
  if (!project) return '-'
  const hit = projectList.value.find((p) => p.id === project)
  return hit ? hit.name : `#${project}`
}

const getList = async () => {
  loading.value = true
  try {
    const data = await ExecutionApi.getExecutionPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const loadProjects = async () => {
  try {
    projectList.value = await ProjectApi.getProjectSimpleList()
  } catch {
    projectList.value = []
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  queryParams.project = undefined
  queryParams.type = undefined
  queryParams.status = undefined
  handleQuery()
}

const openForm = (type: string, id?: number) => formRef.value.open(type, id)

const handleAction = async (cmd: string, row: ExecutionApi.ExecutionVO) => {
  const id = row.id!
  try {
    switch (cmd) {
      case 'edit':
        openForm('update', id)
        return
      case 'tasks':
        // 任务页按执行过滤（任务挂在执行下）
        await router.push({ path: '/zentao/task', query: { execution: id } })
        return
      case 'start':
        await message.confirm('确认开始该执行？将写入实际开始日期')
        await ExecutionApi.startExecution(id)
        message.success('执行已开始')
        break
      case 'suspend':
        await message.confirm('确认挂起该执行？')
        await ExecutionApi.suspendExecution(id)
        message.success('执行已挂起')
        break
      case 'activate':
        await message.confirm('确认激活该执行？')
        await ExecutionApi.activateExecution(id)
        message.success('执行已激活')
        break
      case 'close':
        await message.confirm('确认关闭该执行？')
        await ExecutionApi.closeExecution(id, 'done')
        message.success('执行已关闭')
        break
      case 'delete':
        await message.delConfirm()
        await ExecutionApi.deleteExecution(id)
        message.success(t('common.delSuccess'))
        break
    }
    await getList()
  } catch {}
}

const checkedIds = ref<number[]>([])
const handleRowCheckboxChange = (rows: ExecutionApi.ExecutionVO[]) => {
  checkedIds.value = rows.map((row) => row.id!)
}

const handleDeleteBatch = async () => {
  try {
    await message.delConfirm()
    await ExecutionApi.deleteExecutionList(checkedIds.value)
    checkedIds.value = []
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

onMounted(() => {
  loadProjects()
  // 支持从项目页带 project 参数跳过来
  const project = router.currentRoute.value.query.project
  if (project) queryParams.project = Number(project)
  getList()
})
</script>
