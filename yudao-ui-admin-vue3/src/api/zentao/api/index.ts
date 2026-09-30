import request from '@/config/axios'

// 接口文档库（禅道 module/api）
//
// 注意它不是「对外 REST 接口管理」，而是接口文档：
//   库（zt_doclib 里 type='api'）→ 目录（zt_module type='api'）→ 接口（zt_api）
//   → 可复用结构（zt_apistruct）→ 发布版本（zt_api_lib_release，snap 是快照）
//
// 三条链路在接口上体现得很直接：
//   ① 版本链：zt_api 存当前值，(doc,version) 的 zt_apispec 存每一版；get 传 version 就能看历史
//   ② 发布冻结：release-create 把 modules/apis/structs 打成 snap；get/page 传 releaseID 走冻结版本
//   ③ 目录树：复用 @/api/zentao/module 的 getModuleTree(root=lib, type='api')

/** 接口库（就是 zt_doclib 里 type='api' 的记录） */
export interface ApiLibVO {
  id?: number
  type?: string
  product?: number
  project?: number
  execution?: number
  name?: string
  /** 请求基础路径，禅道 zt_doclib.baseUrl */
  baseUrl?: string
  acl?: string
  desc?: string
  order?: number
  apiCount?: number
  structCount?: number
  addedBy?: string
  addedDate?: number | string
}

/** 接口（当前值） */
export interface ApiVO {
  id?: number
  product?: number
  lib?: number
  libName?: string
  module?: number
  moduleName?: string
  title?: string
  path?: string
  protocol?: string
  method?: string
  requestType?: string
  /** 响应格式：禅道的 create/edit 表单里没有这一项，只读 */
  responseType?: string
  status?: string
  statusName?: string
  owner?: string
  desc?: string
  version?: number
  /** 请求参数树 JSON（原样字符串）：{header:[],params:[],paramsType,query:[]} */
  params?: string
  paramsExample?: string
  responseExample?: string
  /** 响应字段树 JSON（原样字符串） */
  response?: string
  commonParams?: string
  addedBy?: string
  addedDate?: number | string
  editedBy?: string
  editedDate?: number | string
  versionCount?: number
  /** 详情里才有：本次返回的是第几版（0=当前值） */
  viewingVersion?: number
  releaseID?: number
  releaseVersion?: string
  versionList?: ApiVersionVO[]
}

/** 版本链上的一格（接口版本 / 结构版本共用） */
export interface ApiVersionVO {
  version?: number
  addedBy?: string
  addedDate?: number | string
  current?: boolean
}

/** 可复用数据结构 */
export interface ApiStructVO {
  id?: number
  lib?: number
  libName?: string
  name?: string
  type?: string
  desc?: string
  version?: number
  /** 字段树 JSON（原样字符串） */
  attribute?: string
  addedBy?: string
  addedName?: string
  addedDate?: number | string
  editedBy?: string
  editedDate?: number | string
  versionCount?: number
  versionList?: ApiVersionVO[]
}

/** 发布版本快照里的一格：只有编号 + 冻结时的版本号 */
export interface ApiSnapItemVO {
  id?: number
  version?: number
}

/** 发布版本 */
export interface ApiReleaseVO {
  id?: number
  lib?: number
  desc?: string
  version?: string
  addedBy?: string
  addedDate?: number | string
  moduleCount?: number
  apiCount?: number
  structCount?: number
  snapApis?: ApiSnapItemVO[]
  snapStructs?: ApiSnapItemVO[]
  snap?: string
}

/** 字段树节点（attribute / params.params 里的形状，见禅道 common.ui.js） */
export interface ApiFieldNode {
  field?: string
  paramsType?: string
  required?: string | boolean
  desc?: string
  structType?: string
  children?: ApiFieldNode[]
}

// ==================== 接口库 ====================

/** 接口库列表（zt_doclib type='api'，附接口/结构计数） */
export const getApiLibList = () => {
  return request.get<ApiLibVO[]>({ url: '/zentao/api/lib-list' })
}

// ==================== 接口 ====================

export const getApiPage = (params: any) => {
  return request.get({ url: '/zentao/api/page', params })
}

/** 传 version 看历史版本；传 releaseID 看发布冻结的版本 */
export const getApi = (id: number, version?: number, releaseID?: number) => {
  return request.get<ApiVO>({ url: '/zentao/api/get', params: { id, version, releaseID } })
}

export const createApi = (data: ApiVO) => {
  return request.post<number>({ url: '/zentao/api/create', data })
}

export const updateApi = (data: ApiVO) => {
  return request.put({ url: '/zentao/api/update', data })
}

/** 被发布版本冻结、或被数据结构引用时会被拒绝（这是本实现加的加固） */
export const deleteApi = (id: number) => {
  return request.delete({ url: '/zentao/api/delete', params: { id } })
}

// ==================== 数据结构 ====================

export const getApiStructPage = (params: any) => {
  return request.get({ url: '/zentao/api/struct-page', params })
}

export const getApiStruct = (id: number, version?: number) => {
  return request.get<ApiStructVO>({ url: '/zentao/api/struct-get', params: { id, version } })
}

export const createApiStruct = (data: ApiStructVO) => {
  return request.post<number>({ url: '/zentao/api/struct-create', data })
}

// ==================== 发布版本 ====================

export const getApiReleaseList = (libID?: number) => {
  return request.get<ApiReleaseVO[]>({ url: '/zentao/api/release-list', params: { libID } })
}

/** 发布：把当前库的 modules/apis/structs 打成 snap 快照冻结 */
export const createApiRelease = (data: { lib: number; version: string; desc?: string }) => {
  return request.post<number>({ url: '/zentao/api/release-create', data })
}

/** 删除发布版本（物理删除）；删掉之后被它冻结的接口才能删除 */
export const deleteApiRelease = (id: number) => {
  return request.delete({ url: '/zentao/api/release-delete', params: { id } })
}
