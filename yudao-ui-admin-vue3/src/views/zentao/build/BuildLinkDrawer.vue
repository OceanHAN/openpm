<template>
  <el-drawer v-model="drawerVisible" :title="`构建关联：${build?.name || ''}`" size="66%">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="关联 Bug 会顺手把它解决掉"
      description="禅道规则：关联到构建的「未解决 / 未关闭」Bug 会被直接置为已解决（resolution=fixed、解决版本=本构建），并指派回创建人；解除关联不会回退解决状态。"
    />
    <el-tabs v-model="activeTab">
      <el-tab-pane :label="`需求（${linkedStories.length}）`" name="story">
        <el-row :gutter="16">
          <el-col :span="12">
            <div class="mb-8px font-bold">本次完成的需求</div>
            <el-table :data="linkedStories" v-loading="loading" height="420" size="small">
              <el-table-column label="编号" prop="id" width="70" />
              <el-table-column label="标题" prop="title" show-overflow-tooltip />
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ row }">
                  <el-button link type="danger" @click="handleUnlinkStory(row)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-col>
          <el-col :span="12">
            <div class="mb-8px font-bold">
              未关联（{{ unlinkedStories.length }}）
              <el-button class="ml-8px" size="small" type="primary" :disabled="selectedStories.length === 0"
                         @click="handleLinkStory">关联选中</el-button>
            </div>
            <el-table :data="unlinkedStories" v-loading="loading" height="420" size="small"
                      @selection-change="(rows: StoryApi.StoryVO[]) => (selectedStories = rows.map((r) => r.id!))">
              <el-table-column type="selection" width="45" />
              <el-table-column label="编号" prop="id" width="70" />
              <el-table-column label="标题" prop="title" show-overflow-tooltip />
            </el-table>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane :label="`Bug（${linkedBugs.length}）`" name="bug">
        <el-row :gutter="16">
          <el-col :span="12">
            <div class="mb-8px font-bold">本次解决的 Bug</div>
            <el-table :data="linkedBugs" v-loading="loading" height="420" size="small">
              <el-table-column label="编号" prop="id" width="70" />
              <el-table-column label="标题" prop="title" show-overflow-tooltip />
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="bugStatusTag(row.status) as any" size="small">{{ bugStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ row }">
                  <el-button link type="danger" @click="handleUnlinkBug(row)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-col>
          <el-col :span="12">
            <div class="mb-8px font-bold">
              未关联（{{ unlinkedBugs.length }}）
              <el-button class="ml-8px" size="small" type="primary" :disabled="selectedBugs.length === 0"
                         @click="handleLinkBug">关联选中（自动解决）</el-button>
            </div>
            <el-table :data="unlinkedBugs" v-loading="loading" height="420" size="small"
                      @selection-change="(rows: BugApi.BugVO[]) => (selectedBugs = rows.map((r) => r.id!))">
              <el-table-column type="selection" width="45" />
              <el-table-column label="编号" prop="id" width="70" />
              <el-table-column label="标题" prop="title" show-overflow-tooltip />
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="bugStatusTag(row.status) as any" size="small">{{ bugStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<script lang="ts" setup>
import * as BuildApi from '@/api/zentao/build'
import * as StoryApi from '@/api/zentao/story'
import * as BugApi from '@/api/zentao/bug'
import { bugStatusLabel, bugStatusTag } from './constants'

defineOptions({ name: 'ZentaoBuildLinkDrawer' })

const message = useMessage()
const drawerVisible = ref(false)
const loading = ref(false)
const activeTab = ref('story')
const build = ref<BuildApi.BuildVO>()
const linkedStories = ref<StoryApi.StoryVO[]>([])
const unlinkedStories = ref<StoryApi.StoryVO[]>([])
const linkedBugs = ref<BugApi.BugVO[]>([])
const unlinkedBugs = ref<BugApi.BugVO[]>([])
const selectedStories = ref<number[]>([])
const selectedBugs = ref<number[]>([])

const emit = defineEmits(['success'])

const open = async (row: BuildApi.BuildVO, tab = 'story') => {
  build.value = row
  activeTab.value = tab
  drawerVisible.value = true
  await loadData()
}
defineExpose({ open })

const loadData = async () => {
  if (!build.value?.id) return
  loading.value = true
  try {
    const [ls, us, lb, ub] = await Promise.all([
      BuildApi.getBuildStoryList(build.value.id),
      BuildApi.getUnlinkedStoryList(build.value.id),
      BuildApi.getBuildBugList(build.value.id),
      BuildApi.getUnlinkedBugList(build.value.id)
    ])
    linkedStories.value = ls
    unlinkedStories.value = us
    linkedBugs.value = lb
    unlinkedBugs.value = ub
    selectedStories.value = []
    selectedBugs.value = []
  } finally {
    loading.value = false
  }
}

const handleLinkStory = async () => {
  try {
    await BuildApi.linkStory(build.value!.id!, selectedStories.value)
    message.success('已关联需求')
    await loadData()
    emit('success')
  } catch {}
}

const handleUnlinkStory = async (row: StoryApi.StoryVO) => {
  try {
    await message.delConfirm('确认把该需求从构建中移除？')
    await BuildApi.unlinkStory(build.value!.id!, row.id!)
    message.success('已移除')
    await loadData()
    emit('success')
  } catch {}
}

const handleLinkBug = async () => {
  try {
    await message.confirm('关联后，未解决的 Bug 会被自动置为「已解决」，解决版本指向本构建，确认继续？')
    await BuildApi.linkBug(build.value!.id!, selectedBugs.value)
    message.success('已关联并自动解决')
    await loadData()
    emit('success')
  } catch {}
}

const handleUnlinkBug = async (row: BugApi.BugVO) => {
  try {
    await message.delConfirm('确认把该 Bug 从构建中移除？（不会回退它的解决状态）')
    await BuildApi.unlinkBug(build.value!.id!, row.id!)
    message.success('已移除')
    await loadData()
    emit('success')
  } catch {}
}
</script>
