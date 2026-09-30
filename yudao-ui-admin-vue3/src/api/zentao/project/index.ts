import request from '@/config/axios'

// 禅道项目 VO
export interface ProjectVO {
  id?: number
  /** 所属项目。执行专用；项目自身为 0 */
  project?: number
  parent?: number
  path?: string
  grade?: number
  model?: string
  type?: string
  category?: string
  name?: string
  code?: string
  desc?: string
  output?: string
  hasProduct?: number
  multiple?: number
  budget?: number
  budgetUnit?: string
  begin?: string
  end?: string
  realBegan?: string
  realEnd?: string
  days?: number
  status?: string
  pri?: number
  milestone?: number
  estimate?: number
  left?: number
  consumed?: number
  progress?: number
  PO?: string
  PM?: string
  QD?: string
  RD?: string
  team?: string
  teamCount?: number
  acl?: string
  openedBy?: string
  openedDate?: number | string
  lastEditedBy?: string
  lastEditedDate?: number | string
  closedBy?: string
  closedDate?: number | string
  closedReason?: string
  suspendedDate?: number | string
  activatedDate?: number | string
  order?: number
  createTime?: number
}

// 查询项目分页
export const getProjectPage = (params: PageParam) => {
  return request.get<PageResult<ProjectVO[]>>({ url: '/zentao/project/page', params })
}

// 查询项目详情
export const getProject = (id: number) => {
  return request.get<ProjectVO>({ url: '/zentao/project/get', params: { id } })
}

// 新增项目
export const createProject = (data: ProjectVO) => {
  return request.post({ url: '/zentao/project/create', data })
}

// 修改项目
export const updateProject = (data: ProjectVO) => {
  return request.put({ url: '/zentao/project/update', data })
}

// 开始项目
export const startProject = (id: number) => {
  return request.put({ url: '/zentao/project/start', params: { id } })
}

// 挂起项目
export const suspendProject = (id: number) => {
  return request.put({ url: '/zentao/project/suspend', params: { id } })
}

// 激活项目
export const activateProject = (id: number) => {
  return request.put({ url: '/zentao/project/activate', params: { id } })
}

// 关闭项目
export const closeProject = (id: number, reason?: string) => {
  return request.put({ url: '/zentao/project/close', params: { id, reason } })
}

// 删除项目
export const deleteProject = (id: number) => {
  return request.delete({ url: '/zentao/project/delete', params: { id } })
}

// 批量删除
export const deleteProjectList = (ids: number[]) => {
  return request.delete({ url: '/zentao/project/delete-list', params: { ids: ids.join(',') } })
}

// 未关闭的项目列表（下拉用）
export const getProjectSimpleList = () => {
  return request.get<ProjectVO[]>({ url: '/zentao/project/simple-list' })
}
