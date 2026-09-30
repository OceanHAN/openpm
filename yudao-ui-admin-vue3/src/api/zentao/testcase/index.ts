import request from '@/config/axios'

// 用例步骤（zt_casestep）
//   type=group 是「步骤组」（只有描述、没有预期结果）
//   **接口约定**：parent 是「本次提交数组里父步骤组的 0 基下标」；
//   顶层步骤不传 parent（传负数也行）—— 注意 0 是合法下标，代表第一个步骤组
export interface CaseStepVO {
  id?: number
  parent?: number
  type?: string
  desc?: string
  expect?: string
  // 后端算出的层级编号（1. / 1.1 / 1.1.1）与层级，只读
  name?: string
  grade?: number
}

// 测试用例（zt_case + zt_casespec）
export interface CaseVO {
  id?: number
  product?: number
  branch?: number
  module?: number
  story?: number
  storyTitle?: string
  storyVersion?: number
  latestStoryVersion?: number
  needConfirm?: boolean
  title?: string
  precondition?: string
  keywords?: string
  pri?: number
  type?: string
  typeName?: string
  stage?: string
  stageName?: string
  status?: string
  statusName?: string
  version?: number
  lastRunResult?: string
  lastRunner?: string
  lastRunDate?: number | string
  openedBy?: string
  openedDate?: number | string
  reviewedBy?: string
  reviewedDate?: number | string
  lastEditedBy?: string
  lastEditedDate?: number | string
  fromBug?: number
  steps?: CaseStepVO[]
}

export interface CaseSpecVO {
  id?: number
  caseId?: number
  version?: number
  title?: string
  precondition?: string
  files?: string
  stepCount?: number
  current?: boolean
}

export const getCasePage = (params: any) => {
  return request.get({ url: '/zentao/testcase/page', params })
}

export const getCase = (id: number, version?: number) => {
  return request.get<CaseVO>({ url: '/zentao/testcase/get', params: { id, version } })
}

export const getCaseStepList = (id: number, version?: number) => {
  return request.get<CaseStepVO[]>({ url: '/zentao/testcase/step-list', params: { id, version } })
}

export const getCaseSpecList = (id: number) => {
  return request.get<CaseSpecVO[]>({ url: '/zentao/testcase/spec-list', params: { id } })
}

export const getCaseListByStory = (story: number) => {
  return request.get<CaseVO[]>({ url: '/zentao/testcase/list-by-story', params: { story } })
}

export const createCase = (data: CaseVO) => {
  return request.post<number>({ url: '/zentao/testcase/create', data })
}

export const updateCase = (data: CaseVO) => {
  return request.put({ url: '/zentao/testcase/update', data })
}

export const deleteCase = (id: number) => {
  return request.delete({ url: '/zentao/testcase/delete', params: { id } })
}

export const reviewCase = (data: { id: number; result: string; comment?: string }) => {
  return request.put({ url: '/zentao/testcase/review', data })
}

export const confirmStoryChange = (id: number) => {
  return request.put({ url: '/zentao/testcase/confirm-story-change', params: { id } })
}

export const getCaseTypeList = () => {
  return request.get<{ value: string; label: string }[]>({ url: '/zentao/testcase/type-list' })
}

export const getCaseStageList = () => {
  return request.get<{ value: string; label: string }[]>({ url: '/zentao/testcase/stage-list' })
}

export const getCaseStatusList = () => {
  return request.get<{ value: string; label: string }[]>({ url: '/zentao/testcase/status-list' })
}
