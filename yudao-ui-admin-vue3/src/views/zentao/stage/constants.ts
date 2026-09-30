// 阶段（瀑布流程）模块的常量
//
// 【禅道语义】
//   zt_stage 是「阶段模板」：一个 workflowGroup 下若干阶段，各自带工作量占比，
//   同组合计不能超过 100%。项目里实际的阶段是 zt_project 里 type='stage' 的记录。
//   模板阶段类型：mix 综合 / request 需求 / design 设计 / dev 开发 / qa 测试 /
//                release 发布 / review 总结评审 / other 其他

export const STAGE_TYPE_OPTIONS = [
  { value: 'mix', label: '综合', tag: 'info' },
  { value: 'request', label: '需求', tag: 'primary' },
  { value: 'design', label: '设计', tag: 'success' },
  { value: 'dev', label: '开发', tag: 'warning' },
  { value: 'qa', label: '测试', tag: 'danger' },
  { value: 'release', label: '发布', tag: 'success' },
  { value: 'review', label: '总结评审', tag: 'info' },
  { value: 'other', label: '其他', tag: 'info' }
]

/** 项目流程类型（禅道 $config->project->modelList 的子集） */
export const PROJECT_TYPE_OPTIONS = [
  { value: 'waterfall', label: '瀑布' },
  { value: 'waterfallplus', label: '融合瀑布' },
  { value: 'ipd', label: 'IPD' }
]

export const stageLabelOf = (options: Array<{ value: any; label: string }>, value: any) => {
  const hit = options.find((o) => o.value === value)
  return hit ? hit.label : value ?? ''
}

export const stageTypeTagOf = (type?: string) => {
  const hit = STAGE_TYPE_OPTIONS.find((o) => o.value === type)
  return hit ? hit.tag : 'info'
}
