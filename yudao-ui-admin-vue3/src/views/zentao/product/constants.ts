// 禅道产品模块的枚举常量
// 取值与禅道 $lang->product->typeList / statusList 一一对应。

export const PRODUCT_STATUS_OPTIONS = [
  { value: 'normal', label: '正常', tag: 'success' },
  { value: 'closed', label: '结束', tag: 'info' }
]

export const PRODUCT_TYPE_OPTIONS = [
  { value: 'normal', label: '正常' },
  { value: 'branch', label: '多分支' },
  { value: 'platform', label: '多平台' }
]

export const PRODUCT_ACL_OPTIONS = [
  { value: 'open', label: '公开' },
  { value: 'private', label: '私有' }
]

/** 产品字段中文标签（用于操作日志） */
export const PRODUCT_FIELD_LABELS: Record<string, string> = {
  name: '产品名称',
  code: '产品代号',
  type: '类型',
  status: '状态',
  desc: '产品描述',
  PO: '产品经理',
  QD: '测试负责人',
  RD: '研发负责人',
  acl: '访问控制',
  program: '所属项目集',
  line: '所属产品线',
  order: '排序',
  closedDate: '关闭时间'
}

export const productLabelOf = (options: Array<{ value: any; label: string }>, value: any) => {
  const hit = options.find((o) => o.value === value)
  return hit ? hit.label : value ?? ''
}

export const productTagOf = (value: string) => {
  const hit = PRODUCT_STATUS_OPTIONS.find((o) => o.value === value)
  return hit ? hit.tag : 'info'
}

export const productFieldLabel = (field: string) => PRODUCT_FIELD_LABELS[field] ?? field
