// 禅道执行模块的枚举常量
//
// 【重要】执行与项目共用 zt_project 表，靠 type 区分：
//   type = 'project'                    → 项目
//   type IN ('sprint','stage','kanban') → 执行
// 所以这里只需要定义「执行类型」，状态沿用项目的状态枚举。

/** 执行类型。对齐禅道 $lang->execution->typeList */
export const EXECUTION_TYPE_OPTIONS = [
  { value: 'sprint', label: '迭代', tag: 'primary' },
  { value: 'stage', label: '阶段', tag: 'success' },
  { value: 'kanban', label: '看板', tag: 'warning' }
]

/** 执行状态。与项目共用 ProjectStatusEnum */
export const EXECUTION_STATUS_OPTIONS = [
  { value: 'wait', label: '未开始', tag: 'info' },
  { value: 'doing', label: '进行中', tag: 'primary' },
  { value: 'suspended', label: '已挂起', tag: 'warning' },
  { value: 'closed', label: '已关闭', tag: 'info' },
  { value: 'delay', label: '已延期', tag: 'danger' }
]

export const PRI_OPTIONS = [
  { value: 1, label: '1 - 最高' },
  { value: 2, label: '2 - 高' },
  { value: 3, label: '3 - 中' },
  { value: 4, label: '4 - 低' }
]

export const executionLabelOf = (options: Array<{ value: any; label: string }>, value: any) => {
  const hit = options.find((o) => o.value === value)
  return hit ? hit.label : value ?? ''
}

export const executionTagOf = (value: string) => {
  const hit = EXECUTION_STATUS_OPTIONS.find((o) => o.value === value)
  return hit ? hit.tag : 'info'
}

export const executionTypeTagOf = (value: string) => {
  const hit = EXECUTION_TYPE_OPTIONS.find((o) => o.value === value)
  return hit ? hit.tag : 'info'
}
