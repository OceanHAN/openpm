import request from '@/config/axios'
import type { StoryVO } from '@/api/zentao/story'
import type { BugVO } from '@/api/zentao/bug'

// 发布 VO。禅道要点：
//   发布名全局唯一；创建时自动生成「影子构建」
//   stories=完成的需求 / bugs=解决的 Bug / leftBugs=遗留的 Bug（三份清单）
//   build/branch/project 存的是前后带逗号的逗号列表
export interface ReleaseVO {
  id?: number
  product?: number
  productName?: string
  branch?: string
  branchName?: string
  /** 表单提交用 */
  branches?: number[]
  project?: string
  projectNames?: string[]
  build?: string
  buildNames?: string[]
  /** 表单提交用 */
  builds?: number[]
  syncFromBuilds?: boolean
  shadow?: number
  name?: string
  marker?: number
  date?: string
  releasedDate?: string
  status?: string
  statusName?: string
  stories?: string
  bugs?: string
  leftBugs?: string
  releases?: string
  storyCount?: number
  bugCount?: number
  leftBugCount?: number
  included?: boolean
  desc?: string
  createdBy?: string
  createdDate?: number | string
}

// 查询发布分页
export const getReleasePage = (params: PageParam) => {
  return request.get<PageResult<ReleaseVO[]>>({ url: '/zentao/release/page', params })
}

// 发布详情
export const getRelease = (id: number) => {
  return request.get<ReleaseVO>({ url: '/zentao/release/get', params: { id } })
}

// 产品下的发布
export const getReleaseListByProduct = (product: number, branch?: number) => {
  return request.get<ReleaseVO[]>({ url: '/zentao/release/list-by-product', params: { product, branch } })
}

// 新增发布
export const createRelease = (data: ReleaseVO) => {
  return request.post({ url: '/zentao/release/create', data })
}

// 修改发布
export const updateRelease = (data: ReleaseVO) => {
  return request.put({ url: '/zentao/release/update', data })
}

// 删除发布
export const deleteRelease = (id: number) => {
  return request.delete({ url: '/zentao/release/delete', params: { id } })
}

// 发布（状态置为已发布）
export const publishRelease = (id: number, releasedDate?: string) => {
  return request.put({ url: '/zentao/release/publish', params: { id, releasedDate } })
}

// 修改状态
export const changeReleaseStatus = (id: number, status: string, releasedDate?: string) => {
  return request.put({ url: '/zentao/release/change-status', params: { id, status, releasedDate } })
}

// 发布下的需求
export const getReleaseStoryList = (release: number) => {
  return request.get<StoryVO[]>({ url: '/zentao/release/story-list', params: { release } })
}

// 未关联的需求候选
export const getUnlinkedStoryList = (release: number) => {
  return request.get<StoryVO[]>({ url: '/zentao/release/unlinked-story-list', params: { release } })
}

// 发布下的 Bug（type: bug / leftBug）
export const getReleaseBugList = (release: number, type: string) => {
  return request.get<BugVO[]>({ url: '/zentao/release/bug-list', params: { release, type } })
}

// 未关联的 Bug 候选
export const getUnlinkedBugList = (release: number, type: string) => {
  return request.get<BugVO[]>({ url: '/zentao/release/unlinked-bug-list', params: { release, type } })
}

// 关联需求
export const linkStory = (release: number, ids: number[]) => {
  return request.put({ url: '/zentao/release/link-story', data: { release, ids } })
}

// 移除需求
export const unlinkStory = (release: number, story: number) => {
  return request.delete({ url: '/zentao/release/unlink-story', params: { release, story } })
}

// 关联 Bug
export const linkBug = (release: number, type: string, ids: number[]) => {
  return request.put({ url: '/zentao/release/link-bug', data: { release, type, ids } })
}

// 移除 Bug
export const unlinkBug = (release: number, type: string, bug: number) => {
  return request.delete({ url: '/zentao/release/unlink-bug', params: { release, type, bug } })
}
