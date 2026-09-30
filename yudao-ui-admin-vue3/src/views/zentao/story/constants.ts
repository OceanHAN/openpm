// 禅道需求模块的枚举常量
// 取值与后端 StoryStatusEnum / StoryStageEnum / StoryCategoryEnum 以及
// 禅道 zt_story 表里的实际存储值一一对应，不做任何映射转换。

/** 需求状态 */
export const STORY_STATUS = {
  DRAFT: 'draft',
  REVIEWING: 'reviewing',
  ACTIVE: 'active',
  CHANGING: 'changing',
  CLOSED: 'closed'
} as const

export const STORY_STATUS_OPTIONS = [
  { value: 'draft', label: '草稿', tag: 'info' },
  { value: 'reviewing', label: '评审中', tag: 'warning' },
  { value: 'active', label: '激活', tag: 'success' },
  { value: 'changing', label: '变更中', tag: 'warning' },
  { value: 'closed', label: '已关闭', tag: 'info' }
]

/** 研发阶段 */
export const STORY_STAGE_OPTIONS = [
  { value: 'wait', label: '未开始' },
  { value: 'planned', label: '已计划' },
  { value: 'projected', label: '研发立项' },
  { value: 'designing', label: '设计中' },
  { value: 'designed', label: '设计完毕' },
  { value: 'developing', label: '研发中' },
  { value: 'developed', label: '研发完毕' },
  { value: 'testing', label: '测试中' },
  { value: 'tested', label: '测试完毕' },
  { value: 'verified', label: '已验收' }
]

/** 需求分类 */
export const STORY_CATEGORY_OPTIONS = [
  { value: 'feature', label: '功能' },
  { value: 'interface', label: '接口' },
  { value: 'performance', label: '性能' },
  { value: 'safe', label: '安全' },
  { value: 'experience', label: '体验' },
  { value: 'improve', label: '改进' },
  { value: 'other', label: '其他' }
]

/** 需求来源 */
export const STORY_SOURCE_OPTIONS = [
  { value: 'customer', label: '客户' },
  { value: 'user', label: '用户' },
  { value: 'po', label: '产品经理' },
  { value: 'market', label: '市场' },
  { value: 'service', label: '客服' },
  { value: 'operation', label: '运营' },
  { value: 'support', label: '技术支持' },
  { value: 'competitor', label: '竞争对手' },
  { value: 'partner', label: '合作伙伴' },
  { value: 'dev', label: '开发人员' },
  { value: 'tester', label: '测试人员' },
  { value: 'bug', label: 'Bug' },
  { value: 'forum', label: '论坛' },
  { value: 'other', label: '其他' }
]

/** 关闭原因 */
export const STORY_CLOSED_REASON_OPTIONS = [
  { value: 'done', label: '已完成' },
  { value: 'duplicate', label: '重复' },
  { value: 'postponed', label: '延期' },
  { value: 'willnotdo', label: '不做' },
  { value: 'bydesign', label: '设计如此' }
]

/** 评审结果 */
export const STORY_REVIEW_RESULT_OPTIONS = [
  { value: 'pass', label: '确认通过', tag: 'success' },
  { value: 'clarify', label: '有待明确', tag: 'warning' },
  { value: 'revert', label: '撤销变更', tag: 'danger' },
  { value: 'reject', label: '拒绝', tag: 'danger' }
]

/** 优先级 */
export const STORY_PRI_OPTIONS = [
  { value: 1, label: '1 - 最高' },
  { value: 2, label: '2 - 高' },
  { value: 3, label: '3 - 中' },
  { value: 4, label: '4 - 低' }
]

/** 根据值取标签文案 */
export const labelOf = (options: Array<{ value: any; label: string }>, value: any) => {
  const hit = options.find((o) => o.value === value)
  return hit ? hit.label : value ?? ''
}

/** 根据值取状态标签颜色 */
export const tagOf = (value: string) => {
  const hit = STORY_STATUS_OPTIONS.find((o) => o.value === value)
  return hit ? hit.tag : 'info'
}

/**
 * 操作日志里的字段名 → 中文标签
 * 后端 zt_history.field 存的是 Java 字段名（对齐禅道），前端做展示映射
 */
export const FIELD_LABELS: Record<string, string> = {
  title: '需求标题',
  spec: '需求描述',
  verify: '验收标准',
  status: '状态',
  stage: '研发阶段',
  version: '版本号',
  pri: '优先级',
  category: '需求分类',
  source: '需求来源',
  sourceNote: '来源备注',
  estimate: '预计工时',
  assignedTo: '指派给',
  closedReason: '关闭原因',
  closedBy: '关闭人',
  closedDate: '关闭时间',
  duplicateStory: '重复需求',
  reviewedBy: '已评审人',
  reviewedDate: '评审时间',
  keywords: '关键词',
  type: '需求类型',
  plan: '所属计划',
  module: '所属模块',
  product: '所属产品',
  branch: '所属分支'
}

export const fieldLabel = (field: string) => FIELD_LABELS[field] ?? field

/** 操作动作 → 标签颜色 */
export const actionTagOf = (action: string) => {
  switch (action) {
    case 'created':
      return 'success'
    case 'edited':
      return 'primary'
    case 'changed':
      return 'warning'
    case 'closed':
      return 'danger'
    case 'activated':
      return 'success'
    case 'deleted':
      return 'danger'
    case 'submitReview':
      return 'warning'
    case 'reviewed':
      return 'primary'
    case 'reviewResult':
      return 'info'
    default:
      return 'info'
  }
}
