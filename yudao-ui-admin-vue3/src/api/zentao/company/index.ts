import request from '@/config/axios'

// 公司信息（禅道 module/company，界面上叫「组织视图」）
//
// 三件事对应的实现：
//   公司信息 view/edit  → 本文件（zt_company）
//   组织成员 browse     → @/api/zentao/organization 的 getUserList / getDeptTree（不重复实现）
//   组织动态 dynamic    → @/api/zentao/action 的 getActionDynamic（不重复实现）
export interface CompanyVO {
  id?: number
  name: string
  phone?: string
  fax?: string
  address?: string
  zipcode?: string
  website?: string
  backyard?: string
  /** 是否允许匿名登录（yudao 无此机制，仅存放） */
  guest?: number
  /** 管理员账号逗号串，如 ,admin, —— 只读，禅道的公司编辑表单里也没有这一项 */
  admins?: string
}

/** 外部公司下拉项：字段名是禅道 ajaxGetOutsideCompany 的原样输出 */
export interface CompanyOptionVO {
  text: string
  value: number
  keys: string
}

/** 超管口径对照：禅道看 zt_company.admins，yudao 看 super_admin 角色 */
export interface CompanyAdminsVO {
  zentaoAdmins: string[]
  yudaoSuperAdmins: string[]
  matched: string[]
  onlyInZentao: string[]
  onlyInYudao: string[]
  note: string
}

export const getCompanyPage = (params: any) => {
  return request.get({ url: '/zentao/company/page', params })
}

export const getCompanyList = () => {
  return request.get<CompanyVO[]>({ url: '/zentao/company/list' })
}

export const getCompany = (id: number) => {
  return request.get<CompanyVO>({ url: '/zentao/company/get', params: { id } })
}

/** 禅道 company::getFirst()：id 最小的一条就是「本公司」 */
export const getFirstCompany = () => {
  return request.get<CompanyVO>({ url: '/zentao/company/get-first' })
}

/** 外部公司（id != 1），给「外部干系人的所属公司」下拉用 */
export const getOutsideList = () => {
  return request.get<CompanyOptionVO[]>({ url: '/zentao/company/outside-list' })
}

export const createCompany = (data: CompanyVO) => {
  return request.post({ url: '/zentao/company/create', data })
}

export const updateCompany = (data: CompanyVO) => {
  return request.put({ url: '/zentao/company/update', data })
}

export const getCompanyAdmins = () => {
  return request.get<CompanyAdminsVO>({ url: '/zentao/company/admins' })
}
