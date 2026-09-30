import request from '@/config/axios'

// 报表（report）：全是只读聚合，不产生数据 —— 数据来自各业务模块已经落库的 zt_* 表
export interface ReportOptionVO {
  years: string[]
  current: string
  depts: { id: number; name: string }[]
  users: { account: string; name: string; deptId: number }[]
}

export interface ObjectStatVO {
  statusStat: Record<string, number>
  actionStat: Record<string, Record<string, number>>
}

export interface CaseStatVO {
  resultStat: Record<string, number>
  actionStat: Record<string, Record<string, number>>
}

export interface AnnualDataVO {
  mode: 'company' | 'dept' | 'user'
  year: string
  who: string
  users?: number
  logins?: number
  actions?: number
  todos: { count: number; undone: number; done: number }
  consumed: number
  contributionCount: number
  maxCount: number
  months: string[]
  contributions: Record<string, Record<string, number>>
  contributionGroups: Record<string, Record<string, number>>
  radarData: Record<string, number>
  productStat: any[]
  executionStat: any[]
  storyStat: ObjectStatVO
  taskStat: ObjectStatVO
  bugStat: ObjectStatVO
  caseStat: CaseStatVO
  statusStat: Record<string, Record<string, number>>
  overview: Record<string, string>
}

export interface ReminderVO {
  account: string
  realname: string
  total: number
  bugs: any[]
  tasks: any[]
  todos: any[]
  testTasks: any[]
  cards: any[]
}

export interface ReportOutputVO {
  objectType: string
  objectTypeName: string
  total: number
  actions: { code: string; name: string; total: number }[]
}

export const getReportOptions = () => {
  return request.get<ReportOptionVO>({ url: '/zentao/report/options' })
}

export const getAnnualData = (params: { year?: string; dept?: number; account?: string }) => {
  return request.get<AnnualDataVO>({ url: '/zentao/report/annual-data', params })
}

export const getReminderList = () => {
  return request.get<ReminderVO[]>({ url: '/zentao/report/reminder-list' })
}

export const getReportOutput = (params: { year?: string; account?: string }) => {
  return request.get<ReportOutputVO[]>({ url: '/zentao/report/output', params })
}

export const getProjectStatusOverview = (account?: string) => {
  return request.get<Record<string, number>>({ url: '/zentao/report/project-status', params: { account } })
}
