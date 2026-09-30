package cn.iocoder.yudao.module.zentao.service.team;

import cn.iocoder.yudao.module.zentao.controller.admin.team.vo.TeamMemberSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.team.vo.TeamMemberRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.team.vo.TeamUpdateReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.team.TeamDO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 项目/执行团队 Service
 *
 * 对应禅道 {@code module/project/model.php} 的 addTeamMembers / updateTeamMembers / getTeamMembers
 * 与 {@code module/execution/model.php} 的成员同步。
 */
public interface TeamService {

    /** 成员列表（带姓名与可用工时 = 天数 × 每天小时数） */
    List<TeamMemberRespVO> getMemberList(Long root, String type);

    List<TeamDO> getMemberListDO(Long root, String type);

    BigDecimal getTotalHours(Long root, String type);

    Long addMember(TeamMemberSaveReqVO reqVO);

    void updateMember(TeamMemberSaveReqVO reqVO);

    void removeMember(Long id);

    /**
     * 全量保存成员（禅道 updateTeamMembers）：先删光再插入，
     * **老成员的加入日期要保留**（否则每次保存都会把「加入日期」刷成今天）
     */
    List<String> updateMembers(TeamUpdateReqVO reqVO);

    /** 成员账号列表（逗号串形式，同步到 zt_project.team） */
    List<String> getMemberAccounts(Long root, String type);

    /**
     * 批量填充「团队人数」（项目列表用）：
     * 禅道是 {@code SELECT root, COUNT(1) FROM zt_team JOIN zt_user ... GROUP BY root}
     */
    java.util.Map<Long, Integer> countByRoots(List<Long> roots, String type);

    /**
     * 把账号列表同步进团队（项目表单里的「团队成员」多选）：
     * 已有成员保留角色与工时，新增的用默认值，不在列表里的删除
     */
    void syncByAccounts(Long root, String type, List<String> accounts, String defaultRole);

    /**
     * 把执行/项目的负责人（PO/PM/QD/RD）补进团队。
     * 禅道 module/execution/model.php:598 就是这么做的：只补不删，
     * 手工加进来的成员不受影响
     */
    void syncOwners(cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO row);

    /**
     * 把团队成员数/账号串回写到 zt_project（teamCount / team 两列）。
     * 禅道是在读取时现算 teamCount 的，本实现把这两列当成**缓存**同步，
     * 这样单条 get 与项目集下的统计都不用额外查询
     */
    void syncTeamInfo(Long root, String type);

    /** 删除某个对象下的全部成员（删项目/执行时调用） */
    void removeAll(Long root, String type);
}
