import request from '@/config/axios'

// 保存查询 / 搜索（禅道 module/search）
//   ⚠️ conditions 是**结构化条件 JSON**，不是 SQL：禅道把条件序列化成 SQL 片段存库、列表页直接拼 WHERE，
//      Java 侧照搬会有注入风险，且 MyBatis-Plus 不接受半截 SQL。本实现存 JSON，由调用方翻译成查询 VO。
export interface SearchQueryVO {
  id?: number
  account?: string
  module: string
  title: string
  /** 表单定义快照（JSON），前端回填搜索表单用 */
  form?: string
  /** 结构化条件 JSON（对应表里的 sql 列） */
  conditions?: string
  /** 是否快捷方式：1 是（显示在列表页页签上） */
  shortcut?: number
  /** 是否公共查询：1 是（所有人可见） */
  common?: number
}

export const getSearchQueryPage = (params: any) => {
  return request.get({ url: '/zentao/search/query/page', params })
}

export const getSearchQuery = (id: number) => {
  return request.get<SearchQueryVO>({ url: '/zentao/search/query/get', params: { id } })
}

/** 某模块下的查询（我的 + 公共）——列表页打开时渲染「已保存的查询」下拉 */
export const listSearchQueryByModule = (module: string, account?: string) => {
  return request.get<SearchQueryVO[]>({ url: '/zentao/search/query/list', params: { module, account } })
}

/** 快捷方式（显示在列表页页签上的那几个） */
export const listSearchShortcuts = (module: string, account?: string) => {
  return request.get<SearchQueryVO[]>({ url: '/zentao/search/query/shortcut-list', params: { module, account } })
}

export const saveSearchQuery = (data: SearchQueryVO) => {
  return request.post<number>({ url: '/zentao/search/query/save', data })
}

export const setSearchQueryShortcut = (id: number, shortcut: number) => {
  return request.put({ url: '/zentao/search/query/shortcut', params: { id, shortcut } })
}

export const deleteSearchQuery = (id: number) => {
  return request.delete({ url: '/zentao/search/query/delete', params: { id } })
}

/** 取中文的拼音首字母（禅道 convert2Pinyin 的等价物，逐字查 zt_searchdict 码表） */
export const getPinyinInitials = (text: string) => {
  return request.get<string>({ url: '/zentao/search/dict/pinyin', params: { text } })
}
