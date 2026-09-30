// 禅道项目模块的枚举常量
// 取值与后端 ProjectStatusEnum / ProjectModelEnum 以及禅道存储值一一对应。

export const PROJECT_STATUS_OPTIONS = [
  { value: 'wait', label: '未开始', tag: 'info' },
  { value: 'doing', label: '进行中', tag: 'primary' },
  { value: 'suspended', label: '已挂起', tag: 'warning' },
  { value: 'closed', label: '已关闭', tag: 'info' },
  { value: 'delay', label: '已延期', tag: 'danger' }
]

/** 项目管理模型 */
export const PROJECT_MODEL_OPTIONS = [
  { value: 'scrum', label: 'Scrum' },
  { value: 'waterfall', label: '瀑布' },
  { value: 'kanban', label: '看板' },
  { value: 'agileplus', label: '融合敏捷' },
  { value: 'waterfallplus', label: '融合瀑布' },
  { value: 'ipd', label: 'IPD' }
]

export const PROJECT_ACL_OPTIONS = [
  { value: 'open', label: '公开' },
  { value: 'private', label: '私有' }
]

export const PRI_OPTIONS = [
  { value: 1, label: '1 - 最高' },
  { value: 2, label: '2 - 高' },
  { value: 3, label: '3 - 中' },
  { value: 4, label: '4 - 低' }
]

/** 项目字段中文标签（用于操作日志） */
export const PROJECT_FIELD_LABELS: Record<string, string> = {
  name: '项目名称',
  code: '项目代号',
  model: '模型',
  type: '项目类型',
  category: '项目分类',
  status: '状态',
  desc: '项目描述',
  output: '交付物',
  pri: '优先级',
  budget: '预算',
  budgetUnit: '预算币种',
  begin: '计划开始',
  end: '计划结束',
  realBegan: '实际开始',
  realEnd: '实际结束',
  days: '可用工作日',
  estimate: '预计工时',
  left: '剩余工时',
  consumed: '已消耗工时',
  progress: '进度',
  PO: '产品负责人',
  PM: '项目经理',
  QD: '测试负责人',
  RD: '研发负责人',
  team: '团队成员',
  teamCount: '团队人数',
  acl: '访问控制',
  hasProduct: '关联产品',
  multiple: '多执行',
  milestone: '里程碑',
  closedReason: '关闭原因',
  closedBy: '关闭人',
  closedDate: '关闭时间',
  suspendedDate: '挂起时间',
  parent: '父项目'
}

export const projectLabelOf = (options: Array<{ value: any; label: string }>, value: any) => {
  const hit = options.find((o) => o.value === value)
  return hit ? hit.label : value ?? ''
}

export const projectTagOf = (value: string) => {
  const hit = PROJECT_STATUS_OPTIONS.find((o) => o.value === value)
  return hit ? hit.tag : 'info'
}

export const projectFieldLabel = (field: string) => PROJECT_FIELD_LABELS[field] ?? field
