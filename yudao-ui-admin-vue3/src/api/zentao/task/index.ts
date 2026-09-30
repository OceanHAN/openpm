import request from '@/config/axios'

// 禅道任务 VO
export interface TaskVO {
  id?: number
  project?: number
  execution?: number
  module?: number
  story?: number
  /** 建任务时需求的版本（冻结） */
  storyVersion?: number
  storyTitle?: string
  latestStoryVersion?: number
  /** 需求已升版且仍激活 → 任务需要确认 */
  storyChanged?: boolean
  fromBug?: number
  name?: string
  type?: string
  pri?: number
  estimate?: number
  consumed?: number
  left?: number
  deadline?: string
  keywords?: string
  desc?: string
  version?: number
  status?: string
  openedBy?: string
  openedDate?: number | string
  assignedTo?: string
  assignedDate?: number | string
  estStarted?: string
  realStarted?: number | string
  finishedBy?: string
  finishedDate?: number | string
  canceledBy?: string
  canceledDate?: number | string
  closedBy?: string
  closedDate?: number | string
  closedReason?: string
  lastEditedBy?: string
  lastEditedDate?: number | string
  activatedDate?: number | string
  createTime?: number
}

// 完成任务 VO
export interface TaskFinishVO {
  id: number
  consumed: number
  left: number
  comment?: string
}

// 查询任务分页
export const getTaskPage = (params: PageParam) => {
  return request.get<PageResult<TaskVO[]>>({ url: '/zentao/task/page', params })
}

// 查询任务详情
export const getTask = (id: number) => {
  return request.get<TaskVO>({ url: '/zentao/task/get', params: { id } })
}

// 新增任务
export const createTask = (data: TaskVO) => {
  return request.post({ url: '/zentao/task/create', data })
}

// 修改任务
export const updateTask = (data: TaskVO) => {
  return request.put({ url: '/zentao/task/update', data })
}

// 开始任务
export const startTask = (id: number) => {
  return request.put({ url: '/zentao/task/start', params: { id } })
}

// 完成任务（登记消耗与剩余工时）
export const finishTask = (data: TaskFinishVO) => {
  return request.put({ url: '/zentao/task/finish', data })
}

// 关闭任务
export const closeTask = (id: number, reason?: string) => {
  return request.put({ url: '/zentao/task/close', params: { id, reason } })
}

// 取消任务
export const cancelTask = (id: number) => {
  return request.put({ url: '/zentao/task/cancel', params: { id } })
}

// 激活任务
export const activateTask = (id: number) => {
  return request.put({ url: '/zentao/task/activate', params: { id } })
}

// 删除任务
export const deleteTask = (id: number) => {
  return request.delete({ url: '/zentao/task/delete', params: { id } })
}

// 批量删除任务
export const deleteTaskList = (ids: number[]) => {
  return request.delete({ url: '/zentao/task/delete-list', params: { ids: ids.join(',') } })
}

// ==================== 需求转任务 ====================

// 把一个需求拆成若干任务（只给名称，优先级从需求继承，需求版本会冻结在任务上）
export const batchCreateTaskFromStory = (params: {
  storyId: number
  execution: number
  project: number
  names: string[]
}) => {
  return request.post<number[]>({
    url: '/zentao/task/batch-create-from-story',
    params: { storyId: params.storyId, execution: params.execution, project: params.project },
    data: params.names
  })
}

// 某需求下的全部任务
export const getTaskListByStory = (story: number) => {
  return request.get<TaskVO[]>({ url: '/zentao/task/list-by-story', params: { story } })
}
