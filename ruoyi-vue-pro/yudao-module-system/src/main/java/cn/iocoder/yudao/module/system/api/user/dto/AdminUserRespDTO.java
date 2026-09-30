package cn.iocoder.yudao.module.system.api.user.dto;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import lombok.Data;

import java.util.Set;

/**
 * Admin 用户 Response DTO
 *
 * @author 芋道源码
 */
@Data
public class AdminUserRespDTO {

    /**
     * 用户ID
     */
    private Long id;
    /**
     * 用户账号（登录名）
     *
     * 由 DSH 于 2026-09-13 补充：禅道侧一切人员引用（openedBy / assignedTo / 评审人）
     * 存的都是「账号」而非昵称，而 LoginUser 上下文里只有昵称，因此需要从这里取。
     * 映射由 AdminUserApiImpl 的 BeanUtils.toBean(AdminUserDO, ...) 自动完成。
     */
    private String username;
    /**
     * 用户昵称
     */
    private String nickname;
    /**
     * 帐号状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

    /**
     * 部门ID
     */
    private Long deptId;
    /**
     * 岗位编号数组
     */
    private Set<Long> postIds;
    /**
     * 手机号码
     */
    private String mobile;
    /**
     * 用户邮箱
     */
    private String email;
    /**
     * 用户性别
     */
    private Integer sex;
    /**
     * 用户头像
     */
    private String avatar;

}
