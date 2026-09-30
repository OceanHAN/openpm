// 产品计划模块的常量
//
// 【禅道语义】
//   status: wait 未开始 → doing 进行中 → done 已完成；任意态 → closed 已关闭
//   激活（activate）后回到「进行中」而不是「未开始」
//   「待定」不是状态，而是日期哨兵 2030-01-01（后端返回 future 标记）
//   parent: 0 独立 / >0 子计划 / -1 有子计划（有子计划的父计划不能删）

export const PLAN_STATUS_OPTIONS = [
  { value: 'wait', label: '未开始', tag: 'info' },
  { value: 'doing', label: '进行中', tag: 'primary' },
  { value: 'done', label: '已完成', tag: 'success' },
  { value: 'closed', label: '已关闭', tag: 'info' }
]

export const PLAN_CLOSED_REASON_OPTIONS = [
  { value: 'done', label: '已完成' },
  { value: 'cancel', label: '已取消' }
]

/** 待定计划的日期哨兵，和后端 PlanDO.FUTURE_DATE 保持一致 */
export const FUTURE_DATE = '2030-01-01'

export const planLabelOf = (options: Array<{ value: any; label: string }>, value: any) => {
  const hit = options.find((o) => o.value === value)
  return hit ? hit.label : value ?? ''
}

export const planTagOf = (value?: string) => {
  const hit = PLAN_STATUS_OPTIONS.find((o) => o.value === value)
  return hit ? hit.tag : 'info'
}

/** 展示用：待定计划显示「待定」，否则显示日期区间 */
export const planPeriod = (row: { begin?: string; end?: string; future?: boolean }) => {
  if (row.future || (row.begin === FUTURE_DATE && row.end === FUTURE_DATE)) return '待定'
  return `${row.begin || '-'} ~ ${row.end || '-'}`
}
