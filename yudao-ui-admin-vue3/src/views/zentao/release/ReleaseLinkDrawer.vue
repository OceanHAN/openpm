<template>
  <el-drawer v-model="drawerVisible" :title="`发布清单：${release?.name || ''}`" size="66%">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="发布有三份清单"
      description="完成的需求（本次交付的内容）、解决的 Bug、遗留的 Bug（带着上线的已知问题）。左边是已关联，右边是候选，勾选即可关联。"
    />
    <el-tabs v-model="activeTab">
      <el-tab-pane :label="`需求（${linkedStories.length}）`" name="story">
        <el-row :gutter="16">
          <el-col :span="12">
            <div class="mb-8px font-bold">本次完成的需求</div>
            <el-table :data="linkedStories" v-loading="loading" height="420" size="small">
              <el-table-column label="编号" prop="id" width="70" />
              <el-table-column label="标题" prop="title" show-overflow-tooltip />
              <el-table-column label="阶段" width="90" align="center">
                <template #default="{ row }">{{ labelOf(STORY_STAGE_OPTIONS, row.stage) }}</template>
              </el-table-column>
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

      <el-tab-pane :label="`解决的 Bug（${linkedBugs.length}）`" name="bug">
        <el-row :gutter="16">
          <el-col :span="12">
            <div class="mb-8px font-bold">本次解决的 Bug</div>
            <el-table :data="linkedBugs" v-loading="loading" height="420" size="small">
              <el-table-column label="编号" prop="id" width="70" />
              <el-table-column label="标题" prop="title" show-overflow-tooltip />
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ row }">
                  <el-button link type="danger" @click="handleUnlinkBug(row, 'bug')">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-col>
          <el-col :span="12">
            <div class="mb-8px font-bold">
              未关联（{{ unlinkedBugs.length }}）
              <el-button class="ml-8px" size="small" type="primary" :disabled="selectedBugs.length === 0"
                         @click="handleLinkBug('bug')">关联选中</el-button>
            </div>
            <el-table :data="unlinkedBugs" v-loading="loading" height="420" size="small"
                      @selection-change="(rows: BugApi.BugVO[]) => (selectedBugs = rows.map((r) => r.id!))">
              <el-table-column type="selection" width="45" />
              <el-table-column label="编号" prop="id" width="70" />
              <el-table-column label="标题" prop="title" show-overflow-tooltip />
            </el-table>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane :label="`遗留的 Bug（${linkedLeftBugs.length}）`" name="leftBug">
        <el-row :gutter="16">
          <el-col :span="12">
            <div class="mb-8px font-bold">带着上线的已知问题</div>
            <el-table :data="linkedLeftBugs" v-loading="loading" height="420" size="small">
              <el-table-column label="编号" prop="id" width="70" />
              <el-table-column label="标题" prop="title" show-overflow-tooltip />
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ row }">
                  <el-button link type="danger" @click="handleUnlinkBug(row, 'leftBug')">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-col>
          <el-col :span="12">
            <div class="mb-8px font-bold">
              未关联（{{ unlinkedLeftBugs.length }}）
              <el-button class="ml-8px" size="small" type="primary" :disabled="selectedLeftBugs.length === 0"
                         @click="handleLinkBug('leftBug')">标记为遗留</el-button>
            </div>
            <el-table :data="unlinkedLeftBugs" v-loading="loading" height="420" size="small"
                      @selection-change="(rows: BugApi.BugVO[]) => (selectedLeftBugs = rows.map((r) => r.id!))">
              <el-table-column type="selection" width="45" />
              <el-table-column label="编号" prop="id" width="70" />
              <el-table-column label="标题" prop="title" show-overflow-tooltip />
            </el-table>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<script lang="ts" setup>
import * as ReleaseApi from '@/api/zentao/release'
import * as StoryApi from '@/api/zentao/story'
import * as BugApi from '@/api/zentao/bug'
import { STORY_STAGE_OPTIONS, labelOf } from '@/views/zentao/story/constants'

defineOptions({ name: 'ZentaoReleaseLinkDrawer' })

const message = useMessage()
const drawerVisible = ref(false)
const loading = ref(false)
const activeTab = ref('story')
const release = ref<ReleaseApi.ReleaseVO>()
const linkedStories = ref<StoryApi.StoryVO[]>([])
const unlinkedStories = ref<StoryApi.StoryVO[]>([])
const linkedBugs = ref<BugApi.BugVO[]>([])
const unlinkedBugs = ref<BugApi.BugVO[]>([])
const linkedLeftBugs = ref<BugApi.BugVO[]>([])
const unlinkedLeftBugs = ref<BugApi.BugVO[]>([])
const selectedStories = ref<number[]>([])
const selectedBugs = ref<number[]>([])
const selectedLeftBugs = ref<number[]>([])

const emit = defineEmits(['success'])

const open = async (row: ReleaseApi.ReleaseVO, tab = 'story') => {
  release.value = row
  activeTab.value = tab
  drawerVisible.value = true
  await loadData()
}
defineExpose({ open })

const loadData = async () => {
  if (!release.value?.id) return
  loading.value = true
  try {
    const [ls, us, lb, ub, ll, ul] = await Promise.all([
      ReleaseApi.getReleaseStoryList(release.value.id),
      ReleaseApi.getUnlinkedStoryList(release.value.id),
      ReleaseApi.getReleaseBugList(release.value.id, 'bug'),
      ReleaseApi.getUnlinkedBugList(release.value.id, 'bug'),
      ReleaseApi.getReleaseBugList(release.value.id, 'leftBug'),
      ReleaseApi.getUnlinkedBugList(release.value.id, 'leftBug')
    ])
    linkedStories.value = ls
    unlinkedStories.value = us
    linkedBugs.value = lb
    unlinkedBugs.value = ub
    linkedLeftBugs.value = ll
    unlinkedLeftBugs.value = ul
    selectedStories.value = []
    selectedBugs.value = []
    selectedLeftBugs.value = []
  } finally {
    loading.value = false
  }
}

const handleLinkStory = async () => {
  try {
    await ReleaseApi.linkStory(release.value!.id!, selectedStories.value)
    message.success('已关联需求')
    await loadData()
    emit('success')
  } catch {}
}

const handleUnlinkStory = async (row: StoryApi.StoryVO) => {
  try {
    await message.delConfirm('确认把该需求从发布中移除？')
    await ReleaseApi.unlinkStory(release.value!.id!, row.id!)
    message.success('已移除')
    await loadData()
    emit('success')
  } catch {}
}

const handleLinkBug = async (type: string) => {
  const ids = type === 'leftBug' ? selectedLeftBugs.value : selectedBugs.value
  try {
    await ReleaseApi.linkBug(release.value!.id!, type, ids)
    message.success(type === 'leftBug' ? '已标记为遗留 Bug' : '已关联 Bug')
    await loadData()
    emit('success')
  } catch {}
}

const handleUnlinkBug = async (row: BugApi.BugVO, type: string) => {
  try {
    await message.delConfirm('确认从发布中移除该 Bug？')
    await ReleaseApi.unlinkBug(release.value!.id!, type, row.id!)
    message.success('已移除')
    await loadData()
    emit('success')
  } catch {}
}
</script>
