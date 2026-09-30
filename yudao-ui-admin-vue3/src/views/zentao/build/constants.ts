// 构建模块的常量
//
// 【禅道语义】
//   stories = 本次完成的需求，bugs = 本次解决的 Bug，两者都是逗号列表
//   集成构建：execution=0、branch 取子构建并集、读取时 stories/bugs 合并子构建的
//   关联 Bug 会把未解决的 Bug 自动置为「已解决」，resolvedBuild 指向本构建

/** 构建下需求的候选状态：已关闭的需求不再参与构建 */
export const BUILD_STORY_EXCLUDE_STATUS = ['closed']

export const bugStatusLabel = (status?: string) => {
  if (status === 'active') return '激活'
  if (status === 'resolved') return '已解决'
  if (status === 'closed') return '已关闭'
  return status || ''
}

export const bugStatusTag = (status?: string) => {
  if (status === 'active') return 'danger'
  if (status === 'resolved') return 'warning'
  return 'info'
}
