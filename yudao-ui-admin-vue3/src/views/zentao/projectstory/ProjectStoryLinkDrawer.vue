<template>
  <el-drawer v-model="drawerVisible" :title="`纳入需求范围：${selectedProjectName}`" size="60%">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="候选来自项目已关联的产品"
      description="草稿、评审中、已关闭的需求不能纳入项目范围；关联时记录的是「需求当时的版本」，需求之后正式变更不会改掉已排期的内容，但列表里会标出「版本已变更」。"
    />
    <el-row :gutter="16">
      <el-col :span="12">
        <div class="mb-8px font-bold">已纳入（{{ linked.length }}）</div>
        <el-table :data="linked" v-loading="loading" height="460" size="small">
          <el-table-column label="编号" prop="story" width="70" />
          <el-table-column label="标题" prop="title" show-overflow-tooltip />
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ row }">
              <el-button link type="danger" @click="handleUnlink(row)">移出</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
      <el-col :span="12">
        <div class="mb-8px font-bold">
          可纳入（{{ unlinked.length }}）
          <el-button class="ml-8px" size="small" type="primary" :disabled="selected.length === 0"
                     @click="handleLink">纳入选中</el-button>
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
import * as PsApi from '@/api/zentao/projectstory'
import * as StoryApi from '@/api/zentao/story'
import { STORY_STATUS_OPTIONS, labelOf } from '@/views/zentao/story/constants'

defineOptions({ name: 'ZentaoProjectStoryLinkDrawer' })

const props = defineProps<{
  projectId?: number
  linkedProducts: PsApi.ProjectProductVO[]
}>()

const message = useMessage()
const drawerVisible = ref(false)
const loading = ref(false)
const linked = ref<PsApi.ProjectStoryVO[]>([])
const unlinked = ref<StoryApi.StoryVO[]>([])
const selected = ref<number[]>([])

const emit = defineEmits(['success'])

const selectedProjectName = computed(() => {
  const names = props.linkedProducts.map((p) => p.productName).join(', ')
  return names || `#${props.projectId}`
})

const open = async () => {
  drawerVisible.value = true
  await loadData()
}
defineExpose({ open })

const loadData = async () => {
  if (!props.projectId) return
  loading.value = true
  try {
    const [a, b] = await Promise.all([
      PsApi.getStoryList(props.projectId),
      PsApi.getUnlinkedStoryList(props.projectId)
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
    const added = await PsApi.linkStory(props.projectId!, selected.value)
    message.success(`已纳入 ${added.length} 条需求（草稿/已关联的会被跳过）`)
    await loadData()
    emit('success')
  } catch {}
}

const handleUnlink = async (row: PsApi.ProjectStoryVO) => {
  try {
    await message.delConfirm(`确认把「${row.title}」移出项目范围？`)
    await PsApi.unlinkStory(props.projectId!, row.story!)
    message.success('已移出范围')
    await loadData()
    emit('success')
  } catch {}
}
</script>
