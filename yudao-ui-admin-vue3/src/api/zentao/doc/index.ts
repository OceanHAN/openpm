import request from '@/config/axios'

// 文档库（zt_doclib）
//   type=product/project/execution 是「跟随对象走」的主库（main=true，删不掉）
//   type=custom 是团队空间下的自定义库（parent 指向空间）
export interface DocLibVO {
  id?: number
  type?: string
  typeName?: string
  parent?: number
  product?: number
  project?: number
  execution?: number
  name?: string
  acl?: string
  groups?: string
  users?: string
  main?: boolean
  desc?: string
  order?: number
  docCount?: number
  addedBy?: string
  addedDate?: number | string
}

// 文档 / 章节（zt_doc）
//   type=chapter 是章节（树的中间节点，没有正文）
export interface DocVO {
  id?: number
  lib?: number
  libName?: string
  product?: number
  project?: number
  execution?: number
  module?: number
  parent?: number
  parentTitle?: string
  path?: string
  grade?: number
  order?: number
  title?: string
  keywords?: string
  type?: string
  typeName?: string
  chapter?: boolean
  status?: string
  statusName?: string
  version?: number
  views?: number
  collects?: number
  acl?: string
  groups?: string
  users?: string
  addedBy?: string
  addedDate?: number | string
  editedBy?: string
  editedDate?: number | string
  content?: string
  rawContent?: string
  files?: string
  docCount?: number
  children?: DocVO[]
}

// 文档版本（zt_doccontent）。version=0 是草稿位
export interface DocContentVO {
  id?: number
  doc?: number
  version?: number
  title?: string
  digest?: string
  content?: string
  rawContent?: string
  files?: string
  type?: string
  addedBy?: string
  addedDate?: number | string
  editedBy?: string
  editedDate?: number | string
  current?: boolean
  draft?: boolean
}

// ==================== 文档库 ====================

export const getDocLibList = (params: { type?: string; objectID?: number; parent?: number }) => {
  return request.get<DocLibVO[]>({ url: '/zentao/doc/lib/list', params })
}

export const getDocLib = (id: number) => {
  return request.get<DocLibVO>({ url: '/zentao/doc/lib/get', params: { id } })
}

export const getDocLibTypeList = () => {
  return request.get<{ value: string; label: string }[]>({ url: '/zentao/doc/lib/type-list' })
}

export const createDocLib = (data: DocLibVO) => {
  return request.post<number>({ url: '/zentao/doc/lib/create', data })
}

export const updateDocLib = (data: DocLibVO) => {
  return request.put({ url: '/zentao/doc/lib/update', data })
}

export const deleteDocLib = (id: number) => {
  return request.delete({ url: '/zentao/doc/lib/delete', params: { id } })
}

// ==================== 文档 ====================

export const getDocPage = (params: any) => {
  return request.get({ url: '/zentao/doc/page', params })
}

export const getDoc = (id: number, version?: number) => {
  return request.get<DocVO>({ url: '/zentao/doc/get', params: { id, version } })
}

// 浏览：views +1（仅已发布文档计数）
export const viewDoc = (id: number) => {
  return request.get<DocVO>({ url: '/zentao/doc/view', params: { id } })
}

export const getDocListByLib = (lib: number) => {
  return request.get<DocVO[]>({ url: '/zentao/doc/list-by-lib', params: { lib } })
}

// 章节树：只含 type=chapter 的节点，节点上带直属文档数
export const getChapterTree = (lib: number) => {
  return request.get<DocVO[]>({ url: '/zentao/doc/chapter-tree', params: { lib } })
}

export const getDocContentList = (id: number) => {
  return request.get<DocContentVO[]>({ url: '/zentao/doc/content-list', params: { id } })
}

export const getDocTypeList = () => {
  return request.get<{ value: string; label: string; chapter: boolean }[]>({
    url: '/zentao/doc/type-list'
  })
}

export const getDocStatusList = () => {
  return request.get<{ value: string; label: string }[]>({ url: '/zentao/doc/status-list' })
}

export const createDoc = (data: DocVO) => {
  return request.post<number>({ url: '/zentao/doc/create', data })
}

export const updateDoc = (data: DocVO) => {
  return request.put({ url: '/zentao/doc/update', data })
}

export const deleteDoc = (id: number) => {
  return request.delete({ url: '/zentao/doc/delete', params: { id } })
}

export const moveDoc = (data: { id: number; lib: number; parent: number }) => {
  return request.put({ url: '/zentao/doc/move', data })
}

export const publishDoc = (id: number) => {
  return request.put({ url: '/zentao/doc/publish', params: { id } })
}
