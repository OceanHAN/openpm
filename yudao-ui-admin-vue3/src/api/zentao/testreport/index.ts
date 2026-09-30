import request from '@/config/axios'

// 用例集（zt_testsuite）
export interface TestSuiteVO {
  id?: number
  product?: number
  project?: number
  name?: string
  desc?: string
  type?: string
  order?: number
  caseCount?: number
  addedBy?: string
  addedDate?: number | string
  lastEditedBy?: string
  lastEditedDate?: number | string
}

// 测试报告（zt_testreport）。数字都是读时现算的
export interface TestReportVO {
  id?: number
  product?: number
  project?: number
  execution?: number
  tasks?: string
  taskNames?: string
  builds?: string
  title?: string
  begin?: string
  end?: string
  owner?: string
  report?: string
  createdBy?: string
  createdDate?: number | string
  caseCount?: number
  runCaseCount?: number
  resultCount?: number
  passCount?: number
  failCount?: number
  stories?: string
  bugs?: string
  cases?: string
  caseSummaries?: {
    caseId?: number
    caseTitle?: string
    runCount?: number
    lastResult?: string
    lastResultName?: string
    lastRunner?: string
    lastRunDate?: number | string
  }[]
}

// ==================== 测试报告 ====================
export const getReportPage = (params: any) => {
  return request.get({ url: '/zentao/testreport/page', params })
}
export const getReport = (id: number) => {
  return request.get<TestReportVO>({ url: '/zentao/testreport/get', params: { id } })
}
export const createReport = (data: TestReportVO) => {
  return request.post<number>({ url: '/zentao/testreport/create', data })
}
export const updateReport = (data: TestReportVO) => {
  return request.put({ url: '/zentao/testreport/update', data })
}
export const deleteReport = (id: number) => {
  return request.delete({ url: '/zentao/testreport/delete', params: { id } })
}
// 预览汇总（不落库）：建报告前先看数字
export const previewReport = (params: { product: number; tasks: string; begin?: string; end?: string }) => {
  return request.get<TestReportVO>({ url: '/zentao/testreport/preview', params })
}

// ==================== 用例集 ====================
export const getSuitePage = (params: any) => {
  return request.get({ url: '/zentao/testreport/suite/page', params })
}
export const getSuite = (id: number) => {
  return request.get<TestSuiteVO>({ url: '/zentao/testreport/suite/get', params: { id } })
}
export const getSuiteListByProduct = (product: number) => {
  return request.get<TestSuiteVO[]>({ url: '/zentao/testreport/suite/list-by-product', params: { product } })
}
export const createSuite = (data: TestSuiteVO) => {
  return request.post<number>({ url: '/zentao/testreport/suite/create', data })
}
export const updateSuite = (data: TestSuiteVO) => {
  return request.put({ url: '/zentao/testreport/suite/update', data })
}
export const deleteSuite = (id: number) => {
  return request.delete({ url: '/zentao/testreport/suite/delete', params: { id } })
}
export const linkSuiteCase = (data: { suiteId: number; caseIds: number[] }) => {
  return request.post<number>({ url: '/zentao/testreport/suite/link-case', data })
}
export const unlinkSuiteCase = (suiteId: number, caseId: number) => {
  return request.delete({ url: '/zentao/testreport/suite/unlink-case', params: { suiteId, caseId } })
}
export const getSuiteCaseList = (suiteId: number) => {
  return request.get({ url: '/zentao/testreport/suite/case-list', params: { suiteId } })
}
export const getSuiteUnlinkedCaseList = (params: { suiteId: number; title?: string }) => {
  return request.get({ url: '/zentao/testreport/suite/unlinked-case-list', params })
}
