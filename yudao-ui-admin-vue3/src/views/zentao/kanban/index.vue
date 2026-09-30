<template>
  <ContentWrap>
    <!-- 工具条：空间 → 看板 -->
    <el-form inline class="-mb-15px">
      <el-form-item label="空间">
        <el-select v-model="spaceId" class="!w-200px" @change="handleSpaceChange">
          <el-option v-for="s in spaceList" :key="s.id" :label="s.name" :value="s.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="看板">
        <el-select v-model="kanbanId" class="!w-220px" @change="loadBoard">
          <el-option v-for="k in kanbanList" :key="k.id" :label="k.name" :value="k.id!" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="loadBoard"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
        <el-button
          type="primary"
          plain
          @click="openKanbanForm"
          v-hasPermi="['zentao:kanban:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新建看板
        </el-button>
        <el-button plain @click="openLaneForm" v-hasPermi="['zentao:kanban:create']">加泳道</el-button>
        <el-button plain @click="openColumnForm" v-hasPermi="['zentao:kanban:create']">加列</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 看板主体：区域 → 泳道（行） × 列（列） -->
  <ContentWrap v-for="region in board.regions" :key="region.id">
    <div class="mb-10px flex items-center justify-between">
      <span class="font-bold">{{ region.name }}</span>
      <span class="text-12px text-gray-400">
        泳道 {{ region.lanes.length }} 条 · 列 {{ region.columns.length }} 个
        <span v-if="board.kanban?.showWIP === 1">（列头括号是「卡片数 / 在制品上限」，超限标红）</span>
      </span>
    </div>

    <div v-loading="loading" class="overflow-x-auto">
      <table class="kb-table">
        <thead>
          <tr>
            <th class="kb-lane-head">泳道 \ 列</th>
            <th
              v-for="col in region.columns"
              :key="col.id"
              class="kb-col-head"
              :style="{ backgroundColor: col.color || '#333' }"
            >
              <div>{{ col.name }}</div>
              <div class="text-11px opacity-80">
                {{ cellCount(region, col.id!) }} /
                {{ col.limit === -1 ? '∞' : col.limit }}
              </div>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="lane in region.lanes" :key="lane.id">
            <td class="kb-lane-cell" :style="{ borderLeftColor: lane.color || '#7ec5ff' }">
              <div>{{ lane.name }}</div>
              <div class="text-11px text-gray-400">{{ lane.type }}</div>
            </td>
            <td v-for="cell in lane.cells" :key="cell.columnId" class="kb-cell">
              <div class="kb-cell-head">
                <span v-if="cell.overWip" class="kb-wip-over">超出在制品上限</span>
              </div>
              <div
                v-for="card in cell.cards"
                :key="card.id"
                class="kb-card"
                :class="{ 'kb-card-done': card.status === 'done' }"
                @click="openCardDetail(card)"
              >
                <div class="kb-card-title">{{ card.name }}</div>
                <div class="kb-card-meta">
                  <span>P{{ card.pri }}</span>
                  <span v-if="card.assignedTo">{{ card.assignedTo }}</span>
                  <span>{{ Number(card.progress || 0) }}%</span>
                  <el-tag v-if="card.archived" size="small" type="info">已归档</el-tag>
                </div>
              </div>
              <el-button
                link
                type="primary"
                size="small"
                @click="openCardForm(lane.id, cell.columnId)"
                v-hasPermi="['zentao:kanban:create']"
              >
                + 卡片
              </el-button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </ContentWrap>

  <ContentWrap v-if="!loading && board.regions.length === 0">
    <el-empty description="这个看板还没有区域" />
  </ContentWrap>

  <!-- 看板表单 -->
  <Dialog v-model="kanbanFormVisible" title="新建看板" width="620">
    <el-form ref="kanbanFormRef" :model="kanbanForm" :rules="kanbanRules" label-width="90px">
      <el-form-item label="所属空间" prop="space">
        <el-select v-model="kanbanForm.space" class="w-full">
          <el-option v-for="s in spaceList" :key="s.id" :label="s.name" :value="s.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="kanbanForm.name" placeholder="看板名称" maxlength="255" />
      </el-form-item>
      <el-form-item label="负责人"><el-input v-model="kanbanForm.owner" placeholder="admin" /></el-form-item>
      <el-form-item label="访问权限">
        <el-select v-model="kanbanForm.acl" class="w-full">
          <el-option label="公开" value="open" />
          <el-option label="私有" value="private" />
          <el-option label="继承空间权限" value="extend" />
        </el-select>
      </el-form-item>
      <el-form-item label="描述"><el-input v-model="kanbanForm.desc" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="submitKanbanForm">确 定</el-button>
      <el-button @click="kanbanFormVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <!-- 泳道 / 列表单 -->
  <Dialog v-model="laneFormVisible" title="加泳道" width="520">
    <el-form :model="laneForm" label-width="90px">
      <el-form-item label="名称">
        <el-input v-model="laneForm.name" placeholder="泳道名称" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="laneForm.type" class="w-full">
          <el-option label="普通泳道" value="common" />
          <el-option label="研发需求" value="story" />
          <el-option label="Bug" value="bug" />
          <el-option label="任务" value="task" />
        </el-select>
      </el-form-item>
      <el-form-item label="颜色"><el-input v-model="laneForm.color" placeholder="#7ec5ff" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="submitLaneForm">确 定</el-button>
      <el-button @click="laneFormVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <Dialog v-model="columnFormVisible" title="加列" width="520">
    <el-form :model="columnForm" label-width="120px">
      <el-form-item label="名称"><el-input v-model="columnForm.name" placeholder="列名称" /></el-form-item>
      <el-form-item label="在制品上限">
        <el-input-number v-model="columnForm.limit" :min="-1" />
        <span class="ml-8px text-12px text-gray-400">-1 表示不限</span>
      </el-form-item>
      <el-form-item label="颜色"><el-input v-model="columnForm.color" placeholder="#2b519c" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="submitColumnForm">确 定</el-button>
      <el-button @click="columnFormVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <!-- 卡片表单 -->
  <Dialog v-model="cardFormVisible" title="新建卡片" width="620">
    <el-form ref="cardFormRef" :model="cardForm" :rules="cardRules" label-width="90px">
      <el-form-item label="标题" prop="name">
        <el-input v-model="cardForm.name" placeholder="卡片标题" maxlength="255" />
      </el-form-item>
      <el-row>
        <el-col :span="12">
          <el-form-item label="优先级">
            <el-input-number v-model="cardForm.pri" :min="0" :max="4" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="预计工时">
            <el-input-number v-model="cardForm.estimate" :min="0" :precision="2" class="w-full" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="指派给"><el-input v-model="cardForm.assignedTo" placeholder="dev1" /></el-form-item>
      <el-form-item label="描述"><el-input v-model="cardForm.desc" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="submitCardForm">确 定</el-button>
      <el-button @click="cardFormVisible = false">取 消</el-button>
    </template>
  </Dialog>

  <!-- 卡片详情 / 操作 -->
  <Dialog v-model="cardDetailVisible" title="卡片详情" width="620">
    <el-descriptions v-if="cardDetail" :column="2" border>
      <el-descriptions-item label="编号">{{ cardDetail.id }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="cardDetail.status === 'done' ? 'success' : 'primary'">
          {{ cardDetail.statusName }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="标题" :span="2">{{ cardDetail.name }}</el-descriptions-item>
      <el-descriptions-item label="优先级">P{{ cardDetail.pri }}</el-descriptions-item>
      <el-descriptions-item label="指派给">{{ cardDetail.assignedTo || '-' }}</el-descriptions-item>
      <el-descriptions-item label="预计工时">{{ cardDetail.estimate }}</el-descriptions-item>
      <el-descriptions-item label="进度">{{ Number(cardDetail.progress || 0) }}%</el-descriptions-item>
      <el-descriptions-item label="描述" :span="2">{{ cardDetail.desc || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-divider content-position="left">移动到</el-divider>
    <el-form inline>
      <el-form-item label="泳道">
        <el-select v-model="moveForm.toLaneId" class="!w-180px">
          <el-option
            v-for="lane in allLanes"
            :key="lane.id"
            :label="lane.name"
            :value="lane.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="列">
        <el-select v-model="moveForm.toColumnId" class="!w-180px">
          <el-option
            v-for="col in allColumns"
            :key="col.id"
            :label="col.name"
            :value="col.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" plain @click="submitMove" v-hasPermi="['zentao:kanban:update']">
          移动
        </el-button>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button v-if="cardDetail?.status !== 'done'" @click="doFinish" v-hasPermi="['zentao:kanban:update']">
        完成（100%）
      </el-button>
      <el-button
        v-if="cardDetail?.status === 'done'"
        @click="doActivate"
        v-hasPermi="['zentao:kanban:update']"
      >
        激活（回到进行中）
      </el-button>
      <el-button @click="doArchive" v-hasPermi="['zentao:kanban:update']">
        {{ cardDetail?.archived ? '还原' : '归档' }}
      </el-button>
      <el-button type="danger" @click="doDeleteCard" v-hasPermi="['zentao:kanban:delete']">
        删除
      </el-button>
      <el-button @click="cardDetailVisible = false">关 闭</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as KanbanApi from '@/api/zentao/kanban'

defineOptions({ name: 'ZentaoKanban' })

const message = useMessage()

const loading = ref(false)
const spaceList = ref<KanbanApi.KanbanSpaceVO[]>([])
const kanbanList = ref<KanbanApi.KanbanVO[]>([])
const spaceId = ref<number>()
const kanbanId = ref<number>()
const board = ref<KanbanApi.KanbanDataVO>({ kanban: {}, regions: [] })

/** 某一列在当前区域里的卡片数（列头显示 卡片数/上限） */
const cellCount = (region: KanbanApi.KanbanDataVO['regions'][number], columnId: number) => {
  let n = 0
  for (const lane of region.lanes) {
    const cell = lane.cells.find((c) => c.columnId === columnId)
    n += cell ? cell.cardCount : 0
  }
  return n
}

const allLanes = computed(() =>
  board.value.regions.flatMap((r) => r.lanes.map((l) => ({ id: l.id, name: l.name })))
)
const allColumns = computed(() => {
  const seen = new Map<number, string>()
  for (const r of board.value.regions) {
    for (const c of r.columns) {
      if (c.id) seen.set(c.id, c.name || '')
    }
  }
  return Array.from(seen.entries()).map(([id, name]) => ({ id, name }))
})

/** 初始化：空间 → 看板 */
const loadSpaces = async () => {
  spaceList.value = await KanbanApi.getKanbanSpaceList()
  spaceId.value = spaceId.value || spaceList.value[0]?.id
  await handleSpaceChange()
}

const handleSpaceChange = async () => {
  if (!spaceId.value) return
  kanbanList.value = await KanbanApi.getKanbanList(spaceId.value)
  kanbanId.value = kanbanList.value.some((k) => k.id === kanbanId.value)
    ? kanbanId.value
    : kanbanList.value[0]?.id
  await loadBoard()
}

const loadBoard = async () => {
  if (!kanbanId.value) {
    board.value = { kanban: {}, regions: [] }
    return
  }
  loading.value = true
  try {
    board.value = await KanbanApi.getKanbanData(kanbanId.value)
  } finally {
    loading.value = false
  }
}

/** ---------- 看板 ---------- */
const kanbanFormVisible = ref(false)
const kanbanFormRef = ref()
const kanbanForm = reactive<KanbanApi.KanbanVO>({ space: undefined, name: '', owner: 'admin', acl: 'extend', desc: '' })
const kanbanRules = {
  space: [{ required: true, message: '请选择空间', trigger: 'change' }],
  name: [{ required: true, message: '看板名称不能为空', trigger: 'blur' }]
}

const openKanbanForm = () => {
  kanbanFormVisible.value = true
  kanbanForm.space = spaceId.value
  kanbanForm.name = ''
  kanbanForm.owner = 'admin'
  kanbanForm.acl = 'extend'
  kanbanForm.desc = ''
}

const submitKanbanForm = async () => {
  await kanbanFormRef.value.validate()
  const id = await KanbanApi.createKanban(kanbanForm)
  message.success('看板已创建（含默认区域、默认泳道与四个默认列）')
  kanbanFormVisible.value = false
  await handleSpaceChange()
  kanbanId.value = id
  await loadBoard()
}

/** ---------- 泳道 / 列 ---------- */
const laneFormVisible = ref(false)
const laneForm = reactive<KanbanApi.KanbanLaneVO>({ name: '', type: 'common', color: '#7ec5ff' })
const openLaneForm = () => {
  const region = board.value.regions[0]
  if (!region) {
    message.warning('先选一个看板')
    return
  }
  laneForm.region = region.id
  laneForm.name = ''
  laneForm.type = 'common'
  laneForm.color = '#7ec5ff'
  laneFormVisible.value = true
}
const submitLaneForm = async () => {
  if (!laneForm.region) return
  await KanbanApi.createKanbanLane(laneForm)
  message.success('泳道已创建（自动补齐所有列的格子）')
  laneFormVisible.value = false
  await loadBoard()
}

const columnFormVisible = ref(false)
const columnForm = reactive<KanbanApi.KanbanColumnVO>({ name: '', limit: -1, color: '#2b519c' })
const openColumnForm = () => {
  const region = board.value.regions[0]
  if (!region) {
    message.warning('先选一个看板')
    return
  }
  columnForm.groupId = region.groupId
  columnForm.name = ''
  columnForm.limit = -1
  columnForm.color = '#2b519c'
  columnFormVisible.value = true
}
const submitColumnForm = async () => {
  if (!columnForm.groupId) return
  await KanbanApi.createKanbanColumn(columnForm)
  message.success('列已创建')
  columnFormVisible.value = false
  await loadBoard()
}

/** ---------- 卡片 ---------- */
const cardFormVisible = ref(false)
const cardFormRef = ref()
const cardForm = reactive<KanbanApi.KanbanCardVO & { lane?: number; column?: number }>({
  name: '',
  pri: 3,
  estimate: 0,
  assignedTo: '',
  desc: ''
})
const cardRules = { name: [{ required: true, message: '卡片标题不能为空', trigger: 'blur' }] }

const openCardForm = (laneId: number, columnId: number) => {
  cardForm.kanban = kanbanId.value
  cardForm.lane = laneId
  cardForm.column = columnId
  cardForm.name = ''
  cardForm.pri = 3
  cardForm.estimate = 0
  cardForm.assignedTo = ''
  cardForm.desc = ''
  cardFormVisible.value = true
}

const submitCardForm = async () => {
  await cardFormRef.value.validate()
  await KanbanApi.createKanbanCard(cardForm)
  message.success('卡片已创建')
  cardFormVisible.value = false
  await loadBoard()
}

/** ---------- 卡片详情与操作 ---------- */
const cardDetailVisible = ref(false)
const cardDetail = ref<KanbanApi.KanbanCardVO>()
const moveForm = reactive<{ toLaneId?: number; toColumnId?: number; fromLaneId?: number; fromColumnId?: number }>({})

const findLocation = (cardId: number) => {
  for (const region of board.value.regions) {
    for (const lane of region.lanes) {
      for (const cell of lane.cells) {
        if (cell.cards.some((c) => c.id === cardId)) {
          return { laneId: lane.id!, columnId: cell.columnId }
        }
      }
    }
  }
  return null
}

const openCardDetail = (card: KanbanApi.KanbanCardVO) => {
  cardDetail.value = card
  const loc = findLocation(card.id!)
  moveForm.fromLaneId = loc?.laneId
  moveForm.fromColumnId = loc?.columnId
  moveForm.toLaneId = loc?.laneId
  moveForm.toColumnId = loc?.columnId
  cardDetailVisible.value = true
}

const submitMove = async () => {
  if (!cardDetail.value || !moveForm.toLaneId || !moveForm.toColumnId) return
  await KanbanApi.moveKanbanCard({
    cardId: cardDetail.value.id!,
    fromColumnId: moveForm.fromColumnId!,
    toColumnId: moveForm.toColumnId,
    fromLaneId: moveForm.fromLaneId!,
    toLaneId: moveForm.toLaneId
  })
  message.success('已移动')
  cardDetailVisible.value = false
  await loadBoard()
}

const doFinish = async () => {
  await KanbanApi.finishKanbanCard(cardDetail.value!.id!)
  message.success('卡片已完成')
  cardDetailVisible.value = false
  await loadBoard()
}

const doActivate = async () => {
  await KanbanApi.activateKanbanCard(cardDetail.value!.id!, 0)
  message.success('卡片已激活')
  cardDetailVisible.value = false
  await loadBoard()
}

const doArchive = async () => {
  const card = cardDetail.value!
  if (card.archived) {
    await KanbanApi.restoreKanbanCard(card.id!)
    message.success('已还原')
  } else {
    await KanbanApi.archiveKanbanCard(card.id!)
    message.success('已归档')
  }
  cardDetailVisible.value = false
  await loadBoard()
}

const doDeleteCard = async () => {
  try {
    await message.delConfirm('确认删除该卡片？（物理删除，并从格子里摘掉）')
    await KanbanApi.deleteKanbanCard(cardDetail.value!.id!)
    message.success('已删除')
    cardDetailVisible.value = false
    await loadBoard()
  } catch {}
}

onMounted(loadSpaces)
</script>

<style scoped>
.kb-table {
  border-collapse: collapse;
  width: 100%;
}
.kb-table th,
.kb-table td {
  border: 1px solid #e5e7eb;
  vertical-align: top;
}
.kb-lane-head,
.kb-lane-cell {
  min-width: 140px;
  padding: 6px 8px;
  text-align: left;
  background: #f7f8fa;
}
.kb-lane-cell {
  border-left: 4px solid #7ec5ff;
}
.kb-col-head {
  min-width: 200px;
  padding: 6px 8px;
  color: #fff;
  text-align: left;
}
.kb-cell {
  padding: 6px;
  min-width: 200px;
}
.kb-cell-head {
  min-height: 14px;
}
.kb-wip-over {
  color: #d2313d;
  font-size: 11px;
}
.kb-card {
  margin-bottom: 6px;
  padding: 6px 8px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}
.kb-card:hover {
  border-color: #476bda;
}
.kb-card-done {
  background: #f0fdf4;
  border-color: #86efac;
}
.kb-card-title {
  font-size: 13px;
  margin-bottom: 4px;
}
.kb-card-meta {
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: #6b7280;
}
</style>
