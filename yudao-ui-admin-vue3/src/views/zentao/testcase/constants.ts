// 与后端 CaseTypeEnum / CaseStageEnum / CaseStatusEnum 一一对应
export const CASE_TYPE_OPTIONS = [
  { value: 'unit', label: '单元测试' },
  { value: 'interface', label: '接口测试' },
  { value: 'feature', label: '功能测试' },
  { value: 'install', label: '安装部署' },
  { value: 'config', label: '配置相关' },
  { value: 'performance', label: '性能测试' },
  { value: 'security', label: '安全相关' },
  { value: 'other', label: '其他' }
]

export const CASE_STAGE_OPTIONS = [
  { value: 'unittest', label: '单元测试环节' },
  { value: 'feature', label: '功能测试环节' },
  { value: 'intergrate', label: '集成测试环节' },
  { value: 'system', label: '系统测试环节' },
  { value: 'smoke', label: '冒烟测试环节' },
  { value: 'bvt', label: '版本验证环节' }
]

export const CASE_STATUS_OPTIONS = [
  { value: 'wait', label: '待评审', tag: 'warning' },
  { value: 'normal', label: '正常', tag: 'success' },
  { value: 'blocked', label: '被阻塞', tag: 'danger' },
  { value: 'investigate', label: '研究中', tag: 'info' }
]

/** 评审可选的结果：wait 不能作为评审结果 */
export const CASE_REVIEW_OPTIONS = CASE_STATUS_OPTIONS.filter((o) => o.value !== 'wait')

export const CASE_PRI_OPTIONS = [
  { value: 1, label: '1' },
  { value: 2, label: '2' },
  { value: 3, label: '3' },
  { value: 4, label: '4' }
]

export const labelOf = (options: { value: string | number; label: string }[], value?: string | number) =>
  options.find((o) => o.value === value)?.label ?? value ?? ''

export const tagOf = (options: { value: string; tag?: string }[], value?: string) =>
  options.find((o) => o.value === value)?.tag ?? 'info'

/** stage 是逗号列表，逐段翻译 */
export const stageNames = (stage?: string) => {
  if (!stage) return '-'
  return stage
    .split(',')
    .filter(Boolean)
    .map((s) => labelOf(CASE_STAGE_OPTIONS, s.trim()))
    .join('、')
}
