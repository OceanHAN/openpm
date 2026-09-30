// 禅道任务模块的枚举常量
// 取值与后端 TaskStatusEnum 以及禅道 zt_task 表里的实际存储值一一对应。

export const TASK_STATUS_OPTIONS = [
  { value: 'wait', label: '未开始', tag: 'info' },
  { value: 'doing', label: '进行中', tag: 'primary' },
  { value: 'done', label: '已完成', tag: 'success' },
  { value: 'pause', label: '已暂停', tag: 'warning' },
  { value: 'cancel', label: '已取消', tag: 'info' },
  { value: 'closed', label: '已关闭', tag: 'info' }
]

/** 任务类型 */
export const TASK_TYPE_OPTIONS = [
  { value: 'design', label: '设计' },
  { value: 'devel', label: '开发' },
  { value: 'test', label: '测试' },
  { value: 'study', label: '研究' },
  { value: 'discuss', label: '讨论' },
  { value: 'ui', label: '界面' },
  { value: 'affair', label: '事务' },
  { value: 'misc', label: '其他' }
]

/** 优先级 */
export const PRI_OPTIONS = [
  { value: 1, label: '1 - 最高' },
  { value: 2, label: '2 - 高' },
  { value: 3, label: '3 - 中' },
  { value: 4, label: '4 - 低' }
]

/** 任务字段中文标签（用于操作日志） */
export const TASK_FIELD_LABELS: Record<string, string> = {
  name: '任务名称',
  desc: '任务描述',
  status: '状态',
  pri: '优先级',
  estimate: '预计工时',
  consumed: '已消耗工时',
  left: '剩余工时',
  type: '任务类型',
  deadline: '截止日期',
  assignedTo: '指派给',
  estStarted: '预计开始',
  realStarted: '实际开始',
  finishedBy: '完成人',
  finishedDate: '完成时间',
  closedReason: '关闭原因',
  version: '版本号',
  keywords: '关键词',
  project: '所属项目',
  execution: '所属执行',
  story: '关联需求',
  module: '所属模块'
}

export const taskLabelOf = (
  options: Array<{ value: any; label: string }>,
  value: any
) => {
  const hit = options.find((o) => o.value === value)
  return hit ? hit.label : value ?? ''
}

export const taskTagOf = (value: string) => {
  const hit = TASK_STATUS_OPTIONS.find((o) => o.value === value)
  return hit ? hit.tag : 'info'
}

export const taskFieldLabel = (field: string) => TASK_FIELD_LABELS[field] ?? field
