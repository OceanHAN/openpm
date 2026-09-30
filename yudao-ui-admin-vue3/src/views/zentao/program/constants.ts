// 项目集状态（对齐禅道 $lang->program->statusList）
export const PROGRAM_STATUS_OPTIONS = [
  { value: 'wait', label: '未开始', tag: 'info' },
  { value: 'doing', label: '进行中', tag: 'primary' },
  { value: 'suspended', label: '已挂起', tag: 'warning' },
  { value: 'closed', label: '已关闭', tag: 'success' }
]

export const programLabelOf = (options: { value: string; label: string }[], value?: string) =>
  options.find((o) => o.value === value)?.label ?? value ?? '-'

export const programTagOf = (value?: string) =>
  PROGRAM_STATUS_OPTIONS.find((o) => o.value === value)?.tag ?? 'info'
