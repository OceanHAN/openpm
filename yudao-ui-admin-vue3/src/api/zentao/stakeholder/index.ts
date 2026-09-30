import request from '@/config/axios'

// 干系人（zt_stakeholder）
//
// 注意它和团队成员（zt_team）不是一回事：
//   团队成员 = 要干活的人（有角色、有可用工时）
//   干系人   = 需要知情/被影响的人（甲方、领导、外部顾问……）
export interface StakeholderVO {
  id?: number
  /** program / project */
  objectType?: string
  objectID?: number
  /** 账号（from=outside 时是外部人员名字） */
  user?: string
  realname?: string
  /** inside / outside（由 from 推导） */
  type?: string
  typeName?: string
  /** 是否关键干系人 */
  key?: number
  /** team / company / outside */
  from?: string
  fromName?: string
  createdBy?: string
  createdDate?: number | string
}

export const STAKEHOLDER_FROM_OPTIONS = [
  { value: 'team', label: '团队成员' },
  { value: 'company', label: '公司同事' },
  { value: 'outside', label: '外部人员' }
]

export const STAKEHOLDER_TYPE_LABELS: Record<string, string> = {
  inside: '内部',
  outside: '外部'
}

export const getStakeholderList = (objectType: string, objectID: number) => {
  return request.get<StakeholderVO[]>({ url: '/zentao/stakeholder/list', params: { objectType, objectID } })
}

export const createStakeholder = (data: StakeholderVO) => {
  return request.post({ url: '/zentao/stakeholder/create', data })
}

export const batchCreateStakeholder = (objectType: string, objectID: number, from: string, users: string[]) => {
  return request.post({
    url: '/zentao/stakeholder/batch-create',
    params: { objectType, objectID, from },
    data: users
  })
}

export const updateStakeholder = (data: StakeholderVO) => {
  return request.put({ url: '/zentao/stakeholder/update', data })
}

export const deleteStakeholder = (id: number) => {
  return request.delete({ url: '/zentao/stakeholder/delete', params: { id } })
}

export const deleteStakeholderByUser = (objectType: string, objectID: number, user: string) => {
  return request.delete({ url: '/zentao/stakeholder/delete-by-user', params: { objectType, objectID, user } })
}

export const getObjectIdsByStakeholder = (objectType: string, user: string) => {
  return request.get({ url: '/zentao/stakeholder/list-by-user', params: { objectType, user } })
}
