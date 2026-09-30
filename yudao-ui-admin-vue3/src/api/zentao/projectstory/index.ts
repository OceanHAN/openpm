import request from '@/config/axios'
import type { StoryVO } from '@/api/zentao/story'

// 项目需求范围 VO
//   project 列存的是「项目/执行」编号（禅道里两者共用 zt_project）
//   linkVersion 是关联时的需求版本，需求后续变更后会 versionChanged=true
export interface ProjectStoryVO {
  id?: number
  project?: number
  story?: number
  title?: string
  product?: number
  productName?: string
  branch?: number
  status?: string
  stage?: string
  pri?: number
  estimate?: number
  assignedTo?: string
  linkVersion?: number
  currentVersion?: number
  versionChanged?: boolean
  order?: number
  openedDate?: number | string
  relatedProjects?: number[]
}

// 项目关联产品 VO
export interface ProjectProductVO {
  id?: number
  project?: number
  product?: number
  productName?: string
  productType?: string
  branch?: number
  branchName?: string
  plan?: string
  planNames?: string[]
  storyCount?: number
}

// 项目关联产品
export const linkProduct = (data: { project: number; product: number; branch?: number; plans?: number[] }) => {
  return request.post({ url: '/zentao/projectstory/link-product', data })
}

// 解除项目与产品的关联
export const unlinkProduct = (project: number, product: number, branch?: number) => {
  return request.delete({ url: '/zentao/projectstory/unlink-product', params: { project, product, branch } })
}

// 项目关联的产品列表
export const getProductList = (project: number) => {
  return request.get<ProjectProductVO[]>({ url: '/zentao/projectstory/product-list', params: { project } })
}

// 关联需求
export const linkStory = (project: number, storyIds: number[]) => {
  return request.put<number[]>({ url: '/zentao/projectstory/link-story', data: { project, storyIds } })
}

// 移除需求
export const unlinkStory = (project: number, story: number) => {
  return request.delete({ url: '/zentao/projectstory/unlink-story', params: { project, story } })
}

// 项目/执行下的需求
export const getStoryList = (project: number) => {
  return request.get<ProjectStoryVO[]>({ url: '/zentao/projectstory/story-list', params: { project } })
}

// 未纳入范围的需求候选
export const getUnlinkedStoryList = (project: number) => {
  return request.get<StoryVO[]>({ url: '/zentao/projectstory/unlinked-story-list', params: { project } })
}

// 需求被哪些项目/执行关联
export const getStoryProjects = (story: number) => {
  return request.get<number[]>({ url: '/zentao/projectstory/story-projects', params: { story } })
}

// 项目/执行下的需求数量
export const countStories = (project: number) => {
  return request.get<number>({ url: '/zentao/projectstory/count', params: { project } })
}
