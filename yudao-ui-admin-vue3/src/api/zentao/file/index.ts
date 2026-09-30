import request from '@/config/axios'

// 附件 VO（zt_file）
//   附件不是独立功能，而是所有业务对象的公共能力：靠 (objectType, objectID) 挂在对象上。
//   字节由 yudao 的文件服务托管，url 就是可直接访问的地址。
export interface FileVO {
  id?: number
  title?: string
  extension?: string
  size?: number
  sizeText?: string
  objectType?: string
  objectID?: number
  // 临时分组 id：不为空表示「已上传但还没绑到对象」
  gid?: string
  url?: string
  addedBy?: string
  addedDate?: number | string
  downloads?: number
  image?: boolean
}

// 某对象的附件列表
export const getFileList = (objectType: string, objectID: number) => {
  return request.get<FileVO[]>({ url: '/zentao/file/list', params: { objectType, objectID } })
}

// 某临时分组（gid）下的待绑定附件
export const getFileListByGid = (gid: string) => {
  return request.get<FileVO[]>({ url: '/zentao/file/list-by-gid', params: { gid } })
}

// 某个对象的附件数量（列表页显示「附件 N」角标用）
export const getFileCount = (objectType: string, objectID: number) => {
  return request.get<number>({ url: '/zentao/file/count', params: { objectType, objectID } })
}

// 附件详情
export const getFile = (id: number) => {
  return request.get<FileVO>({ url: '/zentao/file/get', params: { id } })
}

// 上传附件
//   objectID 为空时表示「先上传、后绑定」，此时必须带 gid，
//   等对象保存成功后调用 bindFileByGid 补绑（对齐禅道 file->updateObjectID）
export const uploadFile = (
  file: File,
  params: { objectType?: string; objectID?: number; gid?: string }
) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<FileVO>({
    url: '/zentao/file/upload',
    params,
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 按 gid 绑定附件到对象
export const bindFileByGid = (data: { gid: string; objectType: string; objectID: number }) => {
  return request.post<number>({ url: '/zentao/file/bind-by-gid', data })
}

// 下载附件：返回可访问地址，并把下载次数 +1
export const downloadFile = (id: number) => {
  return request.get<string>({ url: '/zentao/file/download', params: { id } })
}

// 重命名附件
export const renameFile = (id: number, title: string) => {
  return request.put({ url: '/zentao/file/rename', data: { id, title } })
}

// 删除附件
export const deleteFile = (id: number) => {
  return request.delete({ url: '/zentao/file/delete', params: { id } })
}
