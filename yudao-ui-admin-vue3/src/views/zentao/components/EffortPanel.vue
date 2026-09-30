<template>
  <el-drawer v-model="visible" :title="`工时明细：${stat.taskName || '#' + taskId}`" size="860px" append-to-body>
    <div v-loading="loading">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb-15px"
        title="已消耗是所有工时之和，剩余看最后一条"
        description="每登记一条工时，都顺手声明一次「这之后还剩多少」。所以同一批活干下来，剩余可能忽高忽低 —— 那是有人在修正估时，不是算错。剩余归零，任务自动变成已完成。"
      />

      <el-descriptions :column="4" border class="mb-15px">
        <el-descriptions-item label="预计工时">{{ num(stat.estimate) }}</el-descriptions-item>
        <el-descriptions-item label="已消耗">{{ num(stat.consumed) }}</el-descriptions-item>
        <el-descriptions-item label="剩余">
          <span :class="Number(stat.left) > 0 ? 'text-orange-500' : 'text-green-600'">{{ num(stat.left) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="任务状态">
          <el-tag :type="taskTagOf(stat.status!) as any">{{ taskLabelOf(TASK_STATUS_OPTIONS, stat.status!) }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div class="mb-10px">
        <el-button type="primary" plain @click="formRef.open({ id: taskId, name: stat.taskName })"
                   v-hasPermi="['zentao:effort:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 登记工时
        </el-button>
        <span class="ml-10px text-gray-500">共 {{ stat.effortCount || 0 }} 条记录</span>
      </div>

      <el-table :data="stat.efforts || []" border size="small" empty-text="还没有登记过工时">
        <el-table-column label="日期" align="center" prop="date" width="110" />
        <el-table-column label="谁" align="center" prop="account" width="110" />
        <el-table-column label="工作内容" prop="work" min-width="220" show-overflow-tooltip />
        <el-table-column label="消耗" align="center" prop="consumed" width="80" />
        <el-table-column label="剩余" align="center" prop="left" width="80" />
        <el-table-column label="起止" align="center" width="110">
          <template #default="{ row }">{{ row.begin || row.end ? `${row.begin || '?'}~${row.end || '?'}` : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="formRef.open({ id: taskId, name: stat.taskName }, row)"
                       v-hasPermi="['zentao:effort:update']">修改</el-button>
            <el-button link type="danger" @click="handleDelete(row)"
                       v-hasPermi="['zentao:effort:delete']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <EffortForm ref="formRef" @success="load" />
  </el-drawer>
</template>

<script lang="ts" setup>
import * as EffortApi from '@/api/zentao/effort'
import EffortForm from './EffortForm.vue'
import { TASK_STATUS_OPTIONS, taskLabelOf, taskTagOf } from '../task/constants'

defineOptions({ name: 'ZentaoEffortPanel' })

const emit = defineEmits(['success'])
const message = useMessage()

const visible = ref(false)
const loading = ref(false)
const taskId = ref<number>()
const stat = ref<EffortApi.EffortTaskStatVO>({})
const formRef = ref()

// 后端用 BigDecimal 返回，JSON 里已经是数字，直接显示即可（别自作聪明去尾零，会把 10 变成 1）
const num = (v?: number) => (v === null || v === undefined ? '-' : String(v))

const load = async () => {
  if (!taskId.value) return
  loading.value = true
  try {
    stat.value = await EffortApi.getTaskStat(taskId.value)
  } finally {
    loading.value = false
  }
}

const open = async (row: { id?: number; name?: string }) => {
  taskId.value = row.id
  stat.value = { taskName: row.name }
  visible.value = true
  await load()
}

const handleDelete = async (row: EffortApi.EffortVO) => {
  await message.delConfirm(`确认删除「${row.date} ${row.work || ''}」这条工时？任务的已消耗/剩余会跟着重算`)
  await EffortApi.deleteEffort(row.id!)
  message.success('工时已删除')
  await load()
  // 任务列表的已消耗/剩余也变了，通知外面刷新
  emit('success')
}

defineExpose({ open })
</script>
