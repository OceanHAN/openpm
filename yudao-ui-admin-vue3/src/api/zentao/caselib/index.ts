import request from '@/config/axios'
import type { CaseVO } from '@/api/zentao/testcase'

// 用例库 VO
export interface CaseLibVO {
  id?: number
  name?: string
  desc?: string
  order?: number
  type?: string
  /** 库内用例数量 */
  caseCount?: number
  addedBy?: string
  addedDate?: number | string
  lastEditedBy?: string
  lastEditedDate?: number | string
}

/** 库内用例 = 产品用例（CaseVO）+ 来源信息 */
export interface CaseLibCaseVO extends CaseVO {
  lib?: number
  /** 来源产品用例编号（0=库里手建） */
  fromCaseID?: number
  fromCaseVersion?: number
  /** 来源用例已升版 */
  sourceChanged?: boolean
}

// ==================== 用例库 ====================

export const getCaseLibPage = (params: PageParam) => {
  return request.get<PageResult<CaseLibVO[]>>({ url: '/zentao/caselib/page', params })
}

export const getCaseLibList = () => {
  return request.get<CaseLibVO[]>({ url: '/zentao/caselib/list' })
}

export const getCaseLib = (id: number) => {
  return request.get<CaseLibVO>({ url: '/zentao/caselib/get', params: { id } })
}

export const createCaseLib = (data: CaseLibVO) => {
  return request.post<number>({ url: '/zentao/caselib/create', data })
}

export const updateCaseLib = (data: CaseLibVO) => {
  return request.put({ url: '/zentao/caselib/update', data })
}

export const deleteCaseLib = (id: number) => {
  return request.delete({ url: '/zentao/caselib/delete', params: { id } })
}

// ==================== 库内用例 ====================

export const getCaseLibCasePage = (libId: number, params: PageParam) => {
  return request.get<PageResult<CaseLibCaseVO[]>>({
    url: '/zentao/caselib/case-page',
    params: { libId, ...params }
  })
}

export const getCaseLibCase = (id: number) => {
  return request.get<CaseLibCaseVO>({ url: '/zentao/caselib/case-get', params: { id } })
}

export const createCaseLibCase = (libId: number, data: CaseVO) => {
  return request.post<number>({ url: '/zentao/caselib/create-case', params: { libId }, data })
}

// ==================== 产品用例 → 用例库 ====================

/** 可以导入该库的产品用例（已导入的会被后端排除） */
export const getCanImportCasePage = (libId: number, params: PageParam) => {
  return request.get<PageResult<CaseLibCaseVO[]>>({
    url: '/zentao/caselib/can-import-case-page',
    params: { libId, ...params }
  })
}

/** 导入：返回新建的库内用例编号 */
export const importToLib = (libId: number, caseIds: number[]) => {
  return request.post<number[]>({ url: '/zentao/caselib/import-to-lib', params: { libId }, data: caseIds })
}
