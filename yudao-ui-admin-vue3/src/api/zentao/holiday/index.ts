import request from '@/config/axios'

// 节假日（holiday）：假期 holiday（不算工作日）与补班 working（算工作日，即调休）
export interface HolidayVO {
  id?: number
  name: string
  type?: string
  desc?: string
  year?: string
  begin?: string
  end?: string
}

export const getHolidayList = (params: { year?: string; type?: string }) => {
  return request.get<HolidayVO[]>({ url: '/zentao/holiday/list', params })
}

export const getHolidayYears = () => {
  return request.get<string[]>({ url: '/zentao/holiday/years' })
}

export const getHoliday = (id: number) => {
  return request.get<HolidayVO>({ url: '/zentao/holiday/get', params: { id } })
}

export const createHoliday = (data: HolidayVO) => {
  return request.post({ url: '/zentao/holiday/create', data })
}

export const updateHoliday = (data: HolidayVO) => {
  return request.put({ url: '/zentao/holiday/update', data })
}

export const deleteHoliday = (id: number) => {
  return request.delete({ url: '/zentao/holiday/delete', params: { id } })
}

/** 某区间的实际工作日（左闭右开）：补班算、假期不算、周末不算 */
export const getWorkingDays = (begin: string, end: string) => {
  return request.get<{ begin: string; end: string; count: number; days: string[] }>({
    url: '/zentao/holiday/working-days',
    params: { begin, end }
  })
}
