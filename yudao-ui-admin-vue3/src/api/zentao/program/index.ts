import request from '@/config/axios'

// 项目集（zt_project 里 type='program' 的行）
//
// 禅道 config/zentaopms.php 里三个常量指向同一张表：
//   TABLE_PROGRAM / TABLE_PROJECT / TABLE_EXECUTION 都是 `zt_project`
// 所以项目集没有独立的表，靠 type 区分；项目用 parent 指向所属项目集。
export interface ProgramVO {
  id?: number
  /** 上级项目集，0 = 顶级 */
  parent?: number
  parentName?: string
  name?: string
  code?: string
  status?: string
  PM?: string
  budget?: number
  budgetUnit?: string
  /** 层级，顶级项目集为 1 */
  grade?: number
  /** 逗号格式的层级路径，包含自己（,9001,9002,） */
  path?: string
  begin?: string
  end?: string
  realBegan?: string
  realEnd?: string
  pri?: number
  acl?: string
  desc?: string
  /** 下级项目集数量 */
  childCount?: number
  /** 项目数量 */
  projectCount?: number
  /** 产品数量 */
  productCount?: number
  openedBy?: string
  openedDate?: number | string
  lastEditedBy?: string
  lastEditedDate?: number | string
  /** 前端建树用 */
  children?: ProgramVO[]
}

export const getProgramPage = (params: any) => {
  return request.get({ url: '/zentao/program/page', params })
}

export const getProgram = (id: number) => {
  return request.get<ProgramVO>({ url: '/zentao/program/get', params: { id } })
}

/** 全部项目集（含统计），前端自行建树 */
export const getProgramList = () => {
  return request.get<ProgramVO[]>({ url: '/zentao/program/list' })
}

export const getProgramSimpleList = () => {
  return request.get<ProgramVO[]>({ url: '/zentao/program/simple-list' })
}

export const getProgramListByParent = (parent: number) => {
  return request.get<ProgramVO[]>({ url: '/zentao/program/list-by-parent', params: { parent } })
}

export const createProgram = (data: ProgramVO) => {
  return request.post({ url: '/zentao/program/create', data })
}

export const updateProgram = (data: ProgramVO) => {
  return request.put({ url: '/zentao/program/update', data })
}

export const deleteProgram = (id: number) => {
  return request.delete({ url: '/zentao/program/delete', params: { id } })
}

export const startProgram = (id: number) => {
  return request.put({ url: '/zentao/program/start', params: { id } })
}

export const suspendProgram = (id: number) => {
  return request.put({ url: '/zentao/program/suspend', params: { id } })
}

export const activateProgram = (id: number) => {
  return request.put({ url: '/zentao/program/activate', params: { id } })
}

export const closeProgram = (id: number, reason?: string) => {
  return request.put({ url: '/zentao/program/close', params: { id, reason } })
}

/** 项目集下的项目 */
export const getProgramProjectList = (programId: number) => {
  return request.get({ url: '/zentao/program/project-list', params: { programId } })
}

/** 项目集下的产品 */
export const getProgramProductList = (programId: number) => {
  return request.get({ url: '/zentao/program/product-list', params: { programId } })
}
