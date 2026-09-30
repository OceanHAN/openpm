import request from '@/config/axios'

/** 数据视图（数据集） */
export interface DataViewVO {
  id?: number
  name?: string
  code?: string
  mode?: string
  sql?: string
  fields?: Array<Record<string, any>>
  langs?: Record<string, string>
  objects?: Array<Record<string, any>>
  chartCount?: number
  createdBy?: string
  createdDate?: number | string
}

export interface DataViewPreviewVO {
  columns: string[]
  rows: Array<Record<string, any>>
  total: number
  executedSql: string
}

/** 图表 */
export interface ChartVO {
  id?: number
  name?: string
  code?: string
  type?: string
  typeName?: string
  echartsType?: string
  viewCode?: string
  sql?: string
  desc?: string
  stage?: string
  version?: string
  settings?: Record<string, any>
  filters?: Array<Record<string, any>>
  fields?: Array<Record<string, any>>
  createdBy?: string
  createdDate?: number | string
}

export interface ChartDataVO {
  chart: ChartVO
  dimensionField: string
  metricField: string
  agg: string
  rows: Array<{ name: string; value: number | string }>
  executedSql: string
}

export const getBiDict = () => {
  return request.get<Record<string, Array<{ value: string; label: string }>>>({ url: '/zentao/bi/dict' })
}

// ==================== 数据视图 ====================

export const getDataViewPage = (params: PageParam) => {
  return request.get<PageResult<DataViewVO[]>>({ url: '/zentao/bi/dataview/page', params })
}

export const getDataViewList = () => {
  return request.get<DataViewVO[]>({ url: '/zentao/bi/dataview/list' })
}

export const getDataView = (id: number) => {
  return request.get<DataViewVO>({ url: '/zentao/bi/dataview/get', params: { id } })
}

export const createDataView = (data: DataViewVO) => {
  return request.post<number>({ url: '/zentao/bi/dataview/create', data })
}

export const updateDataView = (data: DataViewVO) => {
  return request.put({ url: '/zentao/bi/dataview/update', data })
}

export const deleteDataView = (id: number) => {
  return request.delete({ url: '/zentao/bi/dataview/delete', params: { id } })
}

export const previewDataView = (id: number, limit = 20) => {
  return request.get<DataViewPreviewVO>({ url: '/zentao/bi/dataview/preview', params: { id, limit } })
}

/**
 * 试跑一段 SQL（JSON body：长 SQL 用表单参数会被项目 axios 的默认 JSON header 坑到）
 */
export const previewSql = (sql: string, limit = 20) => {
  return request.post<DataViewPreviewVO>({
    url: '/zentao/bi/dataview/preview-sql',
    data: { sql, limit }
  })
}

// ==================== 图表 ====================

export const getChartPage = (params: PageParam) => {
  return request.get<PageResult<ChartVO[]>>({ url: '/zentao/bi/chart/page', params })
}

export const getChartList = () => {
  return request.get<ChartVO[]>({ url: '/zentao/bi/chart/list' })
}

export const getChart = (id: number) => {
  return request.get<ChartVO>({ url: '/zentao/bi/chart/get', params: { id } })
}

export const createChart = (data: ChartVO) => {
  return request.post<number>({ url: '/zentao/bi/chart/create', data })
}

export const updateChart = (data: ChartVO) => {
  return request.put({ url: '/zentao/bi/chart/update', data })
}

export const deleteChart = (id: number) => {
  return request.delete({ url: '/zentao/bi/chart/delete', params: { id } })
}

export const getChartData = (id: number) => {
  return request.get<ChartDataVO>({ url: '/zentao/bi/chart/data', params: { id } })
}
