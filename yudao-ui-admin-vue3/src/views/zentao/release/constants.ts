// 发布模块的常量
//
// 【禅道语义】
//   status: wait 未开始 / normal 已发布 / fail 发布失败 / terminate 停止维护
//   必填字段随状态变：wait 不要求实际发布日期，normal 不要求计划发布日期
//   bugs 是「解决的 Bug」，leftBugs 是「遗留的 Bug」—— 两个清单
//   发布名全局唯一；创建发布会自动生成一个同名的「影子构建」

export const RELEASE_STATUS_OPTIONS = [
  { value: 'wait', label: '未开始', tag: 'info' },
  { value: 'normal', label: '已发布', tag: 'success' },
  { value: 'fail', label: '发布失败', tag: 'danger' },
  { value: 'terminate', label: '停止维护', tag: 'warning' }
]

export const releaseLabelOf = (options: Array<{ value: any; label: string }>, value: any) => {
  const hit = options.find((o) => o.value === value)
  return hit ? hit.label : value ?? ''
}

export const releaseTagOf = (value?: string) => {
  const hit = RELEASE_STATUS_OPTIONS.find((o) => o.value === value)
  return hit ? hit.tag : 'info'
}
