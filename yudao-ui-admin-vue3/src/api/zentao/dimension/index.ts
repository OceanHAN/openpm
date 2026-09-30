import request from '@/config/axios'

// 维度（禅道 module/dimension）：BI 的「1.5 级导航」
//
// 开源版**只有只读维度 + 切换语义**，没有维度 CRUD / 管理界面（全库只有 upgrade/model.php:6971
// 直接 INSERT zt_dimension），所以本文件只有查询与切换两类方法，切换 = 写「末次维度」记录。
export interface DimensionVO {
  id?: number
  /** 维度名称（下拉显示文本） */
  name: string
  /** 维度代号：macro / efficiency / quality */
  code: string
  desc?: string
  /** 访问控制：open 公开 / private 仅创建者与白名单 */
  acl?: string
  /** 白名单账号逗号串（acl=private 时生效） */
  whitelist?: string
  createdBy?: string
  /** 时间字段：yudao 序列化成数字时间戳，页面里一律用 formatDate */
  createdDate?: number | string
  editedBy?: string
  editedDate?: number | string
}

/** 1.5 级导航下拉项：id/text/keys 是禅道 ajaxGetDropMenu 的原样结构 */
export interface DimensionItemVO {
  id: number
  text: string
  /** 拼音首字母（禅道 convert2Pinyin；码表缺字时退化成原文） */
  keys: string
}

/** 禅道 ajaxGetDropMenu 的 JSON：data/searchHint/link/labelMap/expandName/itemType 原样 */
export interface DimensionDropMenuVO {
  data: DimensionItemVO[]
  searchHint: string
  link: Record<string, string>
  labelMap: Record<string, string>
  expandName: string
  itemType: string
  /** 以下为本实现附加，便于前端拼链接 */
  dimensionID?: number
  module?: string
  method?: string
  params?: string
}

/** 当前维度（禅道 getDimension）：多返回一个 source，说明命中了四级兜底链的哪一级 */
export interface DimensionCurrentVO {
  dimensionID: number
  /** 标签页（禅道 session 按 app->tab 分桶） */
  tab: string
  /** explicit 入参 / config 配置 / last 末次访问 / fallback 可见性兜底 / first 第一条 / none */
  source: string
  sourceDesc: string
  dimension?: DimensionVO
  viewableIds: number[]
}

/** 可见性诊断的一行 */
export interface DimensionVisibilityItemVO {
  id: number
  name: string
  acl: string
  createdBy: string
  whitelist?: string
  visible: boolean
  /** 命中的判据：admin/open/creator/whitelist/none */
  reason: string
}

/** 可见性口径自检（禅道 biModel::getViewableObject('dimension')） */
export interface DimensionVisibilityVO {
  account: string
  superAdmin: boolean
  rule: string
  dimensions: DimensionVisibilityItemVO[]
  viewableIds: number[]
  note: string
}

/** 可见维度列表（禅道 dimensionModel::getList()，已过可见性过滤） */
export const getDimensionList = () => {
  return request.get<DimensionVO[]>({ url: '/zentao/dimension/list' })
}

/** 按 id 取维度（比禅道多一层可见性校验：不可见当不存在） */
export const getDimension = (id: number) => {
  return request.get<DimensionVO>({ url: '/zentao/dimension/get', params: { id } })
}

/** 当前维度（末次维度四级兜底链）；传 dimensionID 就是「切换到该维度」 */
export const getCurrentDimension = (dimensionID?: number, tab?: string) => {
  return request.get<DimensionCurrentVO>({
    url: '/zentao/dimension/get-dimension',
    params: { dimensionID, tab }
  })
}

/** 1.5 级导航下拉（带有 pivot-design → browse、tree-browsegroup → groupID/type 两处参数例外） */
export const getDimensionDropMenu = (params: {
  dimensionID?: number
  module: string
  method: string
  viewType?: string
  tab?: string
}) => {
  return request.get<DimensionDropMenuVO>({ url: '/zentao/dimension/drop-menu', params })
}

/** 可见性口径自检（只读诊断；不传 account 就是当前账号） */
export const getDimensionVisibility = (account?: string) => {
  return request.get<DimensionVisibilityVO>({ url: '/zentao/dimension/visibility', params: { account } })
}
