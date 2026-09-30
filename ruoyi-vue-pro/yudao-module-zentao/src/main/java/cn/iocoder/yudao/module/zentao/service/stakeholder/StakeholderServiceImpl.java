package cn.iocoder.yudao.module.zentao.service.stakeholder;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.stakeholder.vo.StakeholderRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.stakeholder.vo.StakeholderSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.stakeholder.StakeholderDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.stakeholder.StakeholderMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.stakeholder.StakeholderFromEnum;
import cn.iocoder.yudao.module.zentao.enums.stakeholder.StakeholderTypeEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 干系人 Service 实现
 *
 * <h3>三条对齐禅道的规则</h3>
 * <ol>
 *   <li><b>type 由 from 推导</b>：{@code from='outside' → type='outside'}，其余 → inside
 *       （module/stakeholder/model.php#create）；</li>
 *   <li><b>同一个人不能重复加到同一个对象下</b>：禅道的 check 是
 *       {@code user unique(objectID = ? AND deleted = '0')}；</li>
 *   <li><b>删除是按「对象 + 账号」</b>的（禅道 {@code delete(userID)}），
 *       所以接口同时提供按记录编号和按账号两种删法。</li>
 * </ol>
 *
 * <p>本实现简化了 {@code from='outside'} 的处理：禅道会在 {@code zt_user} 里
 * 建一条 {@code type='outside'} 的外部用户记录，这里直接把名字存在 {@code user} 列里
 * （见 README 已知限制）。
 */
@Slf4j
@Service
public class StakeholderServiceImpl implements StakeholderService {

    private static final String OBJECT_TYPE_STAKEHOLDER = "stakeholder";

    /** 允许挂干系人的对象类型：项目集 / 项目 */
    private static final List<String> ALLOWED_OBJECT_TYPES = List.of("program", "project");

    @Resource
    private StakeholderMapper stakeholderMapper;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStakeholder(StakeholderSaveReqVO createReqVO) {
        validateObjectType(createReqVO.getObjectType());
        String from = validateFrom(createReqVO.getFrom());
        String user = createReqVO.getUser().trim();
        if (stakeholderMapper.selectByObjectAndUser(createReqVO.getObjectType(), createReqVO.getObjectID(), user) != null) {
            throw exception(STAKEHOLDER_USER_EXISTS, user);
        }

        StakeholderDO stakeholder = new StakeholderDO();
        stakeholder.setObjectType(createReqVO.getObjectType());
        stakeholder.setObjectID(createReqVO.getObjectID());
        stakeholder.setUser(user);
        stakeholder.setFrom(from);
        // type 由 from 推导（禅道 model.php#create）
        stakeholder.setType(StakeholderFromEnum.OUTSIDE.getFrom().equals(from)
                ? StakeholderTypeEnum.OUTSIDE.getType() : StakeholderTypeEnum.INSIDE.getType());
        stakeholder.setKey(createReqVO.getKey() == null ? 0 : createReqVO.getKey());
        String operator = currentAccount();
        stakeholder.setCreatedBy(operator);
        stakeholder.setCreatedDate(LocalDateTime.now());
        stakeholder.setEditedBy("");
        stakeholderMapper.insert(stakeholder);

        actionService.recordAction(OBJECT_TYPE_STAKEHOLDER, createReqVO.getObjectID(), ActionTypeEnum.CREATED,
                "添加干系人：" + user + (stakeholder.getKey() == 1 ? "（关键）" : ""));
        return stakeholder.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchCreate(String objectType, Long objectID, String from, List<String> users) {
        List<Long> ids = new ArrayList<>();
        if (users == null) {
            return ids;
        }
        for (String user : new LinkedHashSet<>(users)) {
            if (!StringUtils.hasText(user)) {
                continue;
            }
            StakeholderSaveReqVO reqVO = new StakeholderSaveReqVO();
            reqVO.setObjectType(objectType);
            reqVO.setObjectID(objectID);
            reqVO.setFrom(from);
            reqVO.setUser(user.trim());
            reqVO.setKey(0);
            // 已经加过的直接跳过（禅道 batchCreate 也是这个行为），不报错
            if (stakeholderMapper.selectByObjectAndUser(objectType, objectID, user.trim()) != null) {
                continue;
            }
            ids.add(createStakeholder(reqVO));
        }
        return ids;
    }

    @Override
    public void updateStakeholder(StakeholderSaveReqVO updateReqVO) {
        StakeholderDO old = validateStakeholderExists(updateReqVO.getId());
        String from = validateFrom(updateReqVO.getFrom());
        StakeholderDO updateObj = new StakeholderDO();
        updateObj.setId(old.getId());
        updateObj.setFrom(from);
        updateObj.setType(StakeholderFromEnum.OUTSIDE.getFrom().equals(from)
                ? StakeholderTypeEnum.OUTSIDE.getType() : StakeholderTypeEnum.INSIDE.getType());
        updateObj.setKey(updateReqVO.getKey() == null ? old.getKey() : updateReqVO.getKey());
        updateObj.setEditedBy(currentAccount());
        updateObj.setEditedDate(LocalDateTime.now());
        stakeholderMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_STAKEHOLDER, old.getObjectID(),
                ActionTypeEnum.EDITED, null, old, updateObj);
    }

