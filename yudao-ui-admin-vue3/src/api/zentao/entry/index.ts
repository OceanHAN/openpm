import request from '@/config/axios'

// 应用接入（entry）：第三方系统带 code + token 免登录调禅道。
// 两种签名：time 模式 md5(code+key+time)（防重放）、query 模式 md5(md5(query)+key)。
export interface EntryVO {
  id?: number
  name: string
  account?: string
  code: string
  key?: string
  freePasswd?: number
  ip?: string
  desc?: string
  calledTime?: number
  createdBy?: string
  createdDate?: number
  editedBy?: string
  editedDate?: number
}

export interface EntryLogVO {
  id: number
  objectType: string
  objectID: number
  date: number
  url: string
  result?: string
}

export interface EntrySignVO {
  code: string
  time?: string
  token: string
  mode: string
}

export interface EntryVerifyVO {
  entryId: number
  name: string
  code: string
  account: string
  freePasswd: number
  tokenMode: string
  calledTime: number
  userId: number
  userNickname: string
  logId: number
  message: string
}

export const getEntryPage = (params: any) => {
  return request.get({ url: '/zentao/entry/page', params })
}

export const getEntrySimpleList = () => {
  return request.get<EntryVO[]>({ url: '/zentao/entry/simple-list' })
}

export const getEntry = (id: number) => {
  return request.get<EntryVO>({ url: '/zentao/entry/get', params: { id } })
}

export const createEntry = (data: EntryVO) => {
  return request.post({ url: '/zentao/entry/create', data })
}

export const updateEntry = (data: EntryVO) => {
  return request.put({ url: '/zentao/entry/update', data })
}

export const deleteEntry = (id: number) => {
  return request.delete({ url: '/zentao/entry/delete', params: { id } })
}

/** 32 位随机密钥，对应禅道界面的「重新生成密钥」 */
export const getRandomKey = () => {
  return request.get<string>({ url: '/zentao/entry/random-key' })
}

export const getEntryLogPage = (params: any) => {
  return request.get({ url: '/zentao/entry/log-page', params })
}

/** 计算签名（管理端助手，便于对接方自测） */
export const signEntry = (params: { code: string; time?: string; query?: string }) => {
  return request.get<EntrySignVO>({ url: '/zentao/entry/sign', params })
}

/** 应用接入校验：这个接口是免登录的（@PermitAll），第三方系统凭 code + token 调用 */
export const verifyEntry = (data: {
  code: string
  token: string
  time?: string
  query?: string
  account?: string
  module?: string
  method?: string
  url?: string
  clientIp?: string
}) => {
  return request.post<EntryVerifyVO>({ url: '/zentao/entry/verify', data })
}
