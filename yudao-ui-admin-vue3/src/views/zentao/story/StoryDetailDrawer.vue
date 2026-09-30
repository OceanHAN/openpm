<template>
  <el-drawer v-model="visible" :title="`需求详情 #${story?.id ?? ''}`" size="60%">
    <div v-loading="loading">
      <el-tabs v-model="activeTab">
        <!-- ==================== 基本信息 ==================== -->
        <el-tab-pane label="基本信息" name="basic">
          <el-alert
            v-if="viewingVersion && viewingVersion !== story?.version"
            type="warning"
            :closable="false"
            show-icon
            class="mb-10px"
          >
            正在查看历史版本 v{{ viewingVersion }}（当前版本为 v{{ story?.version }}）
            <el-button link type="primary" @click="loadVersion(0)">回到当前版本</el-button>
          </el-alert>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="需求标题" :span="2">
              {{ story?.title }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="tagOf(story?.status ?? '') as any">
                {{ labelOf(STORY_STATUS_OPTIONS, story?.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="研发阶段">
              {{ labelOf(STORY_STAGE_OPTIONS, story?.stage) }}
            </el-descriptions-item>
            <el-descriptions-item label="当前版本">v{{ story?.version }}</el-descriptions-item>
            <el-descriptions-item label="优先级">{{ story?.pri }}</el-descriptions-item>
            <el-descriptions-item label="需求分类">
              {{ labelOf(STORY_CATEGORY_OPTIONS, story?.category) }}
            </el-descriptions-item>
            <el-descriptions-item label="需求来源">
              {{ labelOf(STORY_SOURCE_OPTIONS, story?.source) }}
            </el-descriptions-item>
            <el-descriptions-item label="预计工时">{{ story?.estimate }}</el-descriptions-item>
            <el-descriptions-item label="指派给">{{ story?.assignedTo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ story?.openedBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatDate(story?.openedDate) }}
            </el-descriptions-item>
            <el-descriptions-item label="最后修改人">
              {{ story?.lastEditedBy || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="最后修改时间">
              {{ formatDate(story?.lastEditedDate) }}
            </el-descriptions-item>
            <el-descriptions-item label="已评审人" :span="2">
              {{ story?.reviewedBy || '-' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="story?.status === 'closed'" label="关闭原因">
              {{ labelOf(STORY_CLOSED_REASON_OPTIONS, story?.closedReason) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="story?.status === 'closed'" label="关闭时间">
              {{ formatDate(story?.closedDate) }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="mt-15px">
            <div class="font-bold mb-5px">需求描述</div>
            <div class="whitespace-pre-wrap p-10px bg-[var(--el-fill-color-light)] rounded">
              {{ story?.spec || '（空）' }}
            </div>
          </div>
          <div class="mt-15px">
            <div class="font-bold mb-5px">验收标准</div>
            <div class="whitespace-pre-wrap p-10px bg-[var(--el-fill-color-light)] rounded">
              {{ story?.verify || '（空）' }}
            </div>
          </div>
        </el-tab-pane>

        <!-- ==================== 版本历史 ==================== -->
        <el-tab-pane :label="`版本历史 (${specList.length})`" name="spec">
          <el-table :data="specList" border>
            <el-table-column label="版本" align="center" width="80">
              <template #default="{ row }">
                <el-tag :type="row.version === story?.version ? 'success' : 'info'">
                  v{{ row.version }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="标题" prop="title" show-overflow-tooltip />
            <el-table-column label="需求描述" prop="spec" show-overflow-tooltip />
            <el-table-column label="验收标准" prop="verify" show-overflow-tooltip />
            <el-table-column label="创建时间" align="center" width="170">
              <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="110">
              <template #default="{ row }">
                <el-button link type="primary" @click="loadVersion(row.version); activeTab = 'basic'">
                  查看此版本
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="specList.length === 0" description="暂无版本记录" />
        </el-tab-pane>

        <!-- ==================== 评审情况 ==================== -->
        <el-tab-pane label="评审情况" name="review">
          <template v-if="hasReview">
            <el-descriptions :column="2" border class="mb-15px">
              <el-descriptions-item label="评审版本">
                v{{ review?.version ?? '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="是否评完">
                <el-tag :type="review?.finished ? 'success' : 'warning'">
                  {{ review?.finished ? '已评完' : '评审中' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="聚合结果" :span="2">
                <el-tag v-if="review?.finalResult" :type="reviewTag(review.finalResult) as any">
                  {{ labelOf(STORY_REVIEW_RESULT_OPTIONS, review.finalResult) }}
                </el-tag>
                <span v-else class="text-gray-400">尚未产生</span>
              </el-descriptions-item>
            </el-descriptions>

            <el-table :data="review?.reviewers ?? []" border>
              <el-table-column label="评审人" prop="reviewer" align="center" />
              <el-table-column label="评审结果" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.result" :type="reviewTag(row.result) as any">
                    {{ labelOf(STORY_REVIEW_RESULT_OPTIONS, row.result) }}
                  </el-tag>
                  <el-tag v-else type="info">待评审</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="评审时间" align="center" width="170">
                <template #default="{ row }">{{ formatDate(row.reviewDate) }}</template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="该需求还没有评审记录" />

          <!-- 表决区：仅评审中时显示 -->
          <div v-if="story?.status === 'reviewing'" class="mt-15px">
            <div class="font-bold mb-8px">提交我的评审意见</div>
            <el-radio-group v-model="myResult">
              <el-radio-button
                v-for="o in STORY_REVIEW_RESULT_OPTIONS"
                :key="o.value"
                :value="o.value"
              >
                {{ o.label }}
              </el-radio-button>
            </el-radio-group>
            <el-input
              v-model="myComment"
              type="textarea"
              :rows="2"
              placeholder="评审意见（可选）"
              class="mt-10px"
            />
            <el-button
              type="primary"
              class="mt-10px"
              :disabled="!myResult"
              :loading="submitting"
              @click="handleSubmitReview"
            >
              提交评审意见
            </el-button>
            <div class="text-gray-400 text-12px mt-5px">
              提示：所有评审人都提交后才会自动流转需求状态。结果为「撤销变更」且当前是 v2 以上时，需求版本会回滚。
            </div>
          </div>
          <el-empty v-else description="当前需求不在评审中" />
        </el-tab-pane>
        <!-- ==================== 任务（需求转任务） ==================== -->
        <el-tab-pane :label="`任务 (${taskList.length})`" name="task">
          <el-alert
            type="info"
            :closable="false"
            show-icon
            class="mb-10px"
            title="任务记住的是「建它时需求的版本」"
            description="需求后来正式变更，任务不会跟着变，而是提示「需求已变更」由人确认 —— 已经按老需求做完的工作不该被无声改写。"
          />
          <div class="mb-10px">
            <el-button type="primary" plain size="small" @click="openDecomposeTask" v-hasPermi="['zentao:task:create']">
              <Icon icon="ep:plus" class="mr-5px" />需求转任务
            </el-button>
            <span class="text-12px text-gray-400 ml-8px">只填任务名称即可，优先级从需求继承</span>
          </div>
          <el-table :data="taskList" border size="small">
            <el-table-column label="编号" align="center" prop="id" width="80" />
            <el-table-column label="任务名称" prop="name" min-width="200" show-overflow-tooltip />
            <el-table-column label="状态" align="center" prop="status" width="90" />
            <el-table-column label="优先级" align="center" prop="pri" width="80" />
            <el-table-column label="负责人" align="center" prop="assignedTo" width="90" />
            <el-table-column label="需求版本" align="center" width="130">
              <template #default="{ row }">
                <span>v{{ row.storyVersion }}</span>
                <el-tag v-if="row.storyChanged" type="danger" size="small" class="ml-5px">需求已变更</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="taskList.length === 0" description="该需求还没有分解出任务" />
        </el-tab-pane>

        <!-- ==================== 子需求（需求分解） ==================== -->
        <el-tab-pane :label="`子需求 (${childList.length})`" name="child">
          <el-alert
            v-if="story?.isParent"
            type="info"
            :closable="false"
            show-icon
            class="mb-10px"
            title="父需求的工时是汇总出来的"
            description="父需求自己不填工时：它的 estimate = 所有子需求之和；子需求全部关闭时父需求会自动关闭，父需求已关闭但子需求又被激活时父需求会自动激活。"
          />
          <div class="mb-10px">
            <el-button type="primary" plain size="small" @click="openDecompose" v-hasPermi="['zentao:story:create']">
              <Icon icon="ep:plus" class="mr-5px" />分解子需求
            </el-button>
            <span class="text-12px text-gray-400 ml-8px">只填标题即可，产品/模块/分支/计划/类型/优先级都从父需求继承</span>
          </div>
          <el-table :data="childList" border size="small">
            <el-table-column label="编号" align="center" prop="id" width="80" />
            <el-table-column label="标题" prop="title" min-width="200" show-overflow-tooltip />
            <el-table-column label="状态" align="center" width="90">
              <template #default="{ row }">
                <el-tag :type="tagOf(STORY_STATUS_OPTIONS, row.status) as any">
                  {{ labelOf(STORY_STATUS_OPTIONS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="工时" align="center" prop="estimate" width="80" />
            <el-table-column label="操作" align="center" width="90">
              <template #default="{ row }">
                <el-button link type="danger" @click="handleUnlinkChild(row)" v-hasPermi="['zentao:story:update']">
                  移出
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="childList.length === 0" description="该需求还没有分解出子需求" />
        </el-tab-pane>

        <!-- ==================== 附件 ==================== -->
        <!-- 附件是所有业务对象的公共能力，这里复用公共组件（zt_file） -->
        <el-tab-pane :label="`附件 (${filePanelRef?.list?.length ?? 0})`" name="file">
          <AttachmentPanel
            v-if="story?.id"
            ref="filePanelRef"
            object-type="story"
            :objectID="story.id"
          />
        </el-tab-pane>
        <!-- ==================== 操作日志 ==================== -->
        <el-tab-pane :label="`操作日志 (${actionCount})`" name="action">
          <ActionTimeline ref="timelineRef" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>

  <!-- 需求转任务 -->
  <el-dialog v-model="decomposeTaskVisible" title="需求转任务" width="600px" append-to-body>
    <el-form label-width="100px">
      <el-form-item label="需求">{{ story?.title }}</el-form-item>
      <el-form-item label="目标执行" required>
        <el-input-number v-model="taskExecution" :min="1" :controls="false" class="!w-200px" />
        <el-input-number v-model="taskProject" :min="1" :controls="false" class="!w-160px ml-8px" />
        <div class="text-12px text-gray-400">任务必须挂在执行下（左：执行编号，右：所属项目编号）</div>
      </el-form-item>
      <el-form-item label="任务名称">
        <el-input
          v-model="taskNames"
          type="textarea"
          :rows="5"
          placeholder="一行一个任务名称，例如：&#10;登录接口开发&#10;登录接口联调"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="decomposeTaskVisible = false">取消</el-button>
      <el-button type="primary" :loading="decomposingTask" @click="handleDecomposeTask">确定</el-button>
    </template>
  </el-dialog>

  <!-- 分解子需求 -->
  <el-dialog v-model="decomposeVisible" title="分解子需求" width="560px" append-to-body>
    <el-form label-width="90px">
      <el-form-item label="父需求">{{ story?.title }}</el-form-item>
      <el-form-item label="子需求标题">
        <el-input
          v-model="decomposeTitles"
          type="textarea"
          :rows="5"
          placeholder="一行一个标题，例如：&#10;批量导入-解析 Excel&#10;批量导入-校验与提示"
        />
        <div class="text-12px text-gray-400">
          只有标题需要填；产品/模块/分支/计划/类型/优先级都会从父需求继承，工时由子需求自己填
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="decomposeVisible = false">取消</el-button>
      <el-button type="primary" :loading="decomposing" @click="handleDecompose">确定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as StoryApi from '@/api/zentao/story'
import * as TaskApi from '@/api/zentao/task'
import { formatDate } from '@/utils/formatTime'
import ActionTimeline from './ActionTimeline.vue'
import AttachmentPanel from '../components/AttachmentPanel.vue'
import {
  STORY_STATUS_OPTIONS,
  STORY_STAGE_OPTIONS,
  STORY_CATEGORY_OPTIONS,
  STORY_SOURCE_OPTIONS,
  STORY_CLOSED_REASON_OPTIONS,
  STORY_REVIEW_RESULT_OPTIONS,
  labelOf,
  tagOf
} from './constants'

defineOptions({ name: 'ZentaoStoryDetail' })

const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const activeTab = ref('basic')
const story = ref<StoryApi.StoryVO>()
const specList = ref<StoryApi.StorySpecVO[]>([])
const review = ref<StoryApi.StoryReviewVO>()
const viewingVersion = ref(0)
const myResult = ref('')
const myComment = ref('')
const submitting = ref(false)
const timelineRef = ref()
const filePanelRef = ref()
const childList = ref<StoryApi.StoryVO[]>([])
const taskList = ref<any[]>([])
const decomposeTaskVisible = ref(false)
const decomposingTask = ref(false)
const taskNames = ref('')
const taskExecution = ref<number>(90001)
const taskProject = ref<number>(1)
const decomposeVisible = ref(false)
const decomposing = ref(false)
const decomposeTitles = ref('')

/** 操作日志条数，用于 Tab 标题 */
const actionCount = computed(() => timelineRef.value?.count ?? 0)

const emit = defineEmits(['success'])

/** 是否已有评审记录（没提交过评审时不要展示"评审中"这类误导信息） */
const hasReview = computed(() => {
  const list = review.value?.reviewers
  return !!list && list.length > 0
})

/** 打开抽屉 */
const open = async (id: number) => {
  visible.value = true
  activeTab.value = 'basic'
  viewingVersion.value = 0
  myResult.value = ''
  myComment.value = ''
  await loadAll(id)
}
defineExpose({ open })

/** 加载需求 + 版本历史 + 评审情况 + 操作日志 */
const loadAll = async (id: number) => {
  loading.value = true
  try {
    story.value = await StoryApi.getStory(id, viewingVersion.value)
    specList.value = await StoryApi.getStorySpecList(id)
    try {
      review.value = await StoryApi.getStoryReviewList(id)
    } catch {
      review.value = undefined
    }
    await timelineRef.value?.load('story', id)
    childList.value = await StoryApi.getStoryChildList(id)
    taskList.value = await TaskApi.getTaskListByStory(id)
  } finally {
    loading.value = false
  }
}

/** 切到指定版本（0 表示当前版本） */
const loadVersion = async (version: number) => {
  if (!story.value?.id) return
  viewingVersion.value = version
  story.value = await StoryApi.getStory(story.value.id, version)
}

/** 提交我的评审意见 */
const handleSubmitReview = async () => {
  if (!story.value?.id || !myResult.value) return
  submitting.value = true
  try {
    const finalResult = await StoryApi.submitReview({
      id: story.value.id,
      result: myResult.value,
      comment: myComment.value
    })
    if (finalResult) {
      message.success(`全部评审已完成，聚合结果为：${labelOf(STORY_REVIEW_RESULT_OPTIONS, finalResult)}`)
    } else {
      message.success('已记录你的评审意见，等待其他评审人')
    }
    myResult.value = ''
    myComment.value = ''
    await loadAll(story.value.id)
    emit('success')
  } finally {
    submitting.value = false
  }
}

/** 分解：一行一个标题 */
const openDecompose = () => {
  decomposeTitles.value = ''
  decomposeVisible.value = true
}

const handleDecompose = async () => {
  const titles = decomposeTitles.value
    .split('\n')
    .map((t) => t.trim())
    .filter(Boolean)
  if (!titles.length) {
    message.warning('请至少填写一个子需求标题')
    return
  }
  decomposing.value = true
  try {
    const ids = await StoryApi.batchCreateStoryChild(story.value!.id!, titles)
    message.success(`已分解出 ${ids.length} 条子需求`)
    decomposeVisible.value = false
    await loadAll(story.value!.id!)
    emit('success')
  } finally {
    decomposing.value = false
  }
}

const handleUnlinkChild = async (row: StoryApi.StoryVO) => {
  await message.delConfirm(`确认把「${row.title}」从父需求下移出？移出后它自己变成一棵独立的树`)
  // 移出 = 把 parent 置 0（本实现只支持两层，父需求的聚合会跟着重算）
  await StoryApi.updateStory({ ...row, parent: 0 } as any)
  message.success('已移出')
  await loadAll(story.value!.id!)
}

/** 需求转任务：一行一个任务名称 */
const openDecomposeTask = () => {
  taskNames.value = ''
  decomposeTaskVisible.value = true
}

const handleDecomposeTask = async () => {
  const names = taskNames.value
    .split('\n')
    .map((t) => t.trim())
    .filter(Boolean)
  if (!names.length) {
    message.warning('请至少填写一个任务名称')
    return
  }
  decomposingTask.value = true
  try {
    const ids = await TaskApi.batchCreateTaskFromStory({
      storyId: story.value!.id!,
      execution: taskExecution.value,
      project: taskProject.value,
      names
    })
    message.success(`已创建 ${ids.length} 个任务`)
    decomposeTaskVisible.value = false
    await loadAll(story.value!.id!)
  } finally {
    decomposingTask.value = false
  }
}

const reviewTag = (result: string) => {
  const hit = STORY_REVIEW_RESULT_OPTIONS.find((o) => o.value === result)
  return hit ? hit.tag : 'info'
}
</script>
