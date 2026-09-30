import request from '@/config/axios'

// 工时明细（zt_effort）
//
// 一条工时 = 谁、哪天、为哪个任务花了多久、**这之后还剩多久**。
// 任务的 consumed 是工时的加总，而 left 以「最后一条工时声明的值」为准 —— 见后端 EffortServiceImpl。
export interface EffortVO {
  id?: number
  /** 对象类型，固定 task */
  objectType?: string
  /** 任务编号（禅道字段名就是 objectID） */
  objectID?: number
  taskName?: string
  product?: string
  project?: number
  execution?: number
  account?: string
  work?: string
  /** 工作日期，yyyy-MM-dd */
  date?: string
  /** 这之后剩余工时 */
  left?: number
  /** 本次消耗工时 */
  consumed?: number
  /** 开始时间 HHMM */
  begin?: string
  /** 结束时间 HHMM */
  end?: string
  createTime?: number | string
}

// 任务的工时统计
export interface EffortTaskStatVO {
  taskId?: number
  taskName?: string
  estimate?: number
  consumed?: number
  left?: number
  status?: string
  effortCount?: number
  efforts?: EffortVO[]
}

// 按账号汇总
export interface EffortSummaryVO {
  account?: string
  taskCount?: number
  effortCount?: number
  consumed?: number
}

export const getEffortPage = (params: any) => {
  return request.get({ url: '/zentao/effort/page', params })
}

export const getEffort = (id: number) => {
  return request.get({ url: '/zentao/effort/get', params: { id } })
}

export const getEffortListByTask = (taskId: number) => {
  return request.get({ url: '/zentao/effort/list', params: { taskId } })
}

export const getEffortSummary = (params: any) => {
  return request.get<EffortSummaryVO[]>({ url: '/zentao/effort/summary', params })
}

export const getTaskStat = (taskId: number) => {
  return request.get<EffortTaskStatVO>({ url: '/zentao/effort/task-stat', params: { taskId } })
}

export const createEffort = (data: EffortVO & { taskId: number }) => {
  return request.post({ url: '/zentao/effort/create', data })
}

export const updateEffort = (data: EffortVO & { taskId: number }) => {
  return request.put({ url: '/zentao/effort/update', data })
}

export const deleteEffort = (id: number) => {
  return request.delete({ url: '/zentao/effort/delete', params: { id } })
}
