import request from '@/config/axios'

// 测试单（zt_testtask）
export interface TestTaskVO {
  id?: number
  product?: number
  project?: number
  execution?: number
  build?: number
  buildName?: string
  name?: string
  type?: string
  owner?: string
  pri?: number
  begin?: string
  end?: string
  realBegan?: string
  realFinishedDate?: number | string
  desc?: string
  report?: string
  status?: string
  statusName?: string
  caseCount?: number
  runCount?: number
  passCount?: number
  failCount?: number
  blockedCount?: number
  unexecutedCount?: number
  createdBy?: string
  createdDate?: number | string
}

// 测试单里的用例执行记录（zt_testrun）
export interface TestRunVO {
  id?: number
  task?: number
  caseId?: number
  caseTitle?: string
  caseType?: string
  casePri?: number
  caseModule?: number
  caseVersion?: number
  latestCaseVersion?: number
  caseChanged?: boolean
  assignedTo?: string
  lastRunResult?: string
  lastRunResultName?: string
  lastRunner?: string
  lastRunDate?: number | string
  status?: string
  stepCount?: number
}

export interface TestResultVO {
  id?: number
  run?: number
  caseId?: number
  version?: number
  caseResult?: string
  caseResultName?: string
  stepResults?: string
  lastRunner?: string
  date?: number | string
}

export const getTestTaskPage = (params: any) => {
  return request.get({ url: '/zentao/testtask/page', params })
}

export const getTestTask = (id: number) => {
  return request.get<TestTaskVO>({ url: '/zentao/testtask/get', params: { id } })
}

export const getTestTaskSimpleList = (product?: number) => {
  return request.get<{ id: number; name: string; status: string }[]>({
    url: '/zentao/testtask/simple-list',
    params: { product }
  })
}

export const createTestTask = (data: TestTaskVO) => {
  return request.post<number>({ url: '/zentao/testtask/create', data })
}

export const updateTestTask = (data: TestTaskVO) => {
  return request.put({ url: '/zentao/testtask/update', data })
}

export const deleteTestTask = (id: number) => {
  return request.delete({ url: '/zentao/testtask/delete', params: { id } })
}

// ==================== 状态流转 ====================
export const startTestTask = (id: number) => {
  return request.put({ url: '/zentao/testtask/start', params: { id } })
}
export const blockTestTask = (id: number, comment?: string) => {
  return request.put({ url: '/zentao/testtask/block', params: { id }, data: { comment } })
}
export const activateTestTask = (id: number, comment?: string) => {
  return request.put({ url: '/zentao/testtask/activate', params: { id }, data: { comment } })
}
export const closeTestTask = (id: number, data: { realFinishedDate: string; report?: string; comment?: string }) => {
  return request.put({ url: '/zentao/testtask/close', params: { id }, data })
}

// ==================== 用例编排 ====================
export const getRunList = (taskId: number) => {
  return request.get<TestRunVO[]>({ url: '/zentao/testtask/run-list', params: { taskId } })
}

export const getLinkableList = (params: { taskId: number; title?: string; module?: number }) => {
  return request.get<TestRunVO[]>({ url: '/zentao/testtask/linkable-list', params })
}

export const getRunListByCase = (caseId: number) => {
  return request.get<TestRunVO[]>({ url: '/zentao/testtask/run-list-by-case', params: { caseId } })
}

export const linkCase = (data: { taskId: number; caseIds: number[]; assignedTo?: string }) => {
  return request.post<number>({ url: '/zentao/testtask/link-case', data })
}

export const unlinkCase = (runId: number) => {
  return request.delete({ url: '/zentao/testtask/unlink-case', params: { runId } })
}

export const assignCase = (runId: number, assignedTo: string) => {
  return request.put({ url: '/zentao/testtask/assign-case', params: { runId, assignedTo } })
}

// ==================== 执行 ====================
// stepResults 的用例级结果由后端算：默认 pass，遇到非 pass/n-a 以它为准，遇到 fail 直接结束
export const runCase = (data: { runId: number; stepResults: { id: number; result: string; remark?: string }[] }) => {
  return request.post<string>({ url: '/zentao/testtask/run-case', data })
}

export const getResultList = (runId: number) => {
  return request.get<TestResultVO[]>({ url: '/zentao/testtask/result-list', params: { runId } })
}

export const getTestTaskStatusList = () => {
  return request.get<{ value: string; label: string }[]>({ url: '/zentao/testtask/status-list' })
}

export const getTestResultEnumList = () => {
  return request.get<{ value: string; label: string }[]>({ url: '/zentao/testtask/result-enum-list' })
}
