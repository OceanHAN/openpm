/**
 * OA 考勤模块的常量。
 *
 * 说明：上游 yudao-ui-admin-vue3 仓库里，考勤相关组件（attendance/list、attendance/my、
 * attendance/report）会 import 本文件，但我们这份 checkout 里该文件缺失，导致
 * `vite build` 报 `[UNLOADABLE_DEPENDENCY] Could not load src/views/oa/utils/constants`。
 * 这里按组件实际用到的成员补齐（类型 = 打卡方向，状态 = 打卡结果），
 * 数值与 yudao 的字典 oa_attendance_type / oa_attendance_status 保持一致。
 *
 * 注意：本文件与禅道（ZenTao）迁移无关，纯粹是为了让前端能构建出来。
 */

/** 打卡类型：上班 / 下班 */
export enum OA_ATTENDANCE_TYPE {
  /** 上班打卡 */
  CLOCK_IN = 1,
  /** 下班打卡 */
  CLOCK_OUT = 2
}

/** 打卡状态 */
export enum OA_ATTENDANCE_STATUS {
  /** 正常 */
  NORMAL = 1,
  /** 迟到 */
  LATE = 2,
  /** 早退 */
  EARLY = 3,
  /** 缺卡 */
  ABSENT = 4
}
