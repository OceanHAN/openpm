<template>
  <ContentWrap>
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="项目名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="名称关键词" clearable class="!w-200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="模型" prop="model">
        <el-select v-model="queryParams.model" placeholder="全部模型" clearable class="!w-160px">
          <el-option v-for="o in PROJECT_MODEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="!w-140px">
          <el-option v-for="o in PROJECT_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="所属项目集" prop="parent">
        <el-select v-model="queryParams.parent" placeholder="全部（含顶级）" clearable filterable
                   class="!w-200px" @change="handleQuery">
          <el-option v-for="p in programList" :key="p.id" :label="p.name!" :value="p.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="项目经理" prop="PM">
        <el-input v-model="queryParams.PM" placeholder="账号" clearable class="!w-140px" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:project:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增项目
        </el-button>
        <el-button type="danger" plain :disabled="checkedIds.length === 0" @click="handleDeleteBatch"
                   v-hasPermi="['zentao:project:delete']">
          <Icon icon="ep:delete" class="mr-5px" /> 批量删除
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" @selection-change="handleRowCheckboxChange">
      <el-table-column type="selection" width="55" />
      <el-table-column label="编号" align="center" prop="id" width="80" />
      <el-table-column label="项目名称" prop="name" min-width="200" show-overflow-tooltip />
      <el-table-column label="所属项目集" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ programName(row.parent) }}</template>
      </el-table-column>
      <el-table-column label="模型" align="center" width="110">
        <template #default="{ row }">{{ projectLabelOf(PROJECT_MODEL_OPTIONS, row.model) }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="{ row }">
          <el-tag :type="projectTagOf(row.status) as any">
            {{ projectLabelOf(PROJECT_STATUS_OPTIONS, row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="周期" align="center" width="200">
        <template #default="{ row }">
          {{ row.begin || '-' }} ~ {{ row.end || '-' }}
        </template>
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
      <el-table-column label="项目经理" align="center" prop="PM" width="100" />
      <el-table-column label="团队" align="center" width="80">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.teamCount }} 人</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="230" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'wait'" link type="success"
                     @click="handleAction('start', row)" v-hasPermi="['zentao:project:update']">开始</el-button>
          <el-button v-if="row.status === 'doing'" link type="warning"
                     @click="handleAction('suspend', row)" v-hasPermi="['zentao:project:update']">挂起</el-button>
          <el-button v-if="row.status === 'suspended'" link type="success"
                     @click="handleAction('activate', row)" v-hasPermi="['zentao:project:update']">激活</el-button>
          <el-button link type="primary" @click="teamRef.open(row, 'project')">团队</el-button>
          <el-button link type="primary" @click="stakeholderRef.open(row, 'project')">干系人</el-button>
          <el-dropdown class="ml-8px" @command="(cmd: string) => handleAction(cmd, row)">
            <el-button link type="primary">更多<Icon icon="ep:arrow-down" class="ml-2px" /></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit" :disabled="row.status === 'closed'"
                                   v-hasPermi="['zentao:project:update']">编辑</el-dropdown-item>
                <el-dropdown-item command="tasks">查看任务</el-dropdown-item>
                <el-dropdown-item command="close" :disabled="row.status === 'closed'" divided
                                   v-hasPermi="['zentao:project:update']">关闭</el-dropdown-item>
                <el-dropdown-item command="delete" divided
                                   v-hasPermi="['zentao:project:delete']">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize"
                @pagination="getList" />
  </ContentWrap>

  <ProjectForm ref="formRef" @success="getList" />
  <TeamPanel ref="teamRef" @success="getList" />
  <StakeholderPanel ref="stakeholderRef" />
</template>

<script lang="ts" setup>
import { useRouter } from 'vue-router'
import * as ProjectApi from '@/api/zentao/project'
import * as ProgramApi from '@/api/zentao/program'
import ProjectForm from './ProjectForm.vue'
import TeamPanel from '../components/TeamPanel.vue'
import StakeholderPanel from '../components/StakeholderPanel.vue'
import {
  PROJECT_STATUS_OPTIONS,
  PROJECT_MODEL_OPTIONS,
  projectLabelOf,
  projectTagOf
} from './constants'

defineOptions({ name: 'ZentaoProject' })

const message = useMessage()
const { t } = useI18n()
const router = useRouter()

const loading = ref(true)
const total = ref(0)
const list = ref<ProjectApi.ProjectVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: '',
  /** 所属项目集（项目靠 parent 指向项目集） */
  parent: undefined as number | undefined,
  model: undefined as string | undefined,
  status: undefined as string | undefined,
  PM: ''
})

// 项目集下拉：项目列表用它做 id → 名称映射与过滤
const programList = ref<ProgramApi.ProgramVO[]>([])
const programName = (id?: number) => {
  if (!id) return '（顶级）'
  const hit = programList.value.find((p) => p.id === id)
  return hit ? hit.name! : `#${id}`
}
const queryFormRef = ref()
const formRef = ref()
const teamRef = ref()
const stakeholderRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await ProjectApi.getProjectPage(queryParams)
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
  queryParams.parent = undefined
  queryFormRef.value.resetFields()
  queryParams.model = undefined
  queryParams.status = undefined
  handleQuery()
}

const openForm = (type: string, id?: number) => formRef.value.open(type, id)

const handleAction = async (cmd: string, row: ProjectApi.ProjectVO) => {
  const id = row.id!
  try {
    switch (cmd) {
      case 'edit':
        openForm('update', id)
        return
      case 'tasks':
        await router.push({ path: '/zentao/task', query: { project: id } })
        return
      case 'start':
        await message.confirm('确认开始该项目？将写入实际开始日期')
        await ProjectApi.startProject(id)
        message.success('项目已开始')
        break
      case 'suspend':
        await message.confirm('确认挂起该项目？')
        await ProjectApi.suspendProject(id)
        message.success('项目已挂起')
        break
      case 'activate':
        await message.confirm('确认激活该项目？')
        await ProjectApi.activateProject(id)
        message.success('项目已激活')
        break
      case 'close':
        await message.confirm(
          '确认关闭该项目？注意：单执行项目会连带关闭其执行，未关联产品时会连带关闭自动创建的产品'
        )
        await ProjectApi.closeProject(id, 'done')
        message.success('项目已关闭')
        break
      case 'delete':
        await message.delConfirm()
        await ProjectApi.deleteProject(id)
        message.success(t('common.delSuccess'))
        break
    }
    await getList()
  } catch {}
}

const checkedIds = ref<number[]>([])
const handleRowCheckboxChange = (rows: ProjectApi.ProjectVO[]) => {
  checkedIds.value = rows.map((row) => row.id!)
}

const handleDeleteBatch = async () => {
  try {
    await message.delConfirm()
    await ProjectApi.deleteProjectList(checkedIds.value)
    checkedIds.value = []
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

onMounted(async () => {
  getList()
  try {
    programList.value = await ProgramApi.getProgramList()
  } catch {
    programList.value = []
  }
})
</script>
