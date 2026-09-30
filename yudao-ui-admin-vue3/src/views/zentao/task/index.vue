<template>
  <ContentWrap>
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="任务名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="名称关键词" clearable class="!w-200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="所属项目" prop="project">
        <el-input-number v-model="queryParams.project" :min="0" :controls="false" placeholder="项目编号" class="!w-140px" />
      </el-form-item>
      <el-form-item label="所属执行" prop="execution">
        <el-select v-model="queryParams.execution" placeholder="全部执行" clearable filterable class="!w-200px"
                   @change="handleExecutionChange">
          <el-option v-for="e in executionList" :key="e.id" :label="e.name" :value="e.id!" />
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
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="!w-160px">
          <el-option v-for="o in TASK_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="指派给" prop="assignedTo">
        <el-input v-model="queryParams.assignedTo" placeholder="账号" clearable class="!w-140px" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:task:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增任务
        </el-button>
        <el-button type="danger" plain :disabled="checkedIds.length === 0" @click="handleDeleteBatch"
                   v-hasPermi="['zentao:task:delete']">
          <Icon icon="ep:delete" class="mr-5px" /> 批量删除
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" @selection-change="handleRowCheckboxChange">
      <el-table-column type="selection" width="55" />
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="任务名称" prop="name" min-width="200" show-overflow-tooltip />
      <el-table-column label="所属执行" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ executionNameOf(row.execution) }}</template>
      </el-table-column>
      <el-table-column label="模块" align="center" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ moduleName(row.module) }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="{ row }">
          <el-tag :type="taskTagOf(row.status) as any">
            {{ taskLabelOf(TASK_STATUS_OPTIONS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" align="center" width="90">
        <template #default="{ row }">{{ taskLabelOf(TASK_TYPE_OPTIONS, row.type) }}</template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="pri" width="80" />
      <el-table-column label="预计" align="center" prop="estimate" width="80" />
      <el-table-column label="已消耗" align="center" prop="consumed" width="80" />
      <el-table-column label="剩余" align="center" width="80">
        <template #default="{ row }">
          <span :class="Number(row.left) > 0 ? 'text-orange-500' : 'text-green-600'">
            {{ row.left }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="指派给" align="center" prop="assignedTo" width="110" />
      <el-table-column label="创建人" align="center" prop="openedBy" width="100" />
      <el-table-column label="操作" align="center" width="240" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'wait'" link type="success"
                     @click="handleAction('start', row)" v-hasPermi="['zentao:task:update']">开始</el-button>
          <el-button v-if="['wait','doing','pause'].includes(row.status)" link type="primary"
                     @click="finishRef.open(row.id)" v-hasPermi="['zentao:task:update']">完成</el-button>
          <el-button link type="primary" @click="effortRef.open(row)">工时</el-button>
          <el-dropdown class="ml-8px" @command="(cmd: string) => handleAction(cmd, row)">
            <el-button link type="primary">更多<Icon icon="ep:arrow-down" class="ml-2px" /></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit" :disabled="['closed','cancel'].includes(row.status)"
                                   v-hasPermi="['zentao:task:update']">编辑</el-dropdown-item>
                <el-dropdown-item command="close" :disabled="row.status !== 'done'"
                                   v-hasPermi="['zentao:task:update']">关闭</el-dropdown-item>
                <el-dropdown-item command="cancel" :disabled="['closed','cancel'].includes(row.status)"
                                   v-hasPermi="['zentao:task:update']">取消</el-dropdown-item>
                <el-dropdown-item command="activate" :disabled="!['closed','cancel'].includes(row.status)"
                                   v-hasPermi="['zentao:task:update']">激活</el-dropdown-item>
                <el-dropdown-item command="delete" divided
                                   v-hasPermi="['zentao:task:delete']">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize"
                @pagination="getList" />
  </ContentWrap>

  <TaskForm ref="formRef" @success="getList" />
  <TaskFinishForm ref="finishRef" @success="getList" />
  <EffortPanel ref="effortRef" @success="getList" />
</template>

<script lang="ts" setup>
import * as TaskApi from '@/api/zentao/task'
import * as ExecutionApi from '@/api/zentao/execution'
import * as ModuleApi from '@/api/zentao/module'
import TaskForm from './TaskForm.vue'
import TaskFinishForm from './TaskFinishForm.vue'
import EffortPanel from '../components/EffortPanel.vue'
import { TASK_STATUS_OPTIONS, TASK_TYPE_OPTIONS, taskLabelOf, taskTagOf } from './constants'

defineOptions({ name: 'ZentaoTask' })

const message = useMessage()
const { t } = useI18n()
const route = useRoute()

const loading = ref(true)
const total = ref(0)
const list = ref<TaskApi.TaskVO[]>([])
const executionList = ref<ExecutionApi.ExecutionVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: '',
  project: undefined as number | undefined,
  execution: undefined as number | undefined,
  module: undefined as number | undefined,
  status: undefined as string | undefined,
  assignedTo: ''
})
const queryFormRef = ref()
const formRef = ref()
const finishRef = ref()
const effortRef = ref()

// 任务挂在执行下，这里用执行下拉做本地 id → 名称映射
const executionNameOf = (execution?: number) => {
  if (!execution) return '-'
  const hit = executionList.value.find((e) => e.id === execution)
  return hit ? hit.name : `#${execution}`
}

const moduleTree = ref<ModuleApi.ModuleVO[]>([])

// 任务模块树挂在执行下：选了执行才拉树
const loadModules = async () => {
  if (!queryParams.execution) {
    moduleTree.value = []
    return
  }
  moduleTree.value = await ModuleApi.getModuleTree({ root: queryParams.execution, type: 'task' })
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

const handleExecutionChange = async () => {
  queryParams.module = undefined
  await loadModules()
  handleQuery()
}

const loadExecutions = async () => {
  try {
    const data = await ExecutionApi.getExecutionPage({ pageNo: 1, pageSize: 100 })
    executionList.value = data.list
  } catch {
    executionList.value = []
  }
}

const getList = async () => {
  loading.value = true
  try {
    const data = await TaskApi.getTaskPage(queryParams)
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
  queryParams.project = undefined
  queryParams.execution = undefined
  queryParams.module = undefined
  moduleTree.value = []
  queryParams.status = undefined
  handleQuery()
}

const openForm = (type: string, id?: number) => formRef.value.open(type, id)

const handleAction = async (cmd: string, row: TaskApi.TaskVO) => {
  const id = row.id!
  try {
    switch (cmd) {
      case 'edit':
        openForm('update', id)
        return
      case 'start':
        await message.confirm('确认开始该任务？')
        await TaskApi.startTask(id)
        message.success('任务已开始')
        break
      case 'close':
        await message.confirm('确认关闭该任务？')
        await TaskApi.closeTask(id, 'done')
        message.success('任务已关闭')
        break
      case 'cancel':
        await message.confirm('确认取消该任务？')
        await TaskApi.cancelTask(id)
        message.success('任务已取消')
        break
      case 'activate':
        await message.confirm('确认激活该任务？')
        await TaskApi.activateTask(id)
        message.success('任务已激活')
        break
      case 'delete':
        await message.delConfirm()
        await TaskApi.deleteTask(id)
        message.success(t('common.delSuccess'))
        break
    }
    await getList()
  } catch {}
}

const checkedIds = ref<number[]>([])
const handleRowCheckboxChange = (rows: TaskApi.TaskVO[]) => {
  checkedIds.value = rows.map((row) => row.id!)
}

const handleDeleteBatch = async () => {
  try {
    await message.delConfirm()
    await TaskApi.deleteTaskList(checkedIds.value)
    checkedIds.value = []
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

onMounted(async () => {
  await loadExecutions()
  // 支持从执行/项目页带过滤条件跳过来
  if (route.query.execution) queryParams.execution = Number(route.query.execution)
  if (route.query.project) queryParams.project = Number(route.query.project)
  if (queryParams.execution) await loadModules()
  getList()
})
</script>
