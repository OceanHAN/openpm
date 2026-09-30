import request from '@/config/axios'

// 禅道缺陷 VO
export interface BugVO {
  id?: number
  product?: number
  project?: number
  execution?: number
  module?: number
  branch?: number // 所属分支/平台
  story?: number
  task?: number
  title?: string
  keywords?: string
  severity?: number
  pri?: number
  type?: string
  os?: string
  browser?: string
  steps?: string
  status?: string
  confirmed?: number
  activatedCount?: number
  activatedDate?: number | string
  openedBy?: string
  openedDate?: number | string
  openedBuild?: string
  assignedTo?: string
  assignedDate?: number | string
  deadline?: string
  resolvedBy?: string
  resolution?: string
  resolvedBuild?: string
  resolvedDate?: number | string
  closedBy?: string
  closedDate?: number | string
  duplicateBug?: number
  lastEditedBy?: string
  lastEditedDate?: number | string
  createTime?: number
}

// 解决缺陷 VO
export interface BugResolveVO {
  id: number
  resolution: string
  resolvedBuild?: string
  duplicateBug?: number
  comment?: string
}

// 查询缺陷分页
export const getBugPage = (params: PageParam) => {
  return request.get<PageResult<BugVO[]>>({ url: '/zentao/bug/page', params })
}

// 查询缺陷详情
export const getBug = (id: number) => {
  return request.get<BugVO>({ url: '/zentao/bug/get', params: { id } })
}

// 新增缺陷
export const createBug = (data: BugVO) => {
  return request.post({ url: '/zentao/bug/create', data })
}

// 修改缺陷
export const updateBug = (data: BugVO) => {
  return request.put({ url: '/zentao/bug/update', data })
}

// 解决缺陷
export const resolveBug = (data: BugResolveVO) => {
  return request.put({ url: '/zentao/bug/resolve', data })
}

// 关闭缺陷
export const closeBug = (id: number) => {
  return request.put({ url: '/zentao/bug/close', params: { id } })
}

// 重新激活缺陷
export const activateBug = (id: number, comment?: string) => {
  return request.put({ url: '/zentao/bug/activate', params: { id, comment } })
}

// 删除缺陷
export const deleteBug = (id: number) => {
  return request.delete({ url: '/zentao/bug/delete', params: { id } })
}

// 批量删除缺陷
export const deleteBugList = (ids: number[]) => {
  return request.delete({ url: '/zentao/bug/delete-list', params: { ids: ids.join(',') } })
}
