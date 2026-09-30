<template>
  <ContentWrap>
    <el-tabs v-model="activeTab">
      <!-- ==================== 回收站 ==================== -->
      <el-tab-pane label="回收站" name="trash">
        <div class="mb-10px flex items-center justify-between">
          <el-form inline class="-mb-15px">
            <el-form-item label="对象类型">
              <el-select v-model="trashQuery.objectType" clearable class="!w-150px" @change="loadTrash">
                <el-option v-for="o in objectTypes" :key="o" :label="o" :value="o" />
              </el-select>
            </el-form-item>
            <el-form-item label="删除人">
              <el-input v-model="trashQuery.actor" placeholder="账号" clearable class="!w-140px" @keyup.enter="loadTrash" />
            </el-form-item>
            <el-form-item>
              <el-button @click="loadTrash"><Icon icon="ep:search" class="mr-5px" /> 查询</el-button>
            </el-form-item>
          </el-form>
          <el-button
            type="danger"
            plain
            :disabled="trashTotal === 0"
            @click="handleHideAll"
            v-hasPermi="['zentao:action:undelete']"
          >
            全部隐藏
          </el-button>
        </div>

        <el-table v-loading="trashLoading" :data="trashList">
          <el-table-column label="编号" align="center" width="90" prop="actionId" />
          <el-table-column label="对象类型" align="center" width="110" prop="objectTypeName" />
          <el-table-column label="对象" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.objectName || '（取不到名称）' }}</span>
              <span class="ml-6px text-12px text-gray-400">#{{ row.objectID }}</span>
            </template>
          </el-table-column>
          <el-table-column label="删除人" align="center" width="100" prop="deletedBy" />
          <el-table-column label="删除时间" align="center" width="170" prop="deletedDate" :formatter="dateFormatter" />
          <el-table-column label="备注" min-width="200" prop="comment" show-overflow-tooltip />
          <el-table-column label="操作" align="center" width="170" fixed="right">
            <template #default="{ row }">
              <el-tooltip v-if="!row.canUndelete" :content="row.reason || '不能还原'" placement="top">
                <span>
                  <el-button link type="primary" disabled>还原</el-button>
                </span>
              </el-tooltip>
              <el-button
                v-else
                link
                type="primary"
                @click="handleUndelete(row)"
                v-hasPermi="['zentao:action:undelete']"
              >
                还原
              </el-button>
              <el-button link type="danger" @click="handleHide(row)" v-hasPermi="['zentao:action:undelete']">
                隐藏
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          :total="trashTotal"
          v-model:page="trashQuery.pageNo"
          v-model:limit="trashQuery.pageSize"
          @pagination="loadTrash"
        />
      </el-tab-pane>

      <!-- ==================== 动态 ==================== -->
      <el-tab-pane label="动态" name="dynamic">
        <el-form inline class="-mb-15px">
          <el-form-item label="成员">
            <el-input v-model="dynamicQuery.actor" placeholder="账号" clearable class="!w-140px" @keyup.enter="loadDynamic" />
          </el-form-item>
          <el-form-item label="周期">
            <el-select v-model="dynamicQuery.period" class="!w-140px" @change="loadDynamic">
              <el-option label="今天" value="today" />
              <el-option label="昨天" value="yesterday" />
              <el-option label="本周" value="thisWeek" />
              <el-option label="本月" value="thisMonth" />
              <el-option label="全部" value="all" />
            </el-select>
          </el-form-item>
          <el-form-item label="项目">
            <el-input-number v-model="dynamicQuery.project" :min="0" class="!w-140px" />
          </el-form-item>
          <el-form-item>
            <el-button @click="loadDynamic"><Icon icon="ep:search" class="mr-5px" /> 查询</el-button>
          </el-form-item>
        </el-form>

        <div v-loading="dynamicLoading" class="mt-12px">
          <el-empty v-if="dynamicList.length === 0" description="这个周期没有动态" />
          <div v-for="item in dynamicList" :key="item.id" class="action-row">
            <div class="action-row-desc">{{ item.renderedDesc || item.actionName }}</div>
            <div v-if="item.comment" class="action-row-comment">{{ item.comment }}</div>
            <div v-if="item.histories?.length" class="action-row-histories">
              <el-tag v-for="(h, idx) in item.histories" :key="idx" size="small" class="mr-6px mb-4px" type="info">
                {{ h.field }}：{{ h.oldValue || '（空）' }} → {{ h.newValue || '（空）' }}
              </el-tag>
            </div>
            <div class="action-row-meta">
              {{ item.objectType }} #{{ item.objectID }} · {{ formatDate(item.date) }}
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import * as ActionApi from '@/api/zentao/action'