    @Override
    public void deleteStakeholder(Long id) {
        StakeholderDO stakeholder = validateStakeholderExists(id);
        stakeholderMapper.deleteById(id);
        actionService.recordAction(OBJECT_TYPE_STAKEHOLDER, stakeholder.getObjectID(), ActionTypeEnum.EDITED,
                "移除干系人：" + stakeholder.getUser());
    }

    @Override
    public void deleteByObjectAndUser(String objectType, Long objectID, String user) {
        StakeholderDO stakeholder = stakeholderMapper.selectByObjectAndUser(objectType, objectID, user);
        if (stakeholder == null) {
            throw exception(STAKEHOLDER_NOT_EXISTS);
        }
        deleteStakeholder(stakeholder.getId());
    }

    @Override
    public StakeholderDO getStakeholder(Long id) {
        return validateStakeholderExists(id);
    }

    @Override
    public StakeholderRespVO getStakeholderVO(Long id) {
        return toRespVOList(List.of(validateStakeholderExists(id))).get(0);
    }

    @Override
    public List<StakeholderRespVO> getStakeholderList(String objectType, Long objectID) {
        List<StakeholderDO> list = objectID == null || objectID <= 0
                ? List.of() : stakeholderMapper.selectListByObject(objectType, objectID);
        return toRespVOList(list);
    }

    @Override
    public Map<Long, List<StakeholderDO>> getStakeholderMap(String objectType, List<Long> objectIds) {
        Map<Long, List<StakeholderDO>> map = new LinkedHashMap<>();
        for (StakeholderDO item : stakeholderMapper.selectListByObjects(objectType, objectIds)) {
            map.computeIfAbsent(item.getObjectID(), k -> new ArrayList<>()).add(item);
        }
        return map;
    }

    @Override
    public List<Long> getObjectIdsByUser(String objectType, String user) {
        List<Long> ids = new ArrayList<>();
        for (StakeholderDO item : stakeholderMapper.selectListByUser(objectType, user)) {
            if (!ids.contains(item.getObjectID())) {
                ids.add(item.getObjectID());
            }
        }
        return ids;
    }

    // ==================== 内部 ====================

    private List<StakeholderRespVO> toRespVOList(List<StakeholderDO> list) {
        // 内部人员批量回填姓名；外部人员（from=outside）直接显示存下来的名字
        List<String> accounts = new ArrayList<>();
        for (StakeholderDO item : list) {
            if (StakeholderTypeEnum.INSIDE.getType().equals(item.getType())) {
                accounts.add(item.getUser());
            }
        }
        Map<String, String> names = new LinkedHashMap<>();
        for (AdminUserRespDTO user : adminUserApi.getUserListByUsernames(accounts)) {
            names.put(user.getUsername(), user.getNickname());
        }
        List<StakeholderRespVO> result = new ArrayList<>();
        for (StakeholderDO item : list) {
            StakeholderRespVO vo = BeanUtils.toBean(item, StakeholderRespVO.class);
            if (vo == null) {
                continue;
            }
            StakeholderTypeEnum type = StakeholderTypeEnum.of(item.getType());
            StakeholderFromEnum from = StakeholderFromEnum.of(item.getFrom());
            vo.setTypeName(type == null ? item.getType() : type.getName());
            vo.setFromName(from == null ? item.getFrom() : from.getName());
            vo.setRealname(names.getOrDefault(item.getUser(), item.getUser()));
            result.add(vo);
        }
        return result;
    }

    private StakeholderDO validateStakeholderExists(Long id) {
        StakeholderDO stakeholder = id == null ? null : stakeholderMapper.selectById(id);
        if (stakeholder == null) {
            throw exception(STAKEHOLDER_NOT_EXISTS);
        }
        return stakeholder;
    }

    private void validateObjectType(String objectType) {
        if (!ALLOWED_OBJECT_TYPES.contains(objectType)) {
            throw exception(STAKEHOLDER_OBJECT_TYPE_INVALID, objectType);
        }
    }

    private String validateFrom(String from) {
        if (!StakeholderFromEnum.isValid(from)) {
            throw exception(STAKEHOLDER_FROM_INVALID, from);
        }
        return from;
    }

    private String currentAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : "";
    }

}
