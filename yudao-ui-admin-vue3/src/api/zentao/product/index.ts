import request from '@/config/axios'

// 禅道产品 VO
export interface ProductVO {
  id?: number
  program?: number
  line?: number
  name?: string
  code?: string
  type?: string
  status?: string
  desc?: string
  PO?: string
  QD?: string
  RD?: string
  acl?: string
  createdBy?: string
  createdDate?: number | string
  closedDate?: number | string
  order?: number
  createTime?: number
  stats?: ProductStats
}

// 产品关联统计（后端实时统计，不是冗余字段）
export interface ProductStats {
  totalStories?: number
  activeStories?: number
  closedStories?: number
  totalBugs?: number
  unresolvedBugs?: number
  closedBugs?: number
}

// 产品精简 VO
export interface ProductSimpleVO {
  id: number
  name: string
  code?: string
  status?: string
  /** 类型：normal 普通 / branch 多分支 / platform 多平台。分支页面据此决定文案与能否建分支 */
  type?: string
}

// 查询产品分页
export const getProductPage = (params: PageParam) => {
  return request.get<PageResult<ProductVO[]>>({ url: '/zentao/product/page', params })
}

// 查询产品详情（含实时统计）
export const getProduct = (id: number) => {
  return request.get<ProductVO>({ url: '/zentao/product/get', params: { id } })
}

// 新增产品
export const createProduct = (data: ProductVO) => {
  return request.post({ url: '/zentao/product/create', data })
}

// 修改产品
export const updateProduct = (data: ProductVO) => {
  return request.put({ url: '/zentao/product/update', data })
}

// 关闭产品
export const closeProduct = (id: number) => {
  return request.put({ url: '/zentao/product/close', params: { id } })
}

// 激活产品
export const activateProduct = (id: number) => {
  return request.put({ url: '/zentao/product/activate', params: { id } })
}

// 删除产品
export const deleteProduct = (id: number) => {
  return request.delete({ url: '/zentao/product/delete', params: { id } })
}

// 批量删除产品
export const deleteProductList = (ids: number[]) => {
  return request.delete({ url: '/zentao/product/delete-list', params: { ids: ids.join(',') } })
}

// 查询产品精简列表（用于需求的产品下拉）
export const getProductSimpleList = () => {
  return request.get<ProductSimpleVO[]>({ url: '/zentao/product/simple-list' })
}
