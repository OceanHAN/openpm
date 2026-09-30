import request from '@/config/axios'

// 测试仪表盘（qa）：只读聚合，口径取自禅道 module/bi/config/metrics.php 的度量定义
export interface QaDashboardVO {
  days: number
  begin: string
  product?: number
  summary: {
    productCount: number
    bugTotal: number
    bugActive: number
    bugResolved: number
    bugClosed: number
    bugEffective: number
    bugFixed: number
    bugFixRate: number
    bugOpenedInRange: number
    bugResolvedInRange: number
    bugClosedInRange: number
    caseTotal: number
    caseWait: number
    testTaskTotal: number
    testTaskUnclosed: number
  }
  productQuality: any[]
  bugStatus: { name: string; value: number }[]
  bugSeverity: { name: number; value: number }[]
  bugResolution: { name: string; value: number }[]
  caseStatus: { name: string; value: number }[]
  caseResult: { name: string; value: number }[]
  testTaskStatus: { name: string; value: number }[]
  pendingBugs: any[]
  reviewCases: any[]
  unclosedTestTasks: any[]
}

export const getQaDashboard = (params: { product?: number; days?: number }) => {
  return request.get<QaDashboardVO>({ url: '/zentao/qa/dashboard', params })
}
