import request from '@/config/axios'

// 我的地盘（my）：它是查询层，不产生数据 —— 各 Tab 都是「带上我的账号」去调各模块的接口
export interface MyOverviewVO {
  account?: string
  todoToday?: number
  todoUndone?: number
  todoOverdue?: number
  taskTotal?: number
  taskDoing?: number
  bugActive?: number
  storyActive?: number
  effortThisMonth?: number
}

export const getMyOverview = (account?: string) => {
  return request.get<MyOverviewVO>({ url: '/zentao/my/overview', params: { account } })
}

export const getMyTaskPage = (params: any) => {
  return request.get({ url: '/zentao/my/task-page', params })
}

export const getMyBugPage = (params: any) => {
  return request.get({ url: '/zentao/my/bug-page', params })
}

export const getMyStoryPage = (params: any) => {
  return request.get({ url: '/zentao/my/story-page', params })
}

export const getMyEffortPage = (params: any) => {
  return request.get({ url: '/zentao/my/effort-page', params })
}

export const getMyActionList = (limit = 20) => {
  return request.get({ url: '/zentao/my/action-list', params: { limit } })
}

// ==================== 第二组：我参与的对象 ====================
// 「我参与」的判定口径统一放在后端（负责人字段 OR FIND_IN_SET(team)），前端只透传我的账号

export interface MyTeamVO {
  id?: number
  root?: number
  rootName?: string
  rootStatus?: string
  type?: string
  account?: string
  role?: string
  join?: string
  days?: number
  hours?: number
  totalHours?: number
  limited?: string
}

export const getMyProjectPage = (params: any) => {
  return request.get({ url: '/zentao/my/project-page', params })
}

export const getMyExecutionPage = (params: any) => {
  return request.get({ url: '/zentao/my/execution-page', params })
}

export const getMyTeamList = (account?: string) => {
  return request.get<MyTeamVO[]>({ url: '/zentao/my/team-list', params: { account } })
}

export const getMyTestTaskPage = (params: any) => {
  return request.get({ url: '/zentao/my/testtask-page', params })
}

export const getMyCasePage = (params: any) => {
  return request.get({ url: '/zentao/my/case-page', params })
}

export const getMyDocPage = (params: any) => {
  return request.get({ url: '/zentao/my/doc-page', params })
}

export const getMyCalendar = (month?: string, account?: string) => {
  return request.get({ url: '/zentao/my/calendar', params: { month, account } })
}
