<template>
  <div v-loading="loading">
    <el-empty v-if="!loading && list.length === 0" description="暂无操作记录" />

    <el-timeline v-else>
      <el-timeline-item
        v-for="item in list"
        :key="item.id"
        :timestamp="formatDate(item.date)"
        placement="top"
        :type="timelineType(item.action)"
      >
        <div class="flex items-center gap-8px">
          <span class="font-bold">{{ item.actor || '-' }}</span>
          <el-tag size="small" :type="actionTagOf(item.action) as any">
            {{ item.actionName }}
          </el-tag>
        </div>

        <div v-if="item.comment" class="mt-6px text-gray-600 whitespace-pre-wrap">
          {{ item.comment }}
        </div>

        <!-- 字段级变更明细 -->
        <div v-if="item.histories && item.histories.length" class="mt-8px">
          <el-table :data="item.histories" size="small" border class="!w-full">
            <el-table-column label="字段" width="110">
              <template #default="{ row }">{{ fieldLabel(row.field) }}</template>
            </el-table-column>
            <el-table-column label="变更前" min-width="140">
              <template #default="{ row }">
                <span class="text-gray-500 line-through">{{ brief(row.oldValue) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="变更后" min-width="140">
              <template #default="{ row }">
                <span class="text-green-600">{{ brief(row.newValue) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="差异" width="110">
              <template #default="{ row }">
                <el-popover
                  v-if="row.diff"
                  placement="left"
                  :width="520"
                  trigger="click"
                >
                  <template #reference>
                    <el-button link type="primary" size="small">查看差异</el-button>
                  </template>
                  <pre class="diff-pre">{{ row.diff }}</pre>
                </el-popover>
                <span v-else class="text-gray-400">-</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script lang="ts" setup>
import * as ActionApi from '@/api/zentao/action'
import { formatDate } from '@/utils/formatTime'
import { actionTagOf, fieldLabel } from './constants'

defineOptions({ name: 'ZentaoActionTimeline' })

const loading = ref(false)
const list = ref<ActionApi.ActionTimelineVO[]>([])

/** 记录条数，供父组件在 Tab 标题上展示 */
const count = computed(() => list.value.length)

/** 加载时间线 */
const load = async (objectType: string, objectID: number) => {
  loading.value = true
  try {
    list.value = await ActionApi.getActionList(objectType, objectID)
  } finally {
    loading.value = false
  }
}
defineExpose({ load, count })

/** 值太长时截断展示，完整内容看差异弹层 */
const brief = (value?: string) => {
  if (!value) return '（空）'
  const oneLine = value.replace(/\n/g, ' ')
  return oneLine.length > 40 ? oneLine.slice(0, 40) + '…' : oneLine
}

/** 动作 → el-timeline-item 的节点颜色 */
const timelineType = (action: string) => {
  switch (action) {
    case 'created':
    case 'activated':
      return 'success'
    case 'closed':
    case 'deleted':
      return 'danger'
    case 'changed':
    case 'submitReview':
      return 'warning'
    default:
      return 'primary'
  }
}
</script>

<style scoped>
.diff-pre {
  margin: 0;
  max-height: 420px;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
