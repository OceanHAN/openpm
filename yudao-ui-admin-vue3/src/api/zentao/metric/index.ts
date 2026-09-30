import request from '@/config/axios'

/** 度量项定义 */
export interface MetricVO {
  id?: number
  purpose?: string
  purposeName?: string
  scope?: string
  scopeName?: string
  object?: string
  objectName?: string
  stage?: string
  type?: string
  name?: string
  alias?: string
  code?: string
  unit?: string
  unitName?: string
  dateType?: string
  dateTypeName?: string
  desc?: string
  definition?: string
  builtin?: boolean
  order?: number
  lastCalcRows?: number
  lastCalcTime?: number | string
  dataCount?: number
  implemented?: boolean
}

/** 度量数据行 */
export interface MetricDataRow {
  id: number
  metricCode: string
  scope: string
  scopeObjectId?: number
  scopeObjectName?: string
  year?: string
  month?: string
  week?: string
  day?: string
  value: string
  date?: string
  calcType?: string
  calculatedBy?: string
  period?: string
}

export interface MetricDataVO {
  metric: MetricVO
  rows: MetricDataRow[]
  total: number
}

export interface MetricCalcVO {
  code: string
  name: string
  recordCount: number
  cycle: string
  calcType: string
  calcTime: number | string
}

export const getMetricDict = () => {
  return request.get<Record<string, Array<{ value: string; label: string }>>>({
    url: '/zentao/metric/dict'
  })
}

export const getMetricSummary = () => {
  return request.get<{
    totalCount: number
    implementedCount: number
    pendingCount: number
    dataCount: number
    lastCalcTime?: string
  }>({ url: '/zentao/metric/summary' })
}

export const getMetricPage = (params: PageParam) => {
  return request.get<PageResult<MetricVO[]>>({ url: '/zentao/metric/page', params })
}

export const getMetric = (code: string) => {
  return request.get<MetricVO>({ url: '/zentao/metric/get', params: { code } })
}

/** 计算一个度量项 */
export const calcMetric = (code: string, calcType = 'inference') => {
  return request.post<MetricCalcVO>({ url: '/zentao/metric/calc', params: { code, calcType } })
}

/** 计算所有已迁移口径 */
export const calcAllMetric = (calcType = 'inference') => {
  return request.post<MetricCalcVO[]>({ url: '/zentao/metric/calc-all', params: { calcType } })
}

/** 查度量数据 */
export const getMetricData = (params: {
  code: string
  scope?: string
  dateBegin?: string
  dateEnd?: string
  pageNo?: number
  pageSize?: number
}) => {
  return request.get<MetricDataVO>({ url: '/zentao/metric/data', params })
}