defineOptions({ name: 'ZentaoAction' })

const message = useMessage()
/**
 * 时间戳 → 可读时间。
 * 动态里的 date 是毫秒时间戳，这里自己格式化而不是复用 dateFormatter：
 * 后者是 el-table 的 formatter（签名要求 TableColumnCtx，直接调会被 TS 拦下）。
 */
const formatDate = (value?: number | string) => {
  if (!value) return '-'
  const date = new Date(Number(value))
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

const activeTab = ref('trash')

/** ---------- 回收站 ---------- */
const trashLoading = ref(false)
const trashList = ref<ActionApi.ActionTrashVO[]>([])
const trashTotal = ref(0)
const trashQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  objectType: undefined as string | undefined,
  actor: ''
})

/** 对象类型下拉：直接从回收站数据里取出现过的类型（不写死清单，避免和后端白名单漂移） */
const objectTypes = ref<string[]>([])

const loadTrash = async () => {
  trashLoading.value = true
  try {
    const page = await ActionApi.getActionTrashPage(trashQuery as any)
    trashList.value = page.list
    trashTotal.value = page.total
    for (const row of page.list) {
      if (row.objectType && !objectTypes.value.includes(row.objectType)) {
        objectTypes.value.push(row.objectType)
      }
    }
  } finally {
    trashLoading.value = false
  }
}

const handleUndelete = async (row: ActionApi.ActionTrashVO) => {
  try {
    await message.confirm(`确认还原「${row.objectName || row.objectType + '#' + row.objectID}」？`)
    const result = await ActionApi.undeleteAction(row.actionId)
    message.success(`已还原：${result.objectName || result.objectType}`)
    await loadTrash()
  } catch {}
}

const handleHide = async (row: ActionApi.ActionTrashVO) => {
  try {
    await message.confirm('隐藏后不在回收站显示，对象仍然是删除状态，且不能再还原。确认隐藏？')
    await ActionApi.hideAction(row.actionId)
    message.success('已隐藏')
    await loadTrash()
  } catch {}
}

const handleHideAll = async () => {
  try {
    await message.delConfirm('确认隐藏回收站里的全部记录？')
    const count = await ActionApi.hideAllAction()
    message.success(`已隐藏 ${count} 条`)
    await loadTrash()
  } catch {}
}

/** ---------- 动态 ---------- */
const dynamicLoading = ref(false)
const dynamicList = ref<ActionApi.ActionTimelineVO[]>([])
const dynamicQuery = reactive({
  actor: '',
  period: 'thisWeek',
  project: undefined as number | undefined
})

const loadDynamic = async () => {
  dynamicLoading.value = true
  try {
    dynamicList.value = await ActionApi.getActionDynamic({
      actor: dynamicQuery.actor || undefined,
      period: dynamicQuery.period,
      project: dynamicQuery.project || undefined,
      limit: 50
    })
  } finally {
    dynamicLoading.value = false
  }
}

onMounted(async () => {
  await loadTrash()
  await loadDynamic()
})
</script>

<style scoped>
.action-row {
  padding: 8px 4px;
  border-bottom: 1px solid #f0f0f0;
}
.action-row-desc {
  font-size: 14px;
}
.action-row-comment {
  margin-top: 4px;
  padding: 4px 8px;
  background: #f7f8fa;
  border-radius: 4px;
  font-size: 13px;
  color: #4b5563;
}
.action-row-histories {
  margin-top: 4px;
}
.action-row-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #9ca3af;
}
</style>
