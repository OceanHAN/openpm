// 禅道缺陷模块的枚举常量
// 取值与后端 BugStatusEnum / BugResolutionEnum / BugTypeEnum 以及禅道存储值一一对应。

export const BUG_STATUS_OPTIONS = [
  { value: 'active', label: '激活', tag: 'danger' },
  { value: 'resolved', label: '已解决', tag: 'success' },
  { value: 'closed', label: '已关闭', tag: 'info' }
]

/** 解决方案 */
export const BUG_RESOLUTION_OPTIONS = [
  { value: 'bydesign', label: '设计如此' },
  { value: 'duplicate', label: '重复Bug' },
  { value: 'external', label: '外部原因' },
  { value: 'fixed', label: '已解决' },
  { value: 'notrepro', label: '无法重现' },
  { value: 'postponed', label: '延期处理' },
  { value: 'willnotfix', label: '不予解决' },
  { value: 'tostory', label: '转为需求' }
]

/** 缺陷类型 */
export const BUG_TYPE_OPTIONS = [
  { value: 'codeerror', label: '代码错误' },
  { value: 'config', label: '配置相关' },
  { value: 'install', label: '安装部署' },
  { value: 'security', label: '安全相关' },
  { value: 'performance', label: '性能问题' },
  { value: 'standard', label: '标准规范' },
  { value: 'automation', label: '测试脚本' },
  { value: 'designdefect', label: '设计缺陷' },
  { value: 'codeimprovement', label: '代码改进' },
  { value: 'others', label: '其他' }
]

/** 严重程度 */
export const BUG_SEVERITY_OPTIONS = [
  { value: 1, label: '1 - 致命', tag: 'danger' },
  { value: 2, label: '2 - 严重', tag: 'warning' },
  { value: 3, label: '3 - 一般', tag: 'info' },
  { value: 4, label: '4 - 轻微', tag: 'info' }
]

/** 优先级 */
export const PRI_OPTIONS = [
  { value: 1, label: '1 - 最高' },
  { value: 2, label: '2 - 高' },
  { value: 3, label: '3 - 中' },
  { value: 4, label: '4 - 低' }
]

/** 缺陷字段中文标签（用于操作日志） */
export const BUG_FIELD_LABELS: Record<string, string> = {
  title: '缺陷标题',
  steps: '重现步骤',
  status: '状态',
  severity: '严重程度',
  pri: '优先级',
  type: '缺陷类型',
  resolution: '解决方案',
  resolvedBuild: '解决版本',
  duplicateBug: '重复缺陷',
  os: '操作系统',
  browser: '浏览器',
  assignedTo: '指派给',
  resolvedBy: '解决人',
  resolvedDate: '解决时间',
  closedBy: '关闭人',
  closedDate: '关闭时间',
  activatedCount: '激活次数',
  activatedDate: '激活时间',
  openedBuild: '影响版本',
  keywords: '关键词',
  product: '所属产品',
  project: '所属项目',
  execution: '所属执行',
  story: '关联需求',
  task: '关联任务'
}

export const bugLabelOf = (options: Array<{ value: any; label: string }>, value: any) => {
  const hit = options.find((o) => o.value === value)
  return hit ? hit.label : value ?? ''
}

export const bugTagOf = (value: string) => {
  const hit = BUG_STATUS_OPTIONS.find((o) => o.value === value)
  return hit ? hit.tag : 'info'
}

export const severityTagOf = (value: number) => {
  const hit = BUG_SEVERITY_OPTIONS.find((o) => o.value === value)
  return hit ? hit.tag : 'info'
}

export const bugFieldLabel = (field: string) => BUG_FIELD_LABELS[field] ?? field
