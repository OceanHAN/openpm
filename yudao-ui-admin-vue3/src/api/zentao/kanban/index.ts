import request from '@/config/axios'

// 看板空间
export interface KanbanSpaceVO {
  id?: number
  name?: string
  type?: string
  typeName?: string
  owner?: string
  team?: string
  desc?: string
  acl?: string
  whitelist?: string
  status?: string
  order?: number
  kanbanCount?: number
  createdBy?: string
  createdDate?: number | string
}

// 看板
export interface KanbanVO {
  id?: number
  space?: number
  spaceName?: string
  name?: string
  owner?: string
  team?: string
  desc?: string
  acl?: string
  whitelist?: string
  archived?: number
  performable?: number
  status?: string
  order?: number
  displayCards?: number
  showWIP?: number
  fluidBoard?: number
  colWidth?: number
  minColWidth?: number
  maxColWidth?: number
  object?: string
  alignment?: string
  regionCount?: number
  cardCount?: number
}

export interface KanbanRegionVO {
  id?: number
  kanban?: number
  space?: number
  name?: string
  order?: number
  groupId?: number
  laneCount?: number
  columnCount?: number
}

export interface KanbanLaneVO {
  id?: number
  region?: number
  groupId?: number
  name?: string
  type?: string
  typeName?: string
  color?: string
  order?: number
}

export interface KanbanColumnVO {
  id?: number
  groupId?: number
  region?: number
  parent?: number
  name?: string
  color?: string
  limit?: number
  order?: number
  archived?: boolean
}

export interface KanbanCardVO {
  id?: number
  kanban?: number
  region?: number
  groupId?: number
  name?: string
  status?: string
  statusName?: string
  pri?: number
  assignedTo?: string
  desc?: string
  begin?: string
  end?: string
  estimate?: number
  progress?: number
  color?: string
  archived?: boolean
  fromID?: number
  fromType?: string
  createdBy?: string
}

// 看板视图数据（区域 → 泳道 × 列 + 卡片）
export interface KanbanCellVO {
  columnId: number
  columnName: string
  limit: number
  cardCount: number
  overWip: boolean
  cards: KanbanCardVO[]
}

export interface KanbanDataVO {
  kanban: KanbanVO
  regions: Array<{
    id: number
    name: string
    groupId: number
    columns: KanbanColumnVO[]
    lanes: Array<{
      id: number
      name: string
      type: string
      color: string
      cells: KanbanCellVO[]
    }>
  }>
}

// ==================== 空间 ====================

export const getKanbanSpaceList = (type?: string) => {
  return request.get<KanbanSpaceVO[]>({ url: '/zentao/kanban/space/list', params: { type } })
}

export const getKanbanSpacePage = (params: PageParam) => {
  return request.get<PageResult<KanbanSpaceVO[]>>({ url: '/zentao/kanban/space/page', params })
}

export const createKanbanSpace = (data: KanbanSpaceVO) => {
  return request.post<number>({ url: '/zentao/kanban/space/create', data })
}

export const updateKanbanSpace = (data: KanbanSpaceVO) => {
  return request.put({ url: '/zentao/kanban/space/update', data })
}

export const deleteKanbanSpace = (id: number) => {
  return request.delete({ url: '/zentao/kanban/space/delete', params: { id } })
}

// ==================== 看板 ====================

export const getKanbanList = (space: number) => {
  return request.get<KanbanVO[]>({ url: '/zentao/kanban/list', params: { space } })
}

export const getKanban = (id: number) => {
  return request.get<KanbanVO>({ url: '/zentao/kanban/get', params: { id } })
}

export const createKanban = (data: KanbanVO) => {
  return request.post<number>({ url: '/zentao/kanban/create', data })
}

export const updateKanban = (data: KanbanVO) => {
  return request.put({ url: '/zentao/kanban/update', data })
}

export const deleteKanban = (id: number) => {
  return request.delete({ url: '/zentao/kanban/delete', params: { id } })
}

/** 看板视图数据 */
export const getKanbanData = (kanbanId: number) => {
  return request.get<KanbanDataVO>({ url: '/zentao/kanban/data', params: { kanbanId } })
}

// ==================== 区域 / 泳道 / 列 ====================

export const createKanbanRegion = (data: KanbanRegionVO) => {
  return request.post<number>({ url: '/zentao/kanban/region/create', data })
}

export const deleteKanbanRegion = (id: number) => {
  return request.delete({ url: '/zentao/kanban/region/delete', params: { id } })
}

export const createKanbanLane = (data: KanbanLaneVO) => {
  return request.post<number>({ url: '/zentao/kanban/lane/create', data })
}

export const deleteKanbanLane = (id: number) => {
  return request.delete({ url: '/zentao/kanban/lane/delete', params: { id } })
}

export const createKanbanColumn = (data: KanbanColumnVO) => {
  return request.post<number>({ url: '/zentao/kanban/column/create', data })
}

export const updateKanbanColumn = (data: KanbanColumnVO) => {
  return request.put({ url: '/zentao/kanban/column/update', data })
}

export const deleteKanbanColumn = (id: number) => {
  return request.delete({ url: '/zentao/kanban/column/delete', params: { id } })
}

// ==================== 卡片 ====================

export const createKanbanCard = (data: KanbanCardVO & { lane?: number; column?: number }) => {
  return request.post<number>({ url: '/zentao/kanban/card/create', data })
}

export const updateKanbanCard = (data: KanbanCardVO) => {
  return request.put({ url: '/zentao/kanban/card/update', data })
}

export const getKanbanCard = (id: number) => {
  return request.get<KanbanCardVO>({ url: '/zentao/kanban/card/get', params: { id } })
}

export const deleteKanbanCard = (id: number) => {
  return request.delete({ url: '/zentao/kanban/card/delete', params: { id } })
}

export const moveKanbanCard = (params: {
  cardId: number
  fromColumnId: number
  toColumnId: number
  fromLaneId: number
  toLaneId: number
}) => {
  return request.post({ url: '/zentao/kanban/card/move', params })
}

export const finishKanbanCard = (id: number) => {
  return request.put({ url: '/zentao/kanban/card/finish', params: { id } })
}

export const activateKanbanCard = (id: number, progress: number) => {
  return request.put({ url: '/zentao/kanban/card/activate', params: { id, progress } })
}

export const archiveKanbanCard = (id: number) => {
  return request.put({ url: '/zentao/kanban/card/archive', params: { id } })
}

export const restoreKanbanCard = (id: number) => {
  return request.put({ url: '/zentao/kanban/card/restore', params: { id } })
}
