// 与后端 TestTaskStatusEnum / TestResultEnum 一一对应
export const TEST_TASK_STATUS_OPTIONS = [
  { value: 'wait', label: '未开始', tag: 'info' },
  { value: 'doing', label: '进行中', tag: 'primary' },
  { value: 'done', label: '已关闭', tag: 'success' },
  { value: 'blocked', label: '被阻塞', tag: 'danger' }
]

/** 执行结果。'' = 未执行（禅道里 lastRunResult 为空表示还没跑） */
export const TEST_RESULT_OPTIONS = [
  { value: '', label: '未执行', tag: 'info' },
  { value: 'pass', label: '通过', tag: 'success' },
  { value: 'fail', label: '失败', tag: 'danger' },
  { value: 'blocked', label: '阻塞', tag: 'warning' },
  { value: 'n/a', label: '忽略', tag: 'info' }
]

/** 执行时每个步骤可选的结果：n/a 表示这一步不适用 */
export const STEP_RESULT_OPTIONS = TEST_RESULT_OPTIONS.filter((o) => o.value !== '')

export const TEST_TASK_TYPE_OPTIONS = [
  { value: 'feature', label: '功能测试' },
  { value: 'interface', label: '接口测试' },
  { value: 'performance', label: '性能测试' },
  { value: 'unit', label: '单元测试' },
  { value: 'install', label: '安装部署' },
  { value: 'other', label: '其他' }
]

export const labelOf = (options: { value: string | number; label: string }[], value?: string | number) =>
  options.find((o) => o.value === value)?.label ?? value ?? ''

export const tagOf = (options: { value: string; tag?: string }[], value?: string) =>
  options.find((o) => o.value === value)?.tag ?? 'info'

/** type 是逗号列表，逐段翻译 */
export const typeNames = (type?: string) => {
  if (!type) return '-'
  return type
    .split(',')
    .filter(Boolean)
    .map((t) => labelOf(TEST_TASK_TYPE_OPTIONS, t.trim()))
    .join('、')
}
