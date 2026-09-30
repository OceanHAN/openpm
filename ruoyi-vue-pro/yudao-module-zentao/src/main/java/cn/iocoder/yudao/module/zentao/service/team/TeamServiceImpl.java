package cn.iocoder.yudao.module.zentao.service.team;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.team.vo.TeamMemberSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.team.vo.TeamMemberRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.team.vo.TeamUpdateReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.team.TeamDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.team.TeamMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.execution.ExecutionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.team.TeamTypeEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 项目/执行团队 Service 实现
 *
 * <h3>三条从禅道抄来的规则</h3>
 * <ol>
 *   <li><b>全量保存是「先删后插」，但加入日期要保留</b>：
 *       {@code updateTeamMembers} 会 {@code delete from zt_team where root=? and type=?}
 *       再按提交的成员列表插入，同时把 {@code $oldJoin[$account]} 带过去
 *       （module/project/model.php:2029 与 project/tao.php#insertMember）；</li>
 *   <li><b>可用工时 = days × hours</b>（禅道 project/model.php:556 的 totalHours），
 *       hours 默认 7.0（config/execution.php: defaultWorkhours）；</li>
 *   <li><b>成员是物理增删</b>：本表没有 deleted 列，删掉就是删掉，
 *       否则重新添加同一个人会撞 UNIQUE(root,type,account)。</li>
 * </ol>
 */
@Slf4j
@Service
public class TeamServiceImpl implements TeamService {

    private static final String OBJECT_TYPE_TEAM = "team";

