import request from '@/config/axios'

// 分支/平台 VO。禅道里 zt_branch 是产品维度的分支表，
// 产品类型为 branch 时叫「分支」，为 platform 时叫「平台」（branchLabel 字段给出文案）。
export interface BranchVO {
  id?: number
  product?: number
  productName?: string
  branchLabel?: string
  name?: string
  defaultFlag?: number
  status?: string
  desc?: string
  createdDate?: string
  closedDate?: string
  order?: number
  /** 是否是虚拟主干（id=0）。主干不落库，不能关闭/删除 */
  mainBranch?: boolean
}

// 查询分支分页
export const getBranchPage = (params: PageParam) => {
  return request.get<PageResult<BranchVO[]>>({ url: '/zentao/branch/page', params })
}

// 查询分支详情（id 传 0 返回虚拟主干）
export const getBranch = (id: number) => {
  return request.get<BranchVO>({ url: '/zentao/branch/get', params: { id } })
}

// 新增分支
export const createBranch = (data: BranchVO) => {
  return request.post({ url: '/zentao/branch/create', data })
}

// 修改分支
export const updateBranch = (data: BranchVO) => {
  return request.put({ url: '/zentao/branch/update', data })
}

// 关闭分支
export const closeBranch = (id: number) => {
  return request.put({ url: '/zentao/branch/close', params: { id } })
}

// 激活分支
export const activateBranch = (id: number) => {
  return request.put({ url: '/zentao/branch/activate', params: { id } })
}

// 设置默认分支。branchId 传 0 表示默认分支为主干
export const setDefaultBranch = (product: number, branchId: number) => {
  return request.put({ url: '/zentao/branch/set-default', params: { product, branchId } })
}

// 删除分支
export const deleteBranch = (id: number) => {
  return request.delete({ url: '/zentao/branch/delete', params: { id } })
}

// 批量删除
export const deleteBranchList = (ids: number[]) => {
  return request.delete({ url: '/zentao/branch/delete-list', params: { ids: ids.join(',') } })
}

// 某产品下的全部分支（含虚拟主干）
export const getBranchListByProduct = (product: number, status?: string) => {
  return request.get<BranchVO[]>({ url: '/zentao/branch/list-by-product', params: { product, status } })
}

// 某产品下的分支数量（不含主干）
export const countBranchByProduct = (product: number) => {
  return request.get<number>({ url: '/zentao/branch/count-by-product', params: { product } })
}
