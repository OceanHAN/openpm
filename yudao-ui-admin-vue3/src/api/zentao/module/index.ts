import request from '@/config/axios'

// 模块树 VO。禅道 zt_module 是一张通用树表，(root, type, branch) 定位一棵树：
//   type=story + root=产品id  → 需求模块
//   type=bug   + root=产品id  → 缺陷模块
//   type=task  + root=执行id  → 任务模块
//   path 用逗号格式：,5,6,
export interface ModuleVO {
  id?: number
  root?: number
  type?: string
  typeName?: string
  branch?: number
  parent?: number
  name?: string
  path?: string
  grade?: number
  order?: number
  shortName?: string
  owner?: string
  childCount?: number
  children?: ModuleVO[]
}

export interface ModuleTypeVO {
  type: string
  name: string
}

// 模块树类型列表
export const getModuleTypeList = () => {
  return request.get<ModuleTypeVO[]>({ url: '/zentao/module/type-list' })
}

// 模块列表（平铺）
export const getModuleList = (params: { root: number; type: string; branch?: number }) => {
  return request.get<ModuleVO[]>({ url: '/zentao/module/list', params })
}

// 模块树（嵌套）
export const getModuleTree = (params: { root: number; type: string; branch?: number }) => {
  return request.get<ModuleVO[]>({ url: '/zentao/module/tree', params })
}

// 模块详情
export const getModule = (id: number) => {
  return request.get<ModuleVO>({ url: '/zentao/module/get', params: { id } })
}

// 新增模块
export const createModule = (data: ModuleVO) => {
  return request.post({ url: '/zentao/module/create', data })
}

// 修改模块
export const updateModule = (data: ModuleVO) => {
  return request.put({ url: '/zentao/module/update', data })
}

// 批量更新排序
export const updateModuleOrder = (items: Array<{ id: number; order: number }>) => {
  return request.put({ url: '/zentao/module/update-order', data: { items } })
}

// 删除模块（连带子模块；挂在被删模块上的需求/任务/缺陷会改挂到上级）
export const deleteModule = (id: number) => {
  return request.delete({ url: '/zentao/module/delete', params: { id } })
}
