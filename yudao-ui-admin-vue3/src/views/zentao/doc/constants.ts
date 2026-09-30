// 文档类型：chapter 是章节（树节点，没有正文），其余才是文档
// 与后端 DocTypeEnum 一一对应
export const DOC_TYPE_OPTIONS = [
  { value: 'chapter', label: '章节', chapter: true },
  { value: 'html', label: '富文本', chapter: false },
  { value: 'markdown', label: 'Markdown', chapter: false },
  { value: 'text', label: '纯文本', chapter: false },
  { value: 'url', label: '链接', chapter: false },
  { value: 'word', label: 'Word', chapter: false },
  { value: 'ppt', label: 'PPT', chapter: false },
  { value: 'excel', label: 'Excel', chapter: false },
  { value: 'attachment', label: '附件', chapter: false }
]

export const DOC_STATUS_OPTIONS = [
  { value: 'normal', label: '已发布', tag: 'success' },
  { value: 'draft', label: '草稿', tag: 'warning' }
]

export const DOC_LIB_TYPE_OPTIONS = [
  { value: 'product', label: '产品文档库' },
  { value: 'project', label: '项目文档库' },
  { value: 'execution', label: '执行文档库' },
  { value: 'custom', label: '自定义文档库' }
]

/** 需要正文的类型（chapter/attachment 不需要；url 的正文是链接） */
export const needsContent = (type?: string) => !!type && type !== 'chapter' && type !== 'attachment'

export const labelOf = (options: { value: string; label: string }[], value?: string) =>
  options.find((o) => o.value === value)?.label ?? value ?? ''

export const tagOf = (options: { value: string; tag?: string }[], value?: string) =>
  options.find((o) => o.value === value)?.tag ?? 'info'

/** 类型对应的图标，纯展示优化 */
export const iconOf = (type?: string) => {
  switch (type) {
    case 'chapter':
      return 'ep:folder'
    case 'markdown':
      return 'ep:document'
    case 'url':
      return 'ep:link'
    case 'word':
      return 'ep:document-copy'
    case 'ppt':
      return 'ep:presentation'
    case 'excel':
      return 'ep:grid'
    case 'attachment':
      return 'ep:paperclip'
    default:
      return 'ep:document'
  }
}
