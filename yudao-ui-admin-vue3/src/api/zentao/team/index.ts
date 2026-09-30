import request from '@/config/axios'

// 项目/执行团队成员（zt_team）
//
// 项目与执行的成员在同一张表里，靠 type 区分；团队人数、可用工时都来自这张表。
export interface TeamMemberVO {
  id?: number
  /** 所属对象编号（项目或执行） */
  root?: number
  /** project / execution */
  type?: string
  account?: string
  realname?: string
  role?: string
  position?: string
  /** yes/no：受限访问 */
  limited?: string
  join?: string
  /** 可用天数 */
  days?: number
  /** 每天投入小时数（默认 7） */
  hours?: number
  /** 可用工时 = days × hours */
  totalHours?: number
  order?: number
}

export const TEAM_ROLE_OPTIONS = [
  '项目经理',
  '产品负责人',
  '测试负责人',
  '研发负责人',
  '研发',
  '测试',
  '产品',
  '设计',
  '运维'
]

export const getTeamList = (root: number, type = 'project') => {
  return request.get<TeamMemberVO[]>({ url: '/zentao/team/list', params: { root, type } })
}

export const getTeamTotalHours = (root: number, type = 'project') => {
  return request.get({ url: '/zentao/team/total-hours', params: { root, type } })
}

export const addTeamMember = (data: TeamMemberVO) => {
  return request.post({ url: '/zentao/team/add-member', data })
}

export const updateTeamMember = (data: TeamMemberVO) => {
  return request.put({ url: '/zentao/team/update-member', data })
}

export const removeTeamMember = (id: number) => {
  return request.delete({ url: '/zentao/team/remove-member', params: { id } })
}

export const updateTeamMembers = (root: number, type: string, members: TeamMemberVO[]) => {
  return request.put({ url: '/zentao/team/update-members', data: { root, type, members } })
}
