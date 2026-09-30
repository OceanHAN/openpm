<template>
  <el-drawer v-model="drawerVisible" :title="`关联需求：${plan?.title || ''}`" size="60%">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="研发需求（type=story）独占一个计划"
      description="禅道规则：type=story 的研发需求同一时间只属于一个计划，挂到新计划会自动从旧计划移走；原始需求/史诗可以同时挂在多个计划上（计划字段变成逗号列表）。"
    />
    <el-row :gutter="16">
      <el-col :span="12">
        <div class="mb-8px font-bold">已关联（{{ linked.length }}）</div>
        <el-table :data="linked" v-loading="loading" height="460" size="small">
          <el-table-column label="编号" prop="id" width="70" />
          <el-table-column label="标题" prop="title" show-overflow-tooltip />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">{{ labelOf(STORY_STATUS_OPTIONS, row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ row }">
              <el-button link type="danger" @click="handleUnlink(row)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
      <el-col :span="12">
        <div class="mb-8px font-bold">
          未关联（{{ unlinked.length }}）
          <el-button class="ml-8px" size="small" type="primary" :disabled="selected.length === 0"
                     @click="handleLink">关联选中</el-button>
        </div>
        <el-table :data="unlinked" v-loading="loading" height="460" size="small"
                  @selection-change="(rows: StoryApi.StoryVO[]) => (selected = rows.map((r) => r.id!))">
          <el-table-column type="selection" width="45" />
          <el-table-column label="编号" prop="id" width="70" />
          <el-table-column label="标题" prop="title" show-overflow-tooltip />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">{{ labelOf(STORY_STATUS_OPTIONS, row.status) }}</template>
          </el-table-column>
        </el-table>
      </el-col>
    </el-row>
  </el-drawer>
</template>

<script lang="ts" setup>
import * as PlanApi from '@/api/zentao/plan'
import * as StoryApi from '@/api/zentao/story'
import { STORY_STATUS_OPTIONS, labelOf } from '@/views/zentao/story/constants'

defineOptions({ name: 'ZentaoPlanLinkStoryDrawer' })

const message = useMessage()
const drawerVisible = ref(false)
const loading = ref(false)
const plan = ref<PlanApi.PlanVO>()
const linked = ref<StoryApi.StoryVO[]>([])
const unlinked = ref<StoryApi.StoryVO[]>([])
const selected = ref<number[]>([])

const emit = defineEmits(['success'])

const open = async (row: PlanApi.PlanVO) => {
  plan.value = row
  drawerVisible.value = true
  await loadData()
}
defineExpose({ open })

const loadData = async () => {
  if (!plan.value?.id) return
  loading.value = true
  try {
    const [a, b] = await Promise.all([
      PlanApi.getPlanStoryList(plan.value.id),
      PlanApi.getUnlinkedStoryList(plan.value.id)
    ])
    linked.value = a
    unlinked.value = b
    selected.value = []
  } finally {
    loading.value = false
  }
}

const handleLink = async () => {
  try {
    await PlanApi.linkStory(plan.value!.id!, selected.value)
    message.success('已关联')
    await loadData()
    emit('success')
  } catch {}
}

const handleUnlink = async (row: StoryApi.StoryVO) => {
  try {
    await message.delConfirm('确认把该需求从计划中移除？')
    await PlanApi.unlinkStory(plan.value!.id!, row.id!)
    message.success('已移除')
    await loadData()
    emit('success')
  } catch {}
}
</script>
