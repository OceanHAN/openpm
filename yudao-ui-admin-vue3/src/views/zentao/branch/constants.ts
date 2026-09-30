// 分支/平台模块的枚举常量
//
// 【禅道语义】zt_branch 是产品维度的分支表，产品类型决定称呼：
//   product.type = 'branch'   → 「分支」
//   product.type = 'platform' → 「平台」
//   product.type = 'normal'   → 不启用分支
// 后端会在 branchLabel 字段里给出当前文案，前端直接用。
//
// 另外 id = 0 是「主干」，不落库（mainBranch = true）。

export const BRANCH_STATUS_OPTIONS = [
  { value: 'active', label: '激活', tag: 'success' },
  { value: 'closed', label: '已关闭', tag: 'info' }
]

export const branchLabelOf = (options: Array<{ value: any; label: string }>, value: any) => {
  const hit = options.find((o) => o.value === value)
  return hit ? hit.label : value ?? ''
}

export const branchTagOf = (value?: string) => {
  const hit = BRANCH_STATUS_OPTIONS.find((o) => o.value === value)
  return hit ? hit.tag : 'info'
}
