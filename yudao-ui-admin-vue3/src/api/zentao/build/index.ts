import request from '@/config/axios'
import type { StoryVO } from '@/api/zentao/story'
import type { BugVO } from '@/api/zentao/bug'

// 构建 VO。禅道三个要点：
//   stories / bugs 是逗号列表（本次完成的需求 / 本次解决的 Bug）
//   集成构建：builds 列子构建、execution=0、branch 取子构建并集，读取时 stories/bugs 会合并子构建的
//   缺陷的 resolvedBuild 存的就是构建编号
export interface BuildVO {
  id?: number
  project?: number
  product?: number
  productName?: string
  branch?: string
  branchName?: string
  /** 表单提交用：分支编号数组 */
  branches?: number[]
  execution?: number
  executionName?: string
  builds?: string
  buildNames?: string[]
  integrated?: boolean
  /** 表单提交用：勾选的子构建编号 */
  buildIds?: number[]
  name?: string
  date?: string
  builder?: string
  scmPath?: string
  filePath?: string
  desc?: string
  stories?: string
  bugs?: string
  storyCount?: number
  bugCount?: number
  /** 是否已被集成构建/发布引用 */
  child?: boolean
  createdBy?: string
  createdDate?: number | string
}

// 查询构建分页
export const getBuildPage = (params: PageParam) => {
  return request.get<PageResult<BuildVO[]>>({ url: '/zentao/build/page', params })
}

// 查询构建详情
export const getBuild = (id: number) => {
  return request.get<BuildVO>({ url: '/zentao/build/get', params: { id } })
}

// 某产品下的构建（缺陷「解决版本」下拉用）
export const getBuildListByProduct = (product: number, branch?: number) => {
  return request.get<BuildVO[]>({ url: '/zentao/build/list-by-product', params: { product, branch } })
}

// 某执行下的构建
export const getBuildListByExecution = (execution: number) => {
  return request.get<BuildVO[]>({ url: '/zentao/build/list-by-execution', params: { execution } })
}

// 新增构建
export const createBuild = (data: BuildVO) => {
  return request.post({ url: '/zentao/build/create', data })
}

// 修改构建
export const updateBuild = (data: BuildVO) => {
  return request.put({ url: '/zentao/build/update', data })
}

// 删除构建
export const deleteBuild = (id: number) => {
  return request.delete({ url: '/zentao/build/delete', params: { id } })
}

// 构建下的需求
export const getBuildStoryList = (build: number) => {
  return request.get<StoryVO[]>({ url: '/zentao/build/story-list', params: { build } })
}

// 还没关联到该构建的需求
export const getUnlinkedStoryList = (build: number) => {
  return request.get<StoryVO[]>({ url: '/zentao/build/unlinked-story-list', params: { build } })
}

// 构建下的 Bug
export const getBuildBugList = (build: number) => {
  return request.get<BugVO[]>({ url: '/zentao/build/bug-list', params: { build } })
}

// 还没关联到该构建的 Bug
export const getUnlinkedBugList = (build: number) => {
  return request.get<BugVO[]>({ url: '/zentao/build/unlinked-bug-list', params: { build } })
}

// 关联需求
export const linkStory = (build: number, ids: number[]) => {
  return request.put({ url: '/zentao/build/link-story', data: { build, ids } })
}

// 移除需求
export const unlinkStory = (build: number, story: number) => {
  return request.delete({ url: '/zentao/build/unlink-story', params: { build, story } })
}

// 关联 Bug（未解决的会被自动置为已解决）
export const linkBug = (build: number, ids: number[], resolvedBy?: Record<number, string>) => {
  return request.put({ url: '/zentao/build/link-bug', data: { build, ids, resolvedBy } })
}

// 移除 Bug（不会回退解决状态）
export const unlinkBug = (build: number, bug: number) => {
  return request.delete({ url: '/zentao/build/unlink-bug', params: { build, bug } })
}

// 产品的分支/平台文案
export const getBranchLabel = (product: number) => {
  return request.get<{ enabled: boolean; label: string }>({ url: '/zentao/build/branch-label', params: { product } })
}
