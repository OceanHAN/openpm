import request from '@/config/axios'

/**
 * 操作时间线的一条。
 *
 * 注意 objectType/objectID/actor/action/actionName/date 是**后端必定返回**的字段，
 * 这里就不要写成可选（`?`）—— 否则调用方（story/ActionTimeline.vue）传给
 * formatDate / actionTagOf 时会因 `string | undefined` 报 TS 错。
 */
export interface ActionTimelineVO {
  id: number
  objectType: string
  objectID: number
  actor: string
  action: string
  actionName: string
  date: number | string
  comment?: string
  renderedDesc?: string
  histories?: Array<{
    field?: string
    oldValue?: string
    newValue?: string
    diff?: string
  }>
}

/** 回收站里的一条 */
export interface ActionTrashVO {
  actionId: number
  objectType?: string
  objectTypeName?: string
  objectID?: number
  objectName?: string
  deletedBy?: string
  deletedDate?: number | string
  comment?: string
  canUndelete?: boolean
  reason?: string
}

/** 对象的操作时间线 */
export const getActionList = (objectType: string, objectID: number) => {
  return request.get<ActionTimelineVO[]>({ url: '/zentao/action/list', params: { objectType, objectID } })
}

/** 动态（feed） */
export const getActionDynamic = (params: {
  actor?: string
  period?: string
  product?: number
  project?: number
  execution?: number
  limit?: number
}) => {
  return request.get<ActionTimelineVO[]>({ url: '/zentao/action/dynamic', params })
}

/** 发备注 */
export const addActionComment = (objectType: string, objectID: number, comment: string) => {
  return request.post<number>({
    url: '/zentao/action/comment',
    params: { objectType, objectID, comment }
  })
}

/** 改备注 */
export const updateActionComment = (id: number, comment: string) => {
  return request.put({ url: '/zentao/action/comment/update', params: { id, comment } })
}

/** 回收站 */
export const getActionTrashPage = (params: PageParam & { objectType?: string; actor?: string }) => {
  return request.get<PageResult<ActionTrashVO[]>>({ url: '/zentao/action/trash', params })
}

export const undeleteAction = (id: number) => {
  return request.post<Record<string, any>>({ url: '/zentao/action/undelete', params: { id } })
}

export const hideAction = (id: number) => {
  return request.post({ url: '/zentao/action/hide', params: { id } })
}

export const hideAllAction = () => {
  return request.post<number>({ url: '/zentao/action/hide-all' })
}
