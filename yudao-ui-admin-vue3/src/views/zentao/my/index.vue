<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="我的地盘是「查询层」，不是新数据"
      description="待办（zt_todo）是唯一的个人数据，其余都是聚合：任务/需求/缺陷按「指派给我」统计（禅道 module/my/model.php#getOverview），工时按我登记的流水汇总。所以这张页面上没有一条数据是这里产生的 —— 点开各 Tab 都是各模块自己的列表接口，只是强制带上我的账号。"
    />

    <el-row :gutter="15" class="mb-15px">
      <el-col :span="4">
        <el-card shadow="never" class="text-center">
          <div class="text-24px font-bold text-primary">{{ overview.todoToday ?? 0 }}</div>
          <div class="text-12px text-gray-500">今天的待办</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="text-center">
          <div class="text-24px font-bold">{{ overview.todoUndone ?? 0 }}</div>
          <div class="text-12px text-gray-500">
            未完成待办
            <el-tag v-if="overview.todoOverdue" type="danger" size="small" class="ml-5px">
              过期 {{ overview.todoOverdue }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="text-center">
          <div class="text-24px font-bold text-orange-500">{{ overview.taskDoing ?? 0 }}/{{ overview.taskTotal ?? 0 }}</div>
          <div class="text-12px text-gray-500">进行中 / 我的任务</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="text-center">
          <div class="text-24px font-bold text-red-500">{{ overview.bugActive ?? 0 }}</div>
          <div class="text-12px text-gray-500">未关闭缺陷</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="text-center">
          <div class="text-24px font-bold text-green-600">{{ overview.storyActive ?? 0 }}</div>
          <div class="text-12px text-gray-500">激活需求</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="text-center">
          <div class="text-24px font-bold">{{ overview.effortThisMonth ?? 0 }}</div>
          <div class="text-12px text-gray-500">本月消耗工时</div>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>

  <ContentWrap>
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- ==================== 待办 ==================== -->
      <el-tab-pane label="待办" name="todo">
        <el-form class="-mb-15px" :inline="true" label-width="70px">
          <el-form-item label="范围">
            <el-select v-model="todoQuery.browseType" class="!w-140px" @change="loadTodos">
              <el-option v-for="o in TODO_BROWSE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="todoQuery.status" clearable placeholder="全部状态" class="!w-140px" @change="loadTodos">
              <!-- undone 是聚合状态：未完成 = 不含 done/closed，与概览卡片的「未完成待办」同口径 -->
              <el-option label="未完成" value="undone" />
              <el-option v-for="o in TODO_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="口径">
            <el-select v-model="todoQuery.scope" class="!w-160px" @change="loadTodos">
              <el-option label="指派给我" value="mine" />
              <el-option label="我指派给别人的" value="other" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="loadTodos"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
            <el-button type="primary" plain @click="todoFormRef.open('create')" v-hasPermi="['zentao:todo:create']">
              <Icon icon="ep:plus" class="mr-5px" /> 新建待办
            </el-button>
            <el-button plain @click="handleImportToToday" v-hasPermi="['zentao:todo:update']">
              <Icon icon="ep:download" class="mr-5px" /> 挪到今天
            </el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="todoLoading" :data="todos" empty-text="这个范围里没有待办">
          <el-table-column label="日期" align="center" width="110">
            <template #default="{ row }">
              <span :class="row.overdue ? 'text-red-500 font-bold' : ''">{{ row.date }}</span>
            </template>
          </el-table-column>
          <el-table-column label="时间" align="center" width="110">
            <template #default="{ row }">{{ row.begin || row.end ? `${row.begin || '?'}~${row.end || '?'}` : '-' }}</template>
          </el-table-column>
          <el-table-column label="待办" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag v-if="row.overdue" type="danger" size="small" class="mr-5px">已过期</el-tag>
              <el-tag v-if="row.privateFlag === 1" type="warning" size="small" class="mr-5px">私有</el-tag>
              {{ row.name }}
            </template>
          </el-table-column>
          <el-table-column label="类型" align="center" width="90">
            <template #default="{ row }">
              {{ row.typeName }}
              <span v-if="row.objectID" class="text-gray-400">#{{ row.objectID }}</span>
            </template>
          </el-table-column>
          <el-table-column label="优先级" align="center" prop="pri" width="80" />
          <el-table-column label="状态" align="center" width="90">
            <template #default="{ row }">
              <el-tag :type="todoTagOf(row.status) as any">{{ todoLabelOf(TODO_STATUS_OPTIONS, row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="指派给" align="center" prop="assignedTo" width="100" />
          <el-table-column label="操作" align="center" width="270" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'wait'" link type="primary"
                         @click="handleTodoAction('start', row)" v-hasPermi="['zentao:todo:update']">开始</el-button>
              <el-button v-if="['wait','doing'].includes(row.status)" link type="success"
                         @click="handleTodoAction('finish', row)" v-hasPermi="['zentao:todo:update']">完成</el-button>
              <el-button v-if="row.status !== 'closed'" link type="info"
                         @click="handleTodoAction('close', row)" v-hasPermi="['zentao:todo:update']">关闭</el-button>
              <el-button v-else link type="warning"
                         @click="handleTodoAction('activate', row)" v-hasPermi="['zentao:todo:update']">激活</el-button>
              <el-dropdown class="ml-8px" @command="(cmd: string) => handleTodoAction(cmd, row)">
                <el-button link type="primary">更多<Icon icon="ep:arrow-down" class="ml-2px" /></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit" v-hasPermi="['zentao:todo:update']">编辑</el-dropdown-item>
                    <el-dropdown-item command="assign" v-hasPermi="['zentao:todo:update']">指派给别人</el-dropdown-item>
                    <el-dropdown-item command="delete" divided v-hasPermi="['zentao:todo:delete']">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 我的任务 / 缺陷 / 需求 / 工时 ==================== -->
      <el-tab-pane label="我的任务" name="task">
        <el-table v-loading="loading.task" :data="lists.task" empty-text="没有指派给我的任务">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="任务名称" prop="name" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" prop="status" width="100" align="center" />
          <el-table-column label="优先级" prop="pri" width="80" align="center" />
          <el-table-column label="预计" prop="estimate" width="80" align="center" />
          <el-table-column label="已消耗" prop="consumed" width="90" align="center" />
          <el-table-column label="剩余" prop="left" width="80" align="center" />
          <el-table-column label="所属需求" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.story">#{{ row.story }} {{ row.storyTitle }}</span>
              <span v-else class="text-gray-400">-</span>
            </template>
          </el-table-column>
        </el-table>
        <Pagination :total="totals.task" v-model:page="pages.task.pageNo" v-model:limit="pages.task.pageSize"
                    @pagination="loadTab('task')" />
      </el-tab-pane>

      <el-tab-pane label="我的缺陷" name="bug">
        <el-table v-loading="loading.bug" :data="lists.bug" empty-text="没有指派给我的缺陷">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="缺陷标题" prop="title" min-width="240" show-overflow-tooltip />
          <el-table-column label="状态" prop="status" width="100" align="center" />
          <el-table-column label="严重程度" prop="severity" width="100" align="center" />
          <el-table-column label="优先级" prop="pri" width="80" align="center" />
          <el-table-column label="所属产品" prop="product" width="100" align="center" />
        </el-table>
        <Pagination :total="totals.bug" v-model:page="pages.bug.pageNo" v-model:limit="pages.bug.pageSize"
                    @pagination="loadTab('bug')" />
      </el-tab-pane>

      <el-tab-pane label="我的需求" name="story">
        <el-table v-loading="loading.story" :data="lists.story" empty-text="没有指派给我的需求">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="需求标题" prop="title" min-width="240" show-overflow-tooltip />
          <el-table-column label="状态" prop="status" width="100" align="center" />
          <el-table-column label="阶段" prop="stage" width="100" align="center" />
          <el-table-column label="优先级" prop="pri" width="80" align="center" />
          <el-table-column label="父需求" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.parent">#{{ row.parent }} {{ row.parentTitle }}</span>
              <span v-else class="text-gray-400">-</span>
            </template>
          </el-table-column>
        </el-table>
        <Pagination :total="totals.story" v-model:page="pages.story.pageNo" v-model:limit="pages.story.pageSize"
                    @pagination="loadTab('story')" />
      </el-tab-pane>

      <el-tab-pane label="我的工时" name="effort">
        <el-table v-loading="loading.effort" :data="lists.effort" empty-text="我还没有登记过工时">
          <el-table-column label="日期" prop="date" width="110" align="center" />
          <el-table-column label="任务" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="text-gray-400 mr-5px">#{{ row.objectID }}</span>{{ row.taskName || '(任务已删除)' }}
            </template>
          </el-table-column>
          <el-table-column label="工作内容" prop="work" min-width="220" show-overflow-tooltip />
          <el-table-column label="消耗" prop="consumed" width="80" align="center" />
          <el-table-column label="剩余" prop="left" width="80" align="center" />
        </el-table>
        <Pagination :total="totals.effort" v-model:page="pages.effort.pageNo" v-model:limit="pages.effort.pageSize"
                    @pagination="loadTab('effort')" />
      </el-tab-pane>

      <el-tab-pane label="我参与的项目" name="project">
        <el-table v-loading="loading.project" :data="lists.project" empty-text="没有我参与的项目">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="项目名称" prop="name" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" prop="status" width="100" align="center" />
          <el-table-column label="开始" prop="begin" width="110" align="center" />
          <el-table-column label="结束" prop="end" width="110" align="center" />
          <el-table-column label="负责人" align="center" width="150">
            <template #default="{ row }">PM {{ row.pm || '-' }} / PO {{ row.po || '-' }}</template>
          </el-table-column>
        </el-table>
        <Pagination :total="totals.project" v-model:page="pages.project.pageNo" v-model:limit="pages.project.pageSize"
                    @pagination="loadTab('project')" />
      </el-tab-pane>

      <el-tab-pane label="我参与的执行" name="execution">
        <el-table v-loading="loading.execution" :data="lists.execution" empty-text="没有我参与的执行">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="执行名称" prop="name" min-width="220" show-overflow-tooltip />
          <el-table-column label="类型" prop="type" width="90" align="center" />
          <el-table-column label="状态" prop="status" width="100" align="center" />
          <el-table-column label="开始" prop="begin" width="110" align="center" />
          <el-table-column label="结束" prop="end" width="110" align="center" />
        </el-table>
        <Pagination :total="totals.execution" v-model:page="pages.execution.pageNo" v-model:limit="pages.execution.pageSize"
                    @pagination="loadTab('execution')" />
      </el-tab-pane>

      <el-tab-pane label="我的团队" name="team">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb-10px"
          title="团队行是「项目/执行 × 成员」"
          description="zt_team 每一行代表「某人在某个项目或执行里的角色」。工时口径：limited=yes 时按 days×hours 封顶，否则显示已登记总工时。"
        />
        <el-table v-loading="loading.team" :data="teamList" empty-text="我还没有加入任何项目或执行">
          <el-table-column label="项目/执行" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag size="small" class="mr-5px">{{ row.type === 'project' ? '项目' : '执行' }}</el-tag>
              <span class="text-gray-400 mr-5px">#{{ row.root }}</span>{{ row.rootName || '(已删除)' }}
            </template>
          </el-table-column>
          <el-table-column label="对象状态" align="center" width="110">
            <template #default="{ row }">{{ row.rootStatus || '-' }}</template>
          </el-table-column>
          <el-table-column label="我的角色" prop="role" width="110" align="center" />
          <el-table-column label="加入日期" prop="join" width="110" align="center" />
          <el-table-column label="可用工作日" prop="days" width="110" align="center" />
          <el-table-column label="每天工时" prop="hours" width="100" align="center" />
          <el-table-column label="工时上限" align="center" width="130">
            <template #default="{ row }">
              <el-tag v-if="row.limited === 'yes'" type="warning" size="small">
                {{ row.totalHours ?? '-' }}
              </el-tag>
              <span v-else class="text-gray-400">不限</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我的测试单" name="testtask">
        <el-table v-loading="loading.testtask" :data="lists.testtask" empty-text="没有指派给我的测试单">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="测试单名称" prop="name" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" prop="status" width="100" align="center" />
          <el-table-column label="版本" prop="build" width="80" align="center" />
          <el-table-column label="负责人" prop="owner" width="100" align="center" />
          <el-table-column label="开始" prop="begin" width="110" align="center" />
          <el-table-column label="结束" prop="end" width="110" align="center" />
        </el-table>
        <Pagination :total="totals.testtask" v-model:page="pages.testtask.pageNo" v-model:limit="pages.testtask.pageSize"
                    @pagination="loadTab('testtask')" />
      </el-tab-pane>

      <el-tab-pane label="我的用例" name="case">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="mb-10px"
          title="口径：我创建的「或」我评审过的"
          description="用例表里 openedBy 与 reviewedBy 是 AND 关系，而这里要的是 OR —— 后端拆成两次查询后按 id 去重合并（同一条既是我创建又是我评审时只出现一次）。"
        />
        <el-table v-loading="loading.case" :data="lists.case" empty-text="没有我创建或评审过的用例">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="用例标题" prop="title" min-width="240" show-overflow-tooltip />
          <el-table-column label="类型" prop="type" width="110" align="center" />
          <el-table-column label="阶段" prop="stage" width="110" align="center" />
          <el-table-column label="优先级" prop="pri" width="80" align="center" />
          <el-table-column label="创建人" prop="openedBy" width="100" align="center" />
          <el-table-column label="评审人" prop="reviewedBy" width="120" align="center" />
        </el-table>
        <Pagination :total="totals.case" v-model:page="pages.case.pageNo" v-model:limit="pages.case.pageSize"
                    @pagination="loadTab('case')" />
      </el-tab-pane>

      <el-tab-pane label="我的文档" name="doc">
        <el-table v-loading="loading.doc" :data="lists.doc" empty-text="没有我相关的文档">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="文档标题" prop="title" min-width="240" show-overflow-tooltip />
          <el-table-column label="类型" prop="type" width="100" align="center" />
          <el-table-column label="状态" prop="status" width="100" align="center" />
          <el-table-column label="创建人" prop="addedBy" width="100" align="center" />
          <el-table-column label="指派给" prop="assignedTo" width="100" align="center" />
          <el-table-column label="最后修改" prop="editedBy" width="100" align="center" />
        </el-table>
        <Pagination :total="totals.doc" v-model:page="pages.doc.pageNo" v-model:limit="pages.doc.pageSize"
                    @pagination="loadTab('doc')" />
      </el-tab-pane>

      <el-tab-pane label="我的日历" name="calendar">
        <el-form class="-mb-15px" :inline="true" label-width="70px">
          <el-form-item label="月份">
            <el-date-picker v-model="calendarMonth" type="month" value-format="YYYY-MM"
                            placeholder="选择月份" class="!w-160px" @change="loadCalendar" />
          </el-form-item>
          <el-form-item>
            <el-button @click="loadCalendar"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
            <el-button link type="primary" @click="resetCalendarMonth">回到本月</el-button>
          </el-form-item>
        </el-form>
        <el-empty v-if="calendar.length === 0" description="这个月没有任何待办、任务或测试单" />
        <div v-for="day in calendar" :key="day.date" class="mb-12px">
          <div class="mb-5px">
            <strong>{{ day.date }}</strong>
            <el-tag size="small" type="info" class="ml-8px">{{ day.count }} 项</el-tag>
          </div>
          <el-table :data="day.items" size="small">
            <el-table-column label="类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="CALENDAR_TAG[row.type] || 'info'">{{ CALENDAR_LABEL[row.type] || row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="编号" prop="id" width="80" align="center" />
            <el-table-column label="名称" prop="name" min-width="240" show-overflow-tooltip />
            <el-table-column label="状态" prop="status" width="100" align="center" />
            <el-table-column label="备注" prop="extra" width="160" align="center" />
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的动态" name="action">
        <el-timeline class="mt-10px">
          <el-timeline-item v-for="a in actions" :key="a.id" :timestamp="formatDate(a.date)" placement="top">
            <el-tag size="small" type="info" class="mr-5px">{{ a.objectType }}#{{ a.objectID }}</el-tag>
            <strong>{{ a.actionName || a.action }}</strong>
            <span v-if="a.comment" class="ml-8px text-gray-500">{{ a.comment }}</span>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="actions.length === 0" description="还没有动态" />
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <TodoForm ref="todoFormRef" @success="refreshTodoAndOverview" />
</template>

<script lang="ts" setup>
import * as MyApi from '@/api/zentao/my'
import * as TodoApi from '@/api/zentao/todo'
import { TODO_BROWSE_OPTIONS, TODO_STATUS_OPTIONS } from '@/api/zentao/todo'
import TodoForm from './TodoForm.vue'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'ZentaoMy' })

const message = useMessage()
const activeTab = ref('todo')

const todoLabelOf = (options: { value: string; label: string }[], value?: string) =>
  options.find((o) => o.value === value)?.label ?? value ?? '-'
const todoTagOf = (value?: string) =>
  TODO_STATUS_OPTIONS.find((o) => o.value === value)?.tag ?? 'info'

// ==================== 概览 ====================
const overview = ref<MyApi.MyOverviewVO>({})
const loadOverview = async () => {
  overview.value = await MyApi.getMyOverview()
}

// ==================== 待办 ====================
const todoLoading = ref(false)
const todos = ref<TodoApi.TodoVO[]>([])
const todoFormRef = ref()
const todoQuery = reactive({
  browseType: 'today',
  // 默认「未完成」：与概览卡片的「今天的待办 / 未完成待办」保持同一口径
  status: 'undone' as string | undefined,
  scope: 'mine'
})

const loadTodos = async () => {
  todoLoading.value = true
  try {
    todos.value = await TodoApi.getMyTodoList({
      browseType: todoQuery.browseType,
      status: todoQuery.status,
      assignedToOther: todoQuery.scope === 'other'
    })
  } finally {
    todoLoading.value = false
  }
}

const refreshTodoAndOverview = async () => {
  await Promise.all([loadTodos(), loadOverview()])
}

const handleImportToToday = async () => {
  await message.confirm('把之前没完成的待办都挪到今天？')
  const count = await TodoApi.importToToday(false)
  message.success(`已挪动 ${count} 条待办`)
  await refreshTodoAndOverview()
}

const handleTodoAction = async (cmd: string, row: TodoApi.TodoVO) => {
  const id = row.id!
  try {
    switch (cmd) {
      case 'edit':
        todoFormRef.value.open('edit', row)
        return
      case 'start':
        await TodoApi.startTodo(id)
        message.success('已开始')
        break
      case 'finish':
        await TodoApi.finishTodo(id)
        message.success('已完成')
        break
      case 'close':
        await TodoApi.closeTodo(id)
        message.success('已关闭')
        break
      case 'activate':
        await TodoApi.activateTodo(id)
        message.success('已激活')
        break
      case 'assign': {
        const { value } = await ElMessageBox.prompt('指派给谁（账号）', '指派待办', { inputValue: 'tester' })
        await TodoApi.assignTodo(id, value)
        message.success('已指派')
        break
      }
      case 'delete':
        await message.delConfirm(`确认删除待办「${row.name}」？`)
        await TodoApi.deleteTodo(id)
        message.success('已删除')
        break
    }
    await refreshTodoAndOverview()
  } catch {}
}

// ==================== 各 Tab 的列表 ====================
const PAGED_TABS = ['task', 'bug', 'story', 'effort', 'project', 'execution', 'testtask', 'case', 'doc']
const loading = reactive<Record<string, boolean>>(
  Object.fromEntries(PAGED_TABS.map((t) => [t, false]))
)
const lists = reactive<Record<string, any[]>>(Object.fromEntries(PAGED_TABS.map((t) => [t, []])))
const totals = reactive<Record<string, number>>(Object.fromEntries(PAGED_TABS.map((t) => [t, 0])))
const pages = reactive<Record<string, { pageNo: number; pageSize: number }>>(
  Object.fromEntries(PAGED_TABS.map((t) => [t, { pageNo: 1, pageSize: 10 }]))
)

const loadTab = async (tab: string) => {
  loading[tab] = true
  try {
    const params = { pageNo: pages[tab].pageNo, pageSize: pages[tab].pageSize }
    const fetcher: Record<string, (p: any) => Promise<any>> = {
      task: MyApi.getMyTaskPage,
      bug: MyApi.getMyBugPage,
      story: MyApi.getMyStoryPage,
      effort: MyApi.getMyEffortPage,
      project: MyApi.getMyProjectPage,
      execution: MyApi.getMyExecutionPage,
      testtask: MyApi.getMyTestTaskPage,
      case: MyApi.getMyCasePage,
      doc: MyApi.getMyDocPage
    }
    const data = await fetcher[tab](params)
    lists[tab] = data.list
    totals[tab] = data.total
  } finally {
    loading[tab] = false
  }
}

const actions = ref<any[]>([])
const loadActions = async () => {
  actions.value = await MyApi.getMyActionList(20)
}

// ==================== 我的团队（不分页） ====================
const teamList = ref<MyApi.MyTeamVO[]>([])
const loadTeam = async () => {
  loading.team = true
  try {
    teamList.value = await MyApi.getMyTeamList()
  } finally {
    loading.team = false
  }
}

// ==================== 我的日历（按天分组，后端已排好序） ====================
// el-tag 的 type 是字面量联合类型，用 Record<string, string> 会被 vue-tsc 报 TS2322
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'
const CALENDAR_LABEL: Record<string, string> = { todo: '待办', task: '任务', testtask: '测试单' }
const CALENDAR_TAG: Record<string, TagType> = { todo: 'warning', task: 'primary', testtask: 'success' }
const calendarMonth = ref('')
const calendar = ref<{ date: string; count: number; items: any[] }[]>([])
const loadCalendar = async () => {
  loading.calendar = true
  try {
    // 空字符串要转成 undefined，否则后端 YearMonth.parse('') 会抛异常
    calendar.value = await MyApi.getMyCalendar(calendarMonth.value || undefined)
  } finally {
    loading.calendar = false
  }
}

/** 清空月份筛选并重新查一次（只改 calendarMonth 不会触发请求） */
const resetCalendarMonth = async () => {
  calendarMonth.value = ''
  await loadCalendar()
}

const handleTabChange = async (name: string) => {
  if (name === 'todo') return loadTodos()
  if (name === 'action') return loadActions()
  if (name === 'team') return loadTeam()
  if (name === 'calendar') return loadCalendar()
  return loadTab(name)
}

onMounted(async () => {
  await Promise.all([loadOverview(), loadTodos()])
  // 其余 Tab 的数据等切换时再拉（「我的地盘」首屏只请求两次）
})
</script>
