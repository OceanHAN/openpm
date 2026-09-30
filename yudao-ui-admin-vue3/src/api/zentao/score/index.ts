import request from '@/config/axios'

// 积分（禅道 module/score）：规则驱动的计分器
//   规则表 = (module, method, times, hour, score) + 扩展加成（严重程度/优先级/密码强度/执行关闭）
//   计分是「被别的模块调」的，这里开了显式入口给联调用
export interface ScoreVO {
  id?: number
  account?: string
  module?: string
  moduleName?: string
  method?: string
  methodName?: string
  desc?: string
  before?: number
  score?: number
  after?: number
  time?: number | string
}

export interface ScoreRuleVO {
  module: string
  moduleName: string
  method: string
  methodName: string
  times: string
  hour: string
  score: number
  desc?: string
}

export interface ScoreTotalVO {
  account: string
  total: number
  yesterday: number
  tip?: string
  count: number
  enabled: boolean
}

export const getScorePage = (params: any) => {
  return request.get({ url: '/zentao/score/page', params })
}

/** 不传 account 就是「我的积分」 */
export const getScoreTotal = (account?: string) => {
  return request.get<ScoreTotalVO>({ url: '/zentao/score/total', params: { account } })
}

export const getScoreRules = () => {
  return request.get<ScoreRuleVO[]>({ url: '/zentao/score/rule' })
}

/** 计分：对应禅道 score::create(module, method, param, account, time) */
export const createScore = (data: {
  module: string
  method: string
  param?: number
  account?: string
  time?: string
}) => {
  return request.post<ScoreVO>({ url: '/zentao/score/create', data })
}