    /** 禅道默认每天工时 */
    public static final BigDecimal DEFAULT_HOURS = new BigDecimal("7.0");

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public List<TeamMemberRespVO> getMemberList(Long root, String type) {
        List<TeamDO> members = getMemberListDO(root, type);
        // 一次把姓名查回来（禅道业务表存的是账号）
        Map<String, String> nameMap = new LinkedHashMap<>();
        List<String> accounts = new ArrayList<>();
        for (TeamDO member : members) {
            accounts.add(member.getAccount());
        }
        for (AdminUserRespDTO user : adminUserApi.getUserListByUsernames(accounts)) {
            nameMap.put(user.getUsername(), user.getNickname());
        }
        List<TeamMemberRespVO> list = new ArrayList<>();
        for (TeamDO member : members) {
            TeamMemberRespVO vo = BeanUtils.toBean(member, TeamMemberRespVO.class);
            if (vo != null) {
                vo.setTotalHours(totalHours(member));
                vo.setRealname(nameMap.getOrDefault(member.getAccount(), member.getAccount()));
            }
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<TeamDO> getMemberListDO(Long root, String type) {
        if (root == null || root <= 0) {
            return List.of();
        }
        return teamMapper.selectListByRoot(root, normalizeType(type));
    }

    @Override
    public BigDecimal getTotalHours(Long root, String type) {
        BigDecimal total = BigDecimal.ZERO;
        for (TeamDO member : getMemberListDO(root, type)) {
            total = total.add(totalHours(member));
        }
        return total;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addMember(TeamMemberSaveReqVO reqVO) {
        String type = normalizeType(reqVO.getType());
        if (teamMapper.selectByRootAndAccount(reqVO.getRoot(), type, reqVO.getAccount()) != null) {
            throw exception(TEAM_MEMBER_EXISTS, reqVO.getAccount());
        }
        TeamDO member = buildMember(reqVO, type);
        teamMapper.insert(member);
        // zt_project 上的 teamCount/team 只是缓存，每次成员变动都要刷一遍，
        // 否则「读的时候现算 teamCount、team 却还是旧值」会出现两列不一致
        syncTeamInfo(reqVO.getRoot(), type);
        actionService.recordAction(OBJECT_TYPE_TEAM, reqVO.getRoot(), ActionTypeEnum.CREATED,
                "添加成员：" + member.getAccount());
        return member.getId();
    }

    @Override
    public void updateMember(TeamMemberSaveReqVO reqVO) {
        TeamDO old = validateMemberExists(reqVO.getId());
        // 角色/工时变了不影响人数，但保持一致更省心（同一个出口只管一件事）
        TeamDO updateObj = new TeamDO();
        updateObj.setId(old.getId());
        updateObj.setRole(reqVO.getRole());
        updateObj.setPosition(reqVO.getPosition());
        updateObj.setLimited(StringUtils.hasText(reqVO.getLimited()) ? reqVO.getLimited() : old.getLimited());
        updateObj.setDays(reqVO.getDays());
        updateObj.setHours(reqVO.getHours());
        if (reqVO.getJoin() != null) {
            updateObj.setJoin(reqVO.getJoin());
        }
        teamMapper.updateById(updateObj);
        syncTeamInfo(old.getRoot(), old.getType());
    }

    @Override
    public void removeMember(Long id) {
        TeamDO member = validateMemberExists(id);
        // 物理删除：本表没有 deleted 列（逻辑删除会撞 UNIQUE(root,type,account)）
        teamMapper.deleteById(id);
        syncTeamInfo(member.getRoot(), member.getType());
        actionService.recordAction(OBJECT_TYPE_TEAM, member.getRoot(), ActionTypeEnum.EDITED,
                "移除成员：" + member.getAccount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> updateMembers(TeamUpdateReqVO reqVO) {
        String type = normalizeType(reqVO.getType());
        Long root = reqVO.getRoot();
        // 1. 记下老成员的加入日期（禅道 $oldJoin）
        Map<String, LocalDate> oldJoin = new LinkedHashMap<>();
        for (TeamDO member : teamMapper.selectListByRoot(root, type)) {
            oldJoin.put(member.getAccount(), member.getJoin());
        }
        // 2. 先删后插
        teamMapper.deleteByRoot(root, type);

        List<String> accounts = new ArrayList<>();
        int order = 0;
        if (reqVO.getMembers() != null) {
            for (TeamUpdateReqVO.Member item : reqVO.getMembers()) {
                if (!StringUtils.hasText(item.getAccount())) {
                    continue;
                }
                TeamDO member = new TeamDO();
                member.setRoot(root);
                member.setType(type);
                member.setAccount(item.getAccount());
                member.setRole(item.getRole() == null ? "" : item.getRole());
                member.setPosition("");
                member.setLimited(StringUtils.hasText(item.getLimited()) ? item.getLimited() : "no");
                member.setJoin(oldJoin.getOrDefault(item.getAccount(), LocalDate.now()));
                member.setDays(item.getDays() == null ? 0 : item.getDays());
                member.setHours(item.getHours() == null ? DEFAULT_HOURS : item.getHours());
                member.setEstimate(BigDecimal.ZERO);
                member.setConsumed(BigDecimal.ZERO);
                member.setLeft(BigDecimal.ZERO);
                member.setOrder(order++);
                teamMapper.insert(member);
                accounts.add(member.getAccount());
            }
        }
        syncTeamInfo(root, type);
        actionService.recordAction(OBJECT_TYPE_TEAM, root, ActionTypeEnum.EDITED,
                "保存团队成员：" + accounts.size() + " 人");
        return accounts;
    }

    @Override
    public List<String> getMemberAccounts(Long root, String type) {
        List<String> accounts = new ArrayList<>();
        for (TeamDO member : getMemberListDO(root, type)) {
            accounts.add(member.getAccount());
        }
        return accounts;
    }

    @Override
    public Map<Long, Integer> countByRoots(List<Long> roots, String type) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (roots == null || roots.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : teamMapper.countByRoots(roots, normalizeType(type))) {
            Object root = row.get("root");
            Object cnt = row.get("cnt");
            if (root != null && cnt != null) {
                result.put(Long.valueOf(String.valueOf(root)), Integer.valueOf(String.valueOf(cnt)));
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncByAccounts(Long root, String type, List<String> accounts, String defaultRole) {
        Set<String> wanted = new LinkedHashSet<>();
        if (accounts != null) {
            for (String account : accounts) {
                if (StringUtils.hasText(account)) {
                    wanted.add(account.trim());
                }
            }
        }
        // 已有成员（保留角色/天数/工时/加入日期）
        Map<String, TeamDO> existing = new LinkedHashMap<>();
        for (TeamDO member : teamMapper.selectListByRoot(root, normalizeType(type))) {
            existing.put(member.getAccount(), member);
        }
        // 需要保留的 = 交集；新增的用默认值；不在列表里的删掉
        List<TeamDO> keep = new ArrayList<>();
        for (String account : wanted) {
            keep.add(existing.get(account));
        }
        teamMapper.deleteByRoot(root, normalizeType(type));
        int order = 0;
        for (String account : wanted) {
            TeamDO old = existing.get(account);
            TeamDO member = old == null ? new TeamDO() : old;
            member.setId(null);
            member.setRoot(root);
            member.setType(normalizeType(type));
            member.setAccount(account);
            if (old == null) {
                member.setRole(defaultRole == null ? "" : defaultRole);
                member.setPosition("");
                member.setLimited("no");
                member.setJoin(LocalDate.now());
                member.setDays(0);
                member.setHours(DEFAULT_HOURS);
                member.setEstimate(BigDecimal.ZERO);
                member.setConsumed(BigDecimal.ZERO);
                member.setLeft(BigDecimal.ZERO);
            }
            member.setOrder(order++);
            teamMapper.insert(member);
        }
    }

    @Override
    public void syncOwners(cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO row) {
        if (row == null || row.getId() == null) {
            return;
        }
        // 注意：执行的 type 是 sprint/stage/kanban，不是字面量 "execution"（踩过：写成
        // "execution".equals(row.getType()) 会导致执行永远进不来，成员一条都不建）
        String type;
        if (ExecutionTypeEnum.isExecution(row.getType())) {
            type = TeamTypeEnum.EXECUTION.getType();
        } else if (ExecutionTypeEnum.PROJECT.getType().equals(row.getType())) {
            type = TeamTypeEnum.PROJECT.getType();
        } else {
            return; // 项目集等其它角色不参与团队
        }
        // 负责人字段 -> 角色名（禅道用 lang->execution->$ownerField 做角色）
        Map<String, String> owners = new LinkedHashMap<>();
        putOwner(owners, row.getPO(), "产品负责人");
        putOwner(owners, row.getPM(), "项目经理");
        putOwner(owners, row.getQD(), "测试负责人");
        putOwner(owners, row.getRD(), "研发负责人");

        int order = teamMapper.selectListByRoot(row.getId(), type).size();
        for (Map.Entry<String, String> entry : owners.entrySet()) {
            TeamDO existing = teamMapper.selectByRootAndAccount(row.getId(), type, entry.getKey());
            if (existing != null) {
                // 角色变了就更新（负责人换人时这里是新增，角色变化时是更新）
                if (!entry.getValue().equals(existing.getRole())) {
                    TeamDO update = new TeamDO();
                    update.setId(existing.getId());
                    update.setRole(entry.getValue());
                    teamMapper.updateById(update);
                }
                continue;
            }
            TeamDO member = new TeamDO();
            member.setRoot(row.getId());
            member.setType(type);
            member.setAccount(entry.getKey());
            member.setRole(entry.getValue());
            member.setPosition("");
            member.setLimited("no");
            member.setJoin(LocalDate.now());
            member.setDays(row.getDays() == null ? 0 : row.getDays());
            member.setHours(DEFAULT_HOURS);
            member.setEstimate(BigDecimal.ZERO);
            member.setConsumed(BigDecimal.ZERO);
            member.setLeft(BigDecimal.ZERO);
            member.setOrder(order++);
            teamMapper.insert(member);
        }
        syncTeamInfo(row.getId(), type);
    }

    @Override
    public void syncTeamInfo(Long root, String type) {
        if (root == null || root <= 0) {
            return;
        }
        List<String> accounts = getMemberAccounts(root, type);
        ProjectDO update = new ProjectDO();
        update.setId(root);
        update.setTeamCount(accounts.size());
        // team 列是禅道的历史遗留（逗号串），同步过去让旧的展示/兼容逻辑不至于失真
        update.setTeam(String.join(",", accounts));
        projectMapper.updateById(update);
    }

    @Override
    public void removeAll(Long root, String type) {
        teamMapper.deleteByRoot(root, normalizeType(type));
    }

    // ==================== 内部 ====================

    private void putOwner(Map<String, String> owners, String account, String role) {
        if (StringUtils.hasText(account) && !owners.containsKey(account)) {
            owners.put(account, role);
        }
    }

    private TeamDO buildMember(TeamMemberSaveReqVO reqVO, String type) {
        TeamDO member = BeanUtils.toBean(reqVO, TeamDO.class);
        member.setId(null);
        member.setType(type);
        member.setRole(reqVO.getRole() == null ? "" : reqVO.getRole());
        member.setPosition(reqVO.getPosition() == null ? "" : reqVO.getPosition());
        member.setLimited(StringUtils.hasText(reqVO.getLimited()) ? reqVO.getLimited() : "no");
        member.setJoin(reqVO.getJoin() == null ? LocalDate.now() : reqVO.getJoin());
        member.setDays(reqVO.getDays() == null ? 0 : reqVO.getDays());
        member.setHours(reqVO.getHours() == null ? DEFAULT_HOURS : reqVO.getHours());
        member.setEstimate(BigDecimal.ZERO);
        member.setConsumed(BigDecimal.ZERO);
        member.setLeft(BigDecimal.ZERO);
        member.setOrder(teamMapper.selectListByRoot(reqVO.getRoot(), type).size());
        return member;
    }

    /** 可用工时 = 天数 × 每天小时数（禅道 getTeamMembers 里的 totalHours） */
    private BigDecimal totalHours(TeamDO member) {
        BigDecimal days = BigDecimal.valueOf(member.getDays() == null ? 0 : member.getDays());
        BigDecimal hours = member.getHours() == null ? BigDecimal.ZERO : member.getHours();
        return days.multiply(hours);
    }

    private TeamDO validateMemberExists(Long id) {
        TeamDO member = id == null ? null : teamMapper.selectById(id);
        if (member == null) {
            throw exception(TEAM_MEMBER_NOT_EXISTS);
        }
        return member;
    }

    private String normalizeType(String type) {
        if (!TeamTypeEnum.isValid(type)) {
            throw exception(TEAM_TYPE_INVALID, type);
        }
        return type;
    }

}
