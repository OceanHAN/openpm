import request from '@/config/axios'

// 禅道需求 VO
export interface StoryVO {
  id?: number
  product?: number // 所属产品
  module?: number // 所属模块
  plan?: string // 所属计划
  branch?: number // 所属分支
  title?: string // 需求标题
  keywords?: string // 关键词
  spec?: string // 需求描述（来自版本快照）
  verify?: string // 验收标准（来自版本快照）
  type?: string // 需求类型
  category?: string // 需求分类
  pri?: number // 优先级 1~4
  estimate?: number // 预计工时
  status?: string // 状态
  stage?: string // 研发阶段
  version?: number // 版本号
  source?: string // 需求来源
  sourceNote?: string
  /** 父需求 */
  parent?: number
  parentTitle?: string
  /** 分解时父需求的版本（冻结） */
  parentVersion?: number
  /** 父需求已升版且仍激活 → 需要确认 */
  parentChanged?: boolean
  root?: number
  path?: string
  grade?: number
  /** 是否已分解（有子需求） */
  isParent?: boolean
  childCount?: number // 来源备注
  fromBug?: number
  openedBy?: string // 创建人
  openedDate?: number | string
  assignedTo?: string // 指派给
  assignedDate?: number | string
  closedBy?: string
  closedDate?: number | string
  closedReason?: string
  duplicateStory?: number
  activatedDate?: number | string
  lastEditedBy?: string
  lastEditedDate?: number | string
  reviewedBy?: string // 已评审人
  reviewedDate?: number | string
  createTime?: number
  updateTime?: number
}

// 需求版本快照 VO
export interface StorySpecVO {
  id: number
  story: number
  version: number
  title: string
  spec?: string
  verify?: string
  files?: string
  createTime?: number
  creator?: string
}

// 需求分层类型 VO（业务需求 epic / 用户需求 requirement / 研发需求 story）
export interface StoryTypeVO {
  type: string
  name: string
  level: number
  /** 允许的父需求类型，逗号分隔 */
  parentTypes?: string
  /** 分解时子需求的类型 */
  childType?: string
}

// 需求分层树节点 VO
export interface StoryTreeNodeVO {
  id: number
  parent?: number
  title: string
  type?: string
  typeName?: string
  grade?: number
  status?: string
  stage?: string
  pri?: number
  estimate?: number
  childCount?: number
  children?: StoryTreeNodeVO[]
}

// 需求评审情况 VO
export interface StoryReviewVO {
  story?: number
  version?: number
  reviewers?: Array<{
    reviewer: string
    result?: string
    reviewDate?: number | string
  }>
  finalResult?: string
  finished?: boolean
}

// 需求变更 VO
export interface StoryChangeVO {
  id: number
  title: string
  spec?: string
  verify?: string
  assignedTo?: string
  comment?: string
}

// 需求关闭 VO
export interface StoryCloseVO {
  id: number
  closedReason: string
  duplicateStory?: number
  comment?: string
}

// ==================== 需求 CRUD ====================

// 查询需求分页
export const getStoryPage = (params: PageParam) => {
  return request.get<PageResult<StoryVO[]>>({ url: '/zentao/story/page', params })
}

// 查询需求详情（version 传 0 或不传表示当前版本）
export const getStory = (id: number, version?: number) => {
  return request.get<StoryVO>({ url: '/zentao/story/get', params: { id, version } })
}

// 新增需求
export const createStory = (data: StoryVO) => {
  return request.post({ url: '/zentao/story/create', data })
}

// 修改需求（普通编辑，不产生新版本）
export const updateStory = (data: StoryVO) => {
  return request.put({ url: '/zentao/story/update', data })
}

// 变更需求（正式变更，版本号 +1 并留存历史）
export const changeStory = (data: StoryChangeVO) => {
  return request.put({ url: '/zentao/story/change', data })
}

// 关闭需求
export const closeStory = (data: StoryCloseVO) => {
  return request.put({ url: '/zentao/story/close', data })
}

// 激活需求
export const activateStory = (id: number) => {
  return request.put({ url: '/zentao/story/activate', params: { id } })
}

// 删除需求
export const deleteStory = (id: number) => {
  return request.delete({ url: '/zentao/story/delete', params: { id } })
}

// 批量删除需求
export const deleteStoryList = (ids: number[]) => {
  return request.delete({ url: '/zentao/story/delete-list', params: { ids: ids.join(',') } })
}

// ==================== 版本历史 ====================

// 查询需求版本历史
export const getStorySpecList = (storyId: number) => {
  return request.get<StorySpecVO[]>({ url: '/zentao/story/spec-list', params: { storyId } })
}

// ==================== 需求评审 ====================

// 提交需求评审
export const startReview = (data: { id: number; reviewers: string[]; comment?: string }) => {
  return request.put({ url: '/zentao/story/start-review', data })
}

// 评审表决
export const submitReview = (data: { id: number; result: string; comment?: string }) => {
  return request.put({ url: '/zentao/story/review', data })
}

// 查询需求评审情况
export const getStoryReviewList = (storyId: number, version?: number) => {
  return request.get<StoryReviewVO>({
    url: '/zentao/story/review-list',
    params: { storyId, version }
  })
}

// ==================== 父子需求（分解） ====================

// 父需求分解出来的子需求
export const getStoryChildList = (parentId: number) => {
  return request.get<StoryVO[]>({ url: '/zentao/story/child-list', params: { parentId } })
}

// 把已有需求挂到父需求下（会冻结父需求当前版本）
export const subdivideStory = (parentId: number, childIds: number[]) => {
  return request.post<number>({ url: '/zentao/story/subdivide', params: { parentId }, data: childIds })
}

// 把一条需求拆成若干子需求（只给标题，其余继承父需求）
export const batchCreateStoryChild = (parentId: number, titles: string[]) => {
  return request.post<number[]>({ url: '/zentao/story/batch-create-child', params: { parentId }, data: titles })
}

// ==================== 需求分层（业务需求 / 用户需求 / 研发需求） ====================

// 需求分层类型字典
export const getStoryTypeList = () => {
  return request.get<StoryTypeVO[]>({ url: '/zentao/story/type-list' })
}

// 某产品各需求分层类型的数量（需求池页签角标）
export const getStoryTypeSummary = (product: number) => {
  return request.get<Record<string, number>>({
    url: '/zentao/story/type-summary',
    params: { product }
  })
}

// 需求分层树（业务需求 → 用户需求 → 研发需求）
export const getStoryTypeTree = (product: number, rootId?: number) => {
  return request.get<StoryTreeNodeVO[]>({
    url: '/zentao/story/type-tree',
    params: { product, rootId }
  })
}
