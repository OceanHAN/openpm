// 模块树模块的常量
//
// 【禅道语义】zt_module 是通用树表，(root, type, branch) 定位一棵树：
//   type=story + root=产品id   → 需求模块
//   type=bug   + root=产品id   → 缺陷模块
//   type=task  + root=执行id   → 任务模块
//   type=case  + root=产品id   → 用例模块
//   type=line  + root=0        → 产品线（objectTables['productline'] = zt_module）
// path 是逗号格式且以逗号开头：一级模块 ',5,'，二级 ',5,6,'；grade 一级为 1。

/** 需要选「分支/平台」的树类型：只有产品视图的树带分支 */
export const BRANCH_AWARE_TYPES = ['story', 'bug', 'case']

/** 根对象是产品（而不是执行）的树类型 */
export const PRODUCT_ROOT_TYPES = ['story', 'bug', 'case']

export const isBranchAware = (type?: string) => BRANCH_AWARE_TYPES.includes(type || '')

export const isProductRoot = (type?: string) => PRODUCT_ROOT_TYPES.includes(type || '')
