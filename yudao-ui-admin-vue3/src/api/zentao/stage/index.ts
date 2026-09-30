import request from '@/config/axios'

// 阶段模板 VO（zt_stage）
//   注意：这是「流程模板」，不是项目里的阶段；
//   项目里实际的阶段是 zt_project 里 type='stage' 的记录，由模板生成。
export interface StageVO {
  id?: number
  workflowGroup?: number
  name?: string
  percent?: string
  type?: string
  typeName?: string
  projectType?: string
  order?: number
  createdBy?: string
  createdDate?: number | string
  // 项目阶段（实例）专有
  project?: number
  projectName?: string
  status?: string
  statusName?: string
  begin?: string
  end?: string
  realBegan?: string
  realEnd?: string
  estimate?: number
  consumed?: number
  left?: number
  progress?: number
}

export interface StageTypeVO {
  value: string
  label: string
}

// 阶段类型列表
export const getStageTypeList = () => {
  return request.get<StageTypeVO[]>({ url: '/zentao/stage/type-list' })
}

// 某个流程模板组下的阶段
export const getStageList = (workflowGroup: number) => {
  return request.get<StageVO[]>({ url: '/zentao/stage/list', params: { workflowGroup } })
}

// 按项目流程类型查模板
export const getStageListByProjectType = (projectType?: string) => {
  return request.get<StageVO[]>({ url: '/zentao/stage/list-by-project-type', params: { projectType } })
}

// 占比合计
export const getTotalPercent = (workflowGroup: number) => {
  return request.get<number>({ url: '/zentao/stage/total-percent', params: { workflowGroup } })
}

// 阶段详情
export const getStage = (id: number) => {
  return request.get<StageVO>({ url: '/zentao/stage/get', params: { id } })
}

// 新建阶段模板
export const createStage = (data: StageVO) => {
  return request.post({ url: '/zentao/stage/create', data })
}

// 批量新建
export const batchCreateStage = (workflowGroup: number, stages: StageVO[]) => {
  return request.post({ url: '/zentao/stage/batch-create', params: { workflowGroup }, data: stages })
}

// 修改阶段模板
export const updateStage = (data: StageVO) => {
  return request.put({ url: '/zentao/stage/update', data })
}

// 删除阶段模板
export const deleteStage = (id: number) => {
  return request.delete({ url: '/zentao/stage/delete', params: { id } })
}

// 重排（按传入顺序 1..n）
export const updateStageOrder = (stageIds: number[]) => {
  return request.put({ url: '/zentao/stage/update-order', data: stageIds })
}

// 按模板为项目生成阶段
export const generateStages = (project: number, workflowGroup?: number) => {
  return request.post<number[]>({ url: '/zentao/stage/generate', params: { project, workflowGroup } })
}

// 项目下的阶段
export const getProjectStages = (project: number) => {
  return request.get<StageVO[]>({ url: '/zentao/stage/project-stages', params: { project } })
}

// 删除项目的全部阶段
export const deleteProjectStages = (project: number) => {
  return request.delete({ url: '/zentao/stage/delete-project-stages', params: { project } })
}
