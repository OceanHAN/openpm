import request from '@/config/axios'
import type { StoryVO } from '@/api/zentao/story'

// 产品计划 VO。禅道三个特色：
//   branch 是逗号列表（一个计划可覆盖多个分支）
//   「待定」用日期哨兵 2030-01-01 表示（后端会给 future 标记）
//   parent：0 独立 / >0 子计划 / -1 有子计划
export interface PlanVO {
  id?: number
  product?: number
  productName?: string
  branch?: string
  branchName?: string
  /** 表单提交用：分支编号数组（后端拼成逗号列表） */
  branches?: number[]
  parent?: number
  title?: string
  status?: string
  statusName?: string
  desc?: string
  begin?: string
  end?: string
  future?: boolean
  finishedDate?: number | string
  closedDate?: number | string
  closedReason?: string
  createdBy?: string
  createdDate?: number | string
  childCount?: number
  storyCount?: number
  bugCount?: number
}

// 查询计划分页
export const getPlanPage = (params: PageParam) => {
  return request.get<PageResult<PlanVO[]>>({ url: '/zentao/plan/page', params })
}

// 查询计划详情
export const getPlan = (id: number) => {
  return request.get<PlanVO>({ url: '/zentao/plan/get', params: { id } })
}

// 某产品下的计划列表
export const getPlanListByProduct = (product: number, branch?: number) => {
  return request.get<PlanVO[]>({ url: '/zentao/plan/list-by-product', params: { product, branch } })
}

// 新增计划
export const createPlan = (data: PlanVO) => {
  return request.post({ url: '/zentao/plan/create', data })
}

// 修改计划
export const updatePlan = (data: PlanVO) => {
  return request.put({ url: '/zentao/plan/update', data })
}

// 开始计划
export const startPlan = (id: number) => {
  return request.put({ url: '/zentao/plan/start', params: { id } })
}

// 完成计划
export const finishPlan = (id: number) => {
  return request.put({ url: '/zentao/plan/finish', params: { id } })
}

// 关闭计划
export const closePlan = (id: number, reason: string) => {
  return request.put({ url: '/zentao/plan/close', params: { id, reason } })
}

// 激活计划
export const activatePlan = (id: number) => {
  return request.put({ url: '/zentao/plan/activate', params: { id } })
}

// 删除计划
export const deletePlan = (id: number) => {
  return request.delete({ url: '/zentao/plan/delete', params: { id } })
}

// 计划下的需求
export const getPlanStoryList = (plan: number) => {
  return request.get<StoryVO[]>({ url: '/zentao/plan/story-list', params: { plan } })
}

// 还没关联到计划的需求（候选）
export const getUnlinkedStoryList = (plan: number) => {
  return request.get<StoryVO[]>({ url: '/zentao/plan/unlinked-story-list', params: { plan } })
}

// 关联需求到计划
export const linkStory = (plan: number, ids: number[]) => {
  return request.put({ url: '/zentao/plan/link-story', data: { plan, ids } })
}

// 从计划移除需求
export const unlinkStory = (plan: number, story: number) => {
  return request.delete({ url: '/zentao/plan/unlink-story', params: { plan, story } })
}

// 关联 Bug 到计划
export const linkBug = (plan: number, ids: number[]) => {
  return request.put({ url: '/zentao/plan/link-bug', data: { plan, ids } })
}

// 从计划移除 Bug
export const unlinkBug = (plan: number, bug: number) => {
  return request.delete({ url: '/zentao/plan/unlink-bug', params: { plan, bug } })
}

// 关闭原因列表
export const getReasonList = () => {
  return request.get<Array<{ value: string; label: string }>>({ url: '/zentao/plan/reason-list' })
}
