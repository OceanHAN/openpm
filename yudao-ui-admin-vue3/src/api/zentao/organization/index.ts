import request from '@/config/axios'

// 组织与权限（禅道视角的只读视图）
//   zt_user/zt_dept/zt_group/zt_grouppriv 不迁移，映射到 yudao 的 system_* 表；
//   这里是把 yudao 权限翻译回禅道视角的结果。

export interface OrgUserVO {
  id?: number
  /** 禅道 zt_user.account */
  account?: string
  /** 禅道 zt_user.realname */
  realname?: string
  deptId?: number
  deptName?: string
  /** 角色名（禅道的权限包） */
  roleNames?: string
  roleCodes?: string
  email?: string
  mobile?: string
  gender?: number
  status?: number
  loginIp?: string
  loginDate?: number | string
  permissionCount?: number
  modules?: string[]
}

export interface OrgPermissionVO {
  module?: string
  moduleName?: string
  methods?: string[]
  count?: number
}

export interface OrgRoleVO {
  id?: number
  /** 禅道 zt_group.name */
  name?: string
  code?: string
  dataScope?: number
  dataScopeName?: string
  status?: number
  userCount?: number
  zentaoPermissionCount?: number
  remark?: string
  permissions?: OrgPermissionVO[]
}

export interface OrgDeptVO {
  id?: number
  name?: string
  parentId?: number
  leaderUserId?: number
  leaderName?: string
  sort?: number
  status?: number
  userCount?: number
  grade?: number
  path?: string
  children?: OrgDeptVO[]
}

export interface OrgMappingVO {
  zentaoTable?: string
  yudaoTable?: string
  strategy?: string
  fieldMapping?: string[]
  notes?: string[]
}

// 用户列表
export const getUserList = (params: { keyword?: string; deptId?: number; status?: number }) => {
  return request.get<OrgUserVO[]>({ url: '/zentao/organization/user-list', params })
}

// 某用户的权限（按模块聚合）
export const getUserPermissions = (userId: number) => {
  return request.get<OrgPermissionVO[]>({ url: '/zentao/organization/user-permissions', params: { userId } })
}

// 权限包（角色）列表
export const getRoleList = (withPermissions = true) => {
  return request.get<OrgRoleVO[]>({ url: '/zentao/organization/role-list', params: { withPermissions } })
}

// 某权限包的权限明细
export const getRolePermissions = (roleId: number) => {
  return request.get<OrgPermissionVO[]>({ url: '/zentao/organization/role-permissions', params: { roleId } })
}

// 部门树
export const getDeptTree = () => {
  return request.get<OrgDeptVO[]>({ url: '/zentao/organization/dept-tree' })
}

// 禅道 → yudao 的映射对照表
export const getMapping = () => {
  return request.get<OrgMappingVO[]>({ url: '/zentao/organization/mapping' })
}
