import request from '@/config/axios'

// 待办（zt_todo）。注意它和任务（zt_task）不是一回事：
//   task = 项目里要交付的工作；todo = 「我今天要干的几件事」，可以不挂任何项目。
export interface TodoVO {
  id?: number
  /** 归属账号（谁的待办清单） */
  account?: string
  /** 哪天做 */
  date?: string
  /** 起止时间 HHMM */
  begin?: string
  end?: string
  /** custom/cycle/bug/task/story/testtask */
  type?: string
  typeName?: string
  /** 关联对象编号（type 不是 custom/cycle 时必填） */
  objectID?: number
  pri?: number
  name?: string
  desc?: string
  status?: string
  /** 是否私有：1 只有归属人能看到内容 */
  privateFlag?: number
  cycle?: number
  assignedTo?: string
  assignedBy?: string
  assignedDate?: number | string
  finishedBy?: string
  finishedDate?: number | string
  closedBy?: string
  closedDate?: number | string
  /** 是否已过期（未完成且日期早于今天） */
  overdue?: boolean
}

export const TODO_STATUS_OPTIONS = [
  { value: 'wait', label: '未开始', tag: 'info' },
  { value: 'doing', label: '进行中', tag: 'primary' },
  { value: 'done', label: '已完成', tag: 'success' },
  { value: 'closed', label: '已关闭', tag: 'info' }
]

export const TODO_TYPE_OPTIONS = [
  { value: 'custom', label: '自定义' },
  { value: 'cycle', label: '周期' },
  { value: 'bug', label: 'Bug' },
  { value: 'task', label: '任务' },
  { value: 'story', label: '需求' },
  { value: 'testtask', label: '测试单' }
]

export const TODO_PRI_OPTIONS = [
  { value: 1, label: '1（最高）' },
  { value: 2, label: '2（较高）' },
  { value: 3, label: '3（普通）' },
  { value: 4, label: '4（最低）' }
]

/** 浏览范围（禅道 my/todo 的 browseType） */
export const TODO_BROWSE_OPTIONS = [
  { value: 'today', label: '今天' },
  { value: 'tomorrow', label: '明天' },
  { value: 'thisweek', label: '本周' },
  { value: 'before', label: '已过期' },
  { value: 'future', label: '未来' },
  { value: 'all', label: '全部' }
]

export const getTodoPage = (params: any) => {
  return request.get({ url: '/zentao/todo/page', params })
}

export const getTodo = (id: number) => {
  return request.get<TodoVO>({ url: '/zentao/todo/get', params: { id } })
}

/** 我的待办：assignedTo = 我 或 finishedBy = 我 或 closedBy = 我 */
export const getMyTodoList = (params: any) => {
  return request.get<TodoVO[]>({ url: '/zentao/todo/my-list', params })
}

export const createTodo = (data: TodoVO) => {
  return request.post({ url: '/zentao/todo/create', data })
}

export const batchCreateTodo = (names: string[], data: TodoVO) => {
  return request.post({ url: '/zentao/todo/batch-create', params: { names: names.join(',') }, data })
}

export const updateTodo = (data: TodoVO) => {
  return request.put({ url: '/zentao/todo/update', data })
}

export const deleteTodo = (id: number) => {
  return request.delete({ url: '/zentao/todo/delete', params: { id } })
}

export const startTodo = (id: number) => request.put({ url: '/zentao/todo/start', params: { id } })
export const finishTodo = (id: number) => request.put({ url: '/zentao/todo/finish', params: { id } })
export const closeTodo = (id: number) => request.put({ url: '/zentao/todo/close', params: { id } })
export const activateTodo = (id: number) => request.put({ url: '/zentao/todo/activate', params: { id } })
export const assignTodo = (id: number, assignedTo: string) =>
  request.put({ url: '/zentao/todo/assign', data: { id, assignedTo } })
export const importToToday = (all = false) =>
  request.put({ url: '/zentao/todo/import-to-today', params: { all } })
export const getTodoTypeList = () => request.get({ url: '/zentao/todo/type-list' })
export const getTodoStatusList = () => request.get({ url: '/zentao/todo/status-list' })
