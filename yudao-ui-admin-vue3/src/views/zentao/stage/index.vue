<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="禅道把「阶段模板」和「项目阶段」分开"
      description="zt_stage 是流程模板（一套瀑布流程有哪些阶段、各占多少工作量），项目里的实际阶段是 zt_project 里 type='stage' 的记录，按模板生成。阶段与执行共用一张表，所以状态流转直接复用执行模块。"
    />

    <el-form class="-mb-15px" :inline="true" label-width="100px">
      <el-form-item label="流程模板组">
        <el-input-number v-model="workflowGroup" :min="1" :controls="false" class="!w-120px" @change="loadTemplates" />
      </el-form-item>
      <el-form-item label="项目流程类型">
        <el-select v-model="projectType" class="!w-160px" @change="loadTemplates">
          <el-option v-for="o in PROJECT_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="loadTemplates"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['zentao:stage:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新建阶段
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <template #header>
      <span>阶段模板（流程组 {{ workflowGroup }}）</span>
      <el-tag class="ml-8px" :type="totalPercent > 100 ? 'danger' : 'success'">
        占比合计 {{ totalPercent }}%
      </el-tag>
    </template>
    <el-table v-loading="loading" :data="templates" empty-text="该流程组下还没有阶段模板">
      <el-table-column label="排序" align="center" prop="order" width="70" />
      <el-table-column label="阶段名称" prop="name" min-width="160" show-overflow-tooltip />
      <el-table-column label="类型" align="center" width="110">
        <template #default="{ row }">
          <el-tag :type="stageTypeTagOf(row.type) as any">{{ row.typeName || stageLabelOf(STAGE_TYPE_OPTIONS, row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="工作量占比" align="center" width="120">
        <template #default="{ row }">{{ row.percent || '-' }}%</template>
      </el-table-column>
      <el-table-column label="项目流程" align="center" prop="projectType" width="120" />
      <el-table-column label="创建人" align="center" prop="createdBy" width="100" />
      <el-table-column label="操作" align="center" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm('update', row.id)"
                     v-hasPermi="['zentao:stage:update']">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)"
                     v-hasPermi="['zentao:stage:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <ContentWrap>
    <template #header>
      <span>项目阶段</span>
      <el-select v-model="projectId" placeholder="请选择项目" filterable class="!w-240px ml-12px" @change="loadProjectStages">
        <el-option v-for="p in projectList" :key="p.id" :label="p.name" :value="p.id!" />
      </el-select>
      <el-button class="ml-8px" type="primary" :disabled="!projectId" @click="handleGenerate"
                 v-hasPermi="['zentao:stage:create']">按模板生成阶段</el-button>
      <el-button class="ml-8px" type="danger" plain :disabled="projectStages.length === 0" @click="handleDeleteStages"
                 v-hasPermi="['zentao:stage:delete']">删除项目阶段</el-button>
    </template>
    <el-table v-loading="projectLoading" :data="projectStages" empty-text="该项目还没有生成阶段">
      <el-table-column label="编号" align="center" prop="id" width="100" />
      <el-table-column label="阶段" prop="name" min-width="140" show-overflow-tooltip />
      <el-table-column label="占比" align="center" width="90">
        <template #default="{ row }">{{ row.percent || '-' }}%</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="{ row }">
          <el-tag :type="projectStatusTagOf(row.status) as any">{{ row.statusName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="周期" align="center" width="200">
        <template #default="{ row }">{{ row.begin || '-' }} ~ {{ row.end || '-' }}</template>
      </el-table-column>
      <el-table-column label="预计" align="center" prop="estimate" width="80" />
      <el-table-column label="已消耗" align="center" prop="consumed" width="80" />
      <el-table-column label="剩余" align="center" prop="left" width="80" />
      <el-table-column label="进度" align="center" width="90">
        <template #default="{ row }">{{ row.progress }}%</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="170" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'wait'" link type="success"
                     @click="handleStart(row)" v-hasPermi="['zentao:execution:update']">开始</el-button>
          <el-button v-if="row.status === 'doing'" link type="primary"
                     @click="handleClose(row)" v-hasPermi="['zentao:execution:update']">关闭</el-button>
          <el-button link type="primary" @click="goExecution(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <StageForm ref="formRef" @success="loadTemplates" />
</template>

<script lang="ts" setup>
import { useRouter } from 'vue-router'
import * as StageApi from '@/api/zentao/stage'
import * as ProjectApi from '@/api/zentao/project'
import * as ExecutionApi from '@/api/zentao/execution'
import StageForm from './StageForm.vue'
import {
  STAGE_TYPE_OPTIONS,
  PROJECT_TYPE_OPTIONS,
  stageLabelOf,
  stageTypeTagOf
} from './constants'

defineOptions({ name: 'ZentaoStage' })

const message = useMessage()
const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const projectLoading = ref(false)
const workflowGroup = ref(1)
const projectType = ref('waterfall')
const totalPercent = ref(0)
const templates = ref<StageApi.StageVO[]>([])
const projectList = ref<ProjectApi.ProjectVO[]>([])
const projectId = ref<number>()
const projectStages = ref<StageApi.StageVO[]>([])
const formRef = ref()

const projectStatusTagOf = (status?: string) => {
  if (status === 'closed') return 'info'
  if (status === 'doing') return 'primary'
  if (status === 'suspended') return 'warning'
  return 'info'
}

const loadTemplates = async () => {
  loading.value = true
  try {
    templates.value = await StageApi.getStageList(workflowGroup.value)
    totalPercent.value = await StageApi.getTotalPercent(workflowGroup.value)
  } finally {
    loading.value = false
  }
}

const loadProjectStages = async () => {
  if (!projectId.value) {
    projectStages.value = []
    return
  }
  projectLoading.value = true
  try {
    projectStages.value = await StageApi.getProjectStages(projectId.value)
  } finally {
    projectLoading.value = false
  }
}

const openForm = (type: string, id?: number) => formRef.value.open(type, id, workflowGroup.value)

const handleDelete = async (row: StageApi.StageVO) => {
  try {
    await message.delConfirm(`确认删除阶段模板「${row.name}」？已生成到项目里的阶段不受影响。`)
    await StageApi.deleteStage(row.id!)
    message.success(t('common.delSuccess'))
    await loadTemplates()
  } catch {}
}

const handleGenerate = async () => {
  try {
    await message.confirm(
      `确认按流程组 ${workflowGroup.value} 为项目生成阶段？项目已有阶段时会拒绝（需先删除）。`
    )
    const ids = await StageApi.generateStages(projectId.value!, workflowGroup.value)
    message.success(`已生成 ${ids.length} 个阶段`)
    await loadProjectStages()
  } catch {}
}

const handleDeleteStages = async () => {
  try {
    await message.delConfirm('确认删除该项目的全部阶段？删除后可以按模板重新生成。')
    await StageApi.deleteProjectStages(projectId.value!)
    message.success('已删除')
    await loadProjectStages()
  } catch {}
}

const handleStart = async (row: StageApi.StageVO) => {
  try {
    await message.confirm(`确认开始阶段「${row.name}」？`)
    await ExecutionApi.startExecution(row.id!)
    message.success('阶段已开始')
    await loadProjectStages()
  } catch {}
}

const handleClose = async (row: StageApi.StageVO) => {
  try {
    await message.confirm(`确认关闭阶段「${row.name}」？`)
    await ExecutionApi.closeExecution(row.id!, 'done')
    message.success('阶段已关闭')
    await loadProjectStages()
  } catch {}
}

const goExecution = (row: StageApi.StageVO) => {
  router.push({ path: '/zentao/execution', query: { id: row.id } })
}

onMounted(async () => {
  projectList.value = await ProjectApi.getProjectSimpleList()
  await loadTemplates()
})
</script>
