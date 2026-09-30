import request from '@/config/axios'
import type { ProjectVO } from '@/api/zentao/project'

// 执行与项目共用后端表，VO 结构一致，直接复用 ProjectVO
export type ExecutionVO = ProjectVO

// 查询执行分页
export const getExecutionPage = (params: PageParam) => {
  return request.get<PageResult<ExecutionVO[]>>({ url: '/zentao/execution/page', params })
}

// 查询执行详情
export const getExecution = (id: number) => {
  return request.get<ExecutionVO>({ url: '/zentao/execution/get', params: { id } })
}

// 新增执行
export const createExecution = (data: ExecutionVO) => {
  return request.post({ url: '/zentao/execution/create', data })
}

// 修改执行
export const updateExecution = (data: ExecutionVO) => {
  return request.put({ url: '/zentao/execution/update', data })
}

// 开始执行
export const startExecution = (id: number) => {
  return request.put({ url: '/zentao/execution/start', params: { id } })
}

// 挂起执行
export const suspendExecution = (id: number) => {
  return request.put({ url: '/zentao/execution/suspend', params: { id } })
}

// 激活执行
export const activateExecution = (id: number) => {
  return request.put({ url: '/zentao/execution/activate', params: { id } })
}

// 关闭执行
export const closeExecution = (id: number, reason?: string) => {
  return request.put({ url: '/zentao/execution/close', params: { id, reason } })
}

// 删除执行
export const deleteExecution = (id: number) => {
  return request.delete({ url: '/zentao/execution/delete', params: { id } })
}

// 批量删除
export const deleteExecutionList = (ids: number[]) => {
  return request.delete({ url: '/zentao/execution/delete-list', params: { ids: ids.join(',') } })
}

// 某项目下的全部执行
export const getExecutionListByProject = (project: number) => {
  return request.get<ExecutionVO[]>({ url: '/zentao/execution/list-by-project', params: { project } })
}

// 某项目下的执行数量
export const countExecutionByProject = (project: number) => {
  return request.get<number>({ url: '/zentao/execution/count-by-project', params: { project } })
}

// ==================== 燃尽图（禅道 execution 的 burn / computeBurn）====================

export interface BurnChartVO {
  executionId: number
  executionName: string
  begin: string
  end: string
  burnBy: string
  type: string
  interval: number
  labels: string[]
  burnLine: (number | null)[]
  baseLine: number[]
  delayLine?: (number | null)[] | null
  rows: { date: string; estimate: number; left: number; consumed: number; storyPoint: number }[]
  firstValue: number
}

/** 燃尽图数据：三条线（实际/理想/延期）与 labels 等长 */
export const getBurnData = (params: {
  id: number
  type?: string
  burnBy?: string
  interval?: number
}) => {
  return request.get<BurnChartVO>({ url: '/zentao/execution/burn-data', params })
}

/** 重新计算燃尽图：把当天汇总写成一条快照，返回写入条数 */
export const computeBurn = (id?: number) => {
  return request.post({ url: '/zentao/execution/compute-burn', params: { id } })
}
