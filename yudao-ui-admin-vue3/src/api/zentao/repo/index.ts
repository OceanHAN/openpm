import request from '@/config/axios'

// 代码库（repo）：本实现只支持「本地 Git 仓库」——路径是服务器上可访问的 git 仓库
export interface RepoVO {
  id?: number
  name: string
  product?: string
  scmType?: string
  path: string
  defaultBranch?: string
  desc?: string
  acl?: string
  status?: string
  synced?: number
  lastSyncRevision?: string
  lastSyncDate?: string
  lastSyncCount?: number
  createdDate?: string
}

export interface RepoCommitVO {
  id: number
  repo: number
  repoName?: string
  revision: string
  commit: number
  comment: string
  committer: string
  time: string
  fileCount: number
  files?: { action: string; path: string; oldPath: string }[]
  linkedObjects?: { objectType: string; objectID: number; objectName: string }[]
}

export const getRepoPage = (params: any) => {
  return request.get({ url: '/zentao/repo/page', params })
}

export const getRepo = (id: number) => {
  return request.get<RepoVO>({ url: '/zentao/repo/get', params: { id } })
}

export const getRepoSimpleList = () => {
  return request.get<RepoVO[]>({ url: '/zentao/repo/simple-list' })
}

export const createRepo = (data: RepoVO) => {
  return request.post({ url: '/zentao/repo/create', data })
}

export const updateRepo = (data: RepoVO) => {
  return request.put({ url: '/zentao/repo/update', data })
}

export const deleteRepo = (id: number) => {
  return request.delete({ url: '/zentao/repo/delete', params: { id } })
}

/** 同步提交记录：跑一次 git log（增量），返回本次新入库的提交数 */
export const syncRepo = (id: number, maxCount?: number) => {
  return request.post({ url: '/zentao/repo/sync', params: { id, maxCount } })
}

export const getCommitPage = (params: any) => {
  return request.get({ url: '/zentao/repo/commit-page', params })
}

export const getCommit = (repo: number, revision: string) => {
  return request.get<RepoCommitVO>({ url: '/zentao/repo/commit-get', params: { repo, revision } })
}
