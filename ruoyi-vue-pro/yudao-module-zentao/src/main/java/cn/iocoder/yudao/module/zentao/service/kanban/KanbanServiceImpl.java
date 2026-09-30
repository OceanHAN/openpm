package cn.iocoder.yudao.module.zentao.service.kanban;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.kanban.vo.*;
import cn.iocoder.yudao.module.zentao.dal.dataobject.kanban.*;
import cn.iocoder.yudao.module.zentao.dal.mysql.kanban.*;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.kanban.KanbanCardStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.kanban.KanbanLaneTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.kanban.KanbanSpaceTypeEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import cn.iocoder.yudao.module.zentao.service.action.ActionServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 看板 Service 实现。
 *
 * <p>业务规则来源：禅道 {@code module/kanban/model.php} + {@code config.php}。
 *
 * <h3>七层聚合</h3>
 * <pre>
 *   空间 zt_kanbanspace
 *     └── 看板 zt_kanban
 *           └── 区域 zt_kanbanregion
 *                 └── 分组 zt_kanbangroup
 *                       ├── 泳道 zt_kanbanlane（横向）
 *                       └── 列   zt_kanbancolumn（纵向，limit = WIP）
 *                             卡片在板上的位置 = zt_kanbancell.cards（泳道 × 列的逗号列表）
 * </pre>
 *
 * <h3>照抄禅道的几条规则</h3>
 * <ol>
 *   <li><b>默认布局</b>：建看板/建区域时自动建 1 个分组 + 「默认泳道」+ 四个默认列
 *       （未开始/进行中/已完成/已关闭，WIP 不限），并给每个 泳道×列 建好格子</li>
 *   <li><b>列的在制品上限</b>：只允许 -1（不限）或正整数；子列之和不能超过父列限额，
 *       父列有限额时子列也不能「不限」</li>
 *   <li><b>卡片移动</b>：先按「同类型泳道」把卡片从该区域的所有格子里摘掉，再追加到目标格子，
 *       并把 card.group 改成目标泳道的分组</li>
 *   <li><b>WIP 超限不阻止</b>：禅道只在界面把 (卡片数/限额) 标红，所以这里只返回 overWip</li>
 * </ol>
 *
 * <h3>有意偏离</h3>
 * <ul>
 *   <li>删看板：禅道只软删看板本体（区域/泳道/列/卡片全部变成孤儿），本实现**级联软删**</li>
 *   <li>删卡片：禅道物理删卡片行但**不清格子里的编号**（界面上看不出来，因为卡片是重新查的）；
 *       本实现顺手把卡片从号列表里摘掉，不留悬空编号</li>
 *   <li>删空间：禅道不做检查，本实现沿用「还有子看板就拒绝」的统一策略</li>
 * </ul>
 */
@Slf4j
@Service
public class KanbanServiceImpl implements KanbanService {

    /** 默认泳道（禅道 {@code $lang->kanbanlane->default}） */
    private static final String DEFAULT_LANE_NAME = "默认泳道";
    private static final String DEFAULT_LANE_COLOR = "#7ec5ff";

    /** 默认列（禅道 {@code $lang->kanban->defaultColumn}）：未开始 / 进行中 / 已完成 / 已关闭 */
    private static final List<String> DEFAULT_COLUMNS = Arrays.asList("未开始", "进行中", "已完成", "已关闭");
    private static final String DEFAULT_COLUMN_COLOR = "#333";

    /** WIP 不限 */
    private static final int LIMIT_UNLIMITED = -1;

    @Resource
    private KanbanSpaceMapper spaceMapper;

    @Resource
    private KanbanMapper kanbanMapper;

    @Resource
    private KanbanRegionMapper regionMapper;

    @Resource
    private KanbanGroupMapper groupMapper;

    @Resource
    private KanbanLaneMapper laneMapper;

    @Resource
    private KanbanColumnMapper columnMapper;

    @Resource
    private KanbanCardMapper cardMapper;

    @Resource
    private KanbanCellMapper cellMapper;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ================================================================
    // 空间
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSpace(KanbanSpaceSaveReqVO reqVO) {
        String type = StringUtils.hasText(reqVO.getType()) ? reqVO.getType() : KanbanSpaceTypeEnum.PRIVATE.getType();
        if (KanbanSpaceTypeEnum.of(type) == null) {
            throw exception(KANBAN_SPACE_TYPE_INVALID, type);
        }
        KanbanSpaceDO existed = spaceMapper.selectByName(reqVO.getName());
        if (existed != null) {
            throw exception(KANBAN_SPACE_NAME_DUPLICATE, reqVO.getName());
        }
        String account = currentAccount();
        KanbanSpaceDO space = new KanbanSpaceDO();
        space.setName(reqVO.getName());
        space.setType(type);
        // 私人空间的负责人恒为创建人（禅道 createSpace 的 type=private 分支）
        space.setOwner(KanbanSpaceTypeEnum.PRIVATE.getType().equals(type) ? account
                : (StringUtils.hasText(reqVO.getOwner()) ? reqVO.getOwner() : account));
        space.setTeam(StringUtils.hasText(reqVO.getTeam()) ? reqVO.getTeam() : account);
        space.setDesc(reqVO.getDesc() == null ? "" : reqVO.getDesc());
        space.setAcl(StringUtils.hasText(reqVO.getAcl()) ? reqVO.getAcl() : "open");
        space.setWhitelist(reqVO.getWhitelist() == null ? "" : reqVO.getWhitelist());
        space.setStatus("active");
        space.setOrder(reqVO.getOrder() == null ? 0 : reqVO.getOrder());
        space.setCreatedBy(account);
        space.setCreatedDate(LocalDateTime.now());
        space.setLastEditedBy(account);
        space.setLastEditedDate(LocalDateTime.now());
        spaceMapper.insert(space);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_SPACE, space.getId(),
                ActionTypeEnum.CREATED, "新建看板空间：" + space.getName());
        return space.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpace(KanbanSpaceSaveReqVO reqVO) {
        KanbanSpaceDO old = validateSpaceExists(reqVO.getId());
        if (StringUtils.hasText(reqVO.getType()) && KanbanSpaceTypeEnum.of(reqVO.getType()) == null) {
            throw exception(KANBAN_SPACE_TYPE_INVALID, reqVO.getType());
        }
        KanbanSpaceDO existed = spaceMapper.selectByName(reqVO.getName());
        if (existed != null && !Objects.equals(existed.getId(), old.getId())) {
            throw exception(KANBAN_SPACE_NAME_DUPLICATE, reqVO.getName());
        }
        KanbanSpaceDO updateObj = new KanbanSpaceDO();
        updateObj.setId(old.getId());
        updateObj.setName(reqVO.getName());
        updateObj.setType(reqVO.getType());
        updateObj.setOwner(reqVO.getOwner());
        updateObj.setTeam(reqVO.getTeam());
        updateObj.setDesc(reqVO.getDesc());
        updateObj.setAcl(reqVO.getAcl());
        updateObj.setWhitelist(reqVO.getWhitelist());
        updateObj.setOrder(reqVO.getOrder());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        spaceMapper.updateById(updateObj);
        actionService.recordActionWithChanges(ActionServiceImpl.OBJECT_TYPE_KANBAN_SPACE, old.getId(),
                ActionTypeEnum.EDITED, "修改看板空间：" + reqVO.getName(), old, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSpace(Long id) {
        KanbanSpaceDO space = validateSpaceExists(id);
        long count = kanbanMapper.countBySpace(id);
        if (count > 0) {
            throw exception(KANBAN_SPACE_HAS_KANBAN, count);
        }
        spaceMapper.deleteById(id);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_SPACE, id,
                ActionTypeEnum.DELETED, "删除看板空间：" + space.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateSpace(Long id) {
        KanbanSpaceDO space = validateSpaceExists(id);
        KanbanSpaceDO updateObj = new KanbanSpaceDO();
        updateObj.setId(id);
        updateObj.setStatus("active");
        updateObj.setActivatedBy(currentAccount());
        updateObj.setActivatedDate(LocalDateTime.now());
        spaceMapper.updateById(updateObj);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_SPACE, id,
                ActionTypeEnum.ACTIVATED, "激活看板空间：" + space.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeSpace(Long id) {
        KanbanSpaceDO space = validateSpaceExists(id);
        KanbanSpaceDO updateObj = new KanbanSpaceDO();
        updateObj.setId(id);
        updateObj.setStatus("closed");
        updateObj.setClosedBy(currentAccount());
        updateObj.setClosedDate(LocalDateTime.now());
        spaceMapper.updateById(updateObj);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_SPACE, id,
                ActionTypeEnum.CLOSED, "关闭看板空间：" + space.getName());
    }

    @Override
    public KanbanSpaceDO validateSpaceExists(Long id) {
        KanbanSpaceDO space = id == null ? null : spaceMapper.selectById(id);
        if (space == null) {
            throw exception(KANBAN_SPACE_NOT_EXISTS, id);
        }
        return space;
    }

    @Override
    public KanbanSpaceRespVO getSpace(Long id) {
        return convertSpace(validateSpaceExists(id));
    }

    @Override
    public PageResult<KanbanSpaceRespVO> getSpacePage(KanbanSpacePageReqVO reqVO) {
        PageResult<KanbanSpaceDO> page = spaceMapper.selectPage(reqVO.getName(), reqVO.getType(), reqVO.getStatus(), reqVO);
        List<KanbanSpaceRespVO> list = new ArrayList<>(page.getList().size());
        for (KanbanSpaceDO space : page.getList()) {
            list.add(convertSpace(space));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public List<KanbanSpaceRespVO> getSpaceList(String type) {
        List<KanbanSpaceRespVO> list = new ArrayList<>();
        for (KanbanSpaceDO space : spaceMapper.selectListByType(type)) {
            list.add(convertSpace(space));
        }
        return list;
    }

    // ================================================================
    // 看板
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createKanban(KanbanSaveReqVO reqVO) {
        KanbanSpaceDO space = validateSpaceExists(reqVO.getSpace());
        KanbanDO existed = kanbanMapper.selectBySpaceAndName(reqVO.getSpace(), reqVO.getName());
        if (existed != null) {
            throw exception(KANBAN_NAME_DUPLICATE, reqVO.getName());
        }
        String account = currentAccount();
        KanbanDO kanban = new KanbanDO();
        kanban.setSpace(space.getId());
        kanban.setName(reqVO.getName());
        // 私人空间里的看板归创建人（禅道 create 里的 space->type == 'private' 分支）
        kanban.setOwner(KanbanSpaceTypeEnum.PRIVATE.getType().equals(space.getType()) ? account
                : (StringUtils.hasText(reqVO.getOwner()) ? reqVO.getOwner() : account));
        kanban.setTeam(StringUtils.hasText(reqVO.getTeam()) ? reqVO.getTeam() : account);
        kanban.setDesc(reqVO.getDesc() == null ? "" : reqVO.getDesc());
        kanban.setAcl(StringUtils.hasText(reqVO.getAcl()) ? reqVO.getAcl() : "open");
        kanban.setWhitelist(reqVO.getWhitelist() == null ? "" : reqVO.getWhitelist());
        kanban.setArchived(reqVO.getArchived() == null ? 1 : reqVO.getArchived());
        kanban.setPerformable(reqVO.getPerformable() == null ? 0 : reqVO.getPerformable());
        kanban.setStatus("active");
        kanban.setOrder(reqVO.getOrder() == null ? 0 : reqVO.getOrder());
        kanban.setDisplayCards(reqVO.getDisplayCards() == null ? 0 : reqVO.getDisplayCards());
        kanban.setShowWIP(reqVO.getShowWIP() == null ? 1 : reqVO.getShowWIP());
        kanban.setFluidBoard(reqVO.getFluidBoard() == null ? 0 : reqVO.getFluidBoard());
        kanban.setColWidth(reqVO.getColWidth() == null ? 264 : reqVO.getColWidth());
        kanban.setMinColWidth(reqVO.getMinColWidth() == null ? 200 : reqVO.getMinColWidth());
        kanban.setMaxColWidth(reqVO.getMaxColWidth() == null ? 384 : reqVO.getMaxColWidth());
        kanban.setObject(reqVO.getObject() == null ? "" : reqVO.getObject());
        kanban.setAlignment(StringUtils.hasText(reqVO.getAlignment()) ? reqVO.getAlignment() : "center");
        kanban.setCreatedBy(account);
        kanban.setCreatedDate(LocalDateTime.now());
        kanban.setLastEditedBy(account);
        kanban.setLastEditedDate(LocalDateTime.now());
        kanbanMapper.insert(kanban);

        // ★ 默认布局：默认区域（含分组 + 默认泳道 + 四个默认列 + 所有格子）
        createRegionInternal(kanban, "默认区域", 1);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN, kanban.getId(),
                ActionTypeEnum.CREATED, "新建看板：" + kanban.getName());
        return kanban.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKanban(KanbanSaveReqVO reqVO) {
        KanbanDO old = validateKanbanExists(reqVO.getId());
        KanbanDO existed = kanbanMapper.selectBySpaceAndName(reqVO.getSpace(), reqVO.getName());
        if (existed != null && !Objects.equals(existed.getId(), old.getId())) {
            throw exception(KANBAN_NAME_DUPLICATE, reqVO.getName());
        }
        KanbanDO updateObj = new KanbanDO();
        updateObj.setId(old.getId());
        updateObj.setSpace(reqVO.getSpace());
        updateObj.setName(reqVO.getName());
        updateObj.setOwner(reqVO.getOwner());
        updateObj.setTeam(reqVO.getTeam());
        updateObj.setDesc(reqVO.getDesc());
        updateObj.setAcl(reqVO.getAcl());
        updateObj.setWhitelist(reqVO.getWhitelist());
        updateObj.setArchived(reqVO.getArchived());
        updateObj.setPerformable(reqVO.getPerformable());
        updateObj.setObject(reqVO.getObject());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        kanbanMapper.updateById(updateObj);
        actionService.recordActionWithChanges(ActionServiceImpl.OBJECT_TYPE_KANBAN, old.getId(),
                ActionTypeEnum.EDITED, "修改看板：" + reqVO.getName(), old, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKanbanSetting(KanbanSaveReqVO reqVO) {
        KanbanDO old = validateKanbanExists(reqVO.getId());
        KanbanDO updateObj = new KanbanDO();
        updateObj.setId(old.getId());
        updateObj.setDisplayCards(reqVO.getDisplayCards());
        updateObj.setShowWIP(reqVO.getShowWIP());
        updateObj.setFluidBoard(reqVO.getFluidBoard());
        updateObj.setColWidth(reqVO.getColWidth());
        updateObj.setMinColWidth(reqVO.getMinColWidth());
        updateObj.setMaxColWidth(reqVO.getMaxColWidth());
        updateObj.setAlignment(reqVO.getAlignment());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        kanbanMapper.updateById(updateObj);
        actionService.recordActionWithChanges(ActionServiceImpl.OBJECT_TYPE_KANBAN, old.getId(),
                ActionTypeEnum.EDITED, "修改看板设置：" + old.getName(), old, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateKanban(Long id) {
        KanbanDO kanban = validateKanbanExists(id);
        if ("active".equals(kanban.getStatus())) {
            throw exception(KANBAN_NOT_CLOSED_CANNOT_ACTIVATE);
        }
        KanbanDO updateObj = new KanbanDO();
        updateObj.setId(id);
        updateObj.setStatus("active");
        updateObj.setActivatedBy(currentAccount());
        updateObj.setActivatedDate(LocalDateTime.now());
        kanbanMapper.updateById(updateObj);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN, id,
                ActionTypeEnum.ACTIVATED, "激活看板：" + kanban.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeKanban(Long id) {
        KanbanDO kanban = validateKanbanExists(id);
        if ("closed".equals(kanban.getStatus())) {
            throw exception(KANBAN_ALREADY_CLOSED);
        }
        KanbanDO updateObj = new KanbanDO();
        updateObj.setId(id);
        updateObj.setStatus("closed");
        updateObj.setClosedBy(currentAccount());
        updateObj.setClosedDate(LocalDateTime.now());
        kanbanMapper.updateById(updateObj);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN, id,
                ActionTypeEnum.CLOSED, "关闭看板：" + kanban.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKanban(Long id) {
        KanbanDO kanban = validateKanbanExists(id);
        // 级联软删：区域 → 泳道 / 列 / 卡片（禅道只软删看板本体，子对象会变孤儿）
        for (KanbanRegionDO region : regionMapper.selectListByKanban(id)) {
            softDeleteRegionChildren(region);
            // 区域自己也要软删（禅道连这一步都没有，只软删看板本体）
            regionMapper.deleteById(region.getId());
        }
        for (KanbanCardDO card : cardMapper.selectListByKanban(id)) {
            cardMapper.deleteById(card.getId());
        }
        cellMapper.deleteByKanban(id);
        kanbanMapper.deleteById(id);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN, id,
                ActionTypeEnum.DELETED, "删除看板：" + kanban.getName());
    }

    @Override
    public KanbanDO validateKanbanExists(Long id) {
        KanbanDO kanban = id == null ? null : kanbanMapper.selectById(id);
        if (kanban == null) {
            throw exception(KANBAN_NOT_EXISTS, id);
        }
        return kanban;
    }

    @Override
    public KanbanRespVO getKanban(Long id) {
        return convertKanban(validateKanbanExists(id));
    }

    @Override
    public PageResult<KanbanRespVO> getKanbanPage(KanbanPageReqVO reqVO) {
        PageResult<KanbanDO> page = kanbanMapper.selectPage(reqVO.getSpace(), reqVO.getName(), reqVO.getStatus(), reqVO);
        List<KanbanRespVO> list = new ArrayList<>(page.getList().size());
        for (KanbanDO kanban : page.getList()) {
            list.add(convertKanban(kanban));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public List<KanbanRespVO> getKanbanList(Long space) {
        List<KanbanRespVO> list = new ArrayList<>();
        for (KanbanDO kanban : kanbanMapper.selectListBySpace(space)) {
            list.add(convertKanban(kanban));
        }
        return list;
    }

    @Override
    public KanbanDataRespVO getKanbanData(Long kanbanId) {
        KanbanDO kanban = validateKanbanExists(kanbanId);
        KanbanDataRespVO data = new KanbanDataRespVO();
        data.setKanban(convertKanban(kanban));

        // 卡片一次性取出来（含归档），再按 cell 的编号列表组装 —— 避免每个格子一次查询
        Map<Long, KanbanCardDO> cardMap = new LinkedHashMap<>();
        for (KanbanCardDO card : cardMapper.selectListByKanban(kanbanId)) {
            cardMap.put(card.getId(), card);
        }

        for (KanbanRegionDO regionDO : regionMapper.selectListByKanban(kanbanId)) {
            KanbanDataRespVO.Region region = new KanbanDataRespVO.Region();
            region.setId(regionDO.getId());
            region.setName(regionDO.getName());
            KanbanGroupDO group = groupMapper.selectByRegion(regionDO.getId());
            region.setGroupId(group == null ? 0L : group.getId());

            List<KanbanColumnDO> columns = group == null ? List.of() : columnMapper.selectListByGroup(group.getId());
            for (KanbanColumnDO column : columns) {
                region.getColumns().add(convertColumn(column));
            }
            for (KanbanLaneDO laneDO : group == null ? List.<KanbanLaneDO>of() : laneMapper.selectListByGroup(group.getId())) {
                KanbanDataRespVO.Lane lane = new KanbanDataRespVO.Lane();
                lane.setId(laneDO.getId());
                lane.setName(laneDO.getName());
                lane.setType(laneDO.getType());
                lane.setColor(laneDO.getColor());
                for (KanbanColumnDO column : columns) {
                    KanbanCellDO cell = cellMapper.selectByLaneAndColumn(laneDO.getId(), column.getId());
                    KanbanDataRespVO.Cell cellVO = new KanbanDataRespVO.Cell();
                    cellVO.setColumnId(column.getId());
                    cellVO.setColumnName(column.getName());
                    cellVO.setLimit(column.getLimit());
                    List<Long> cardIds = parseIds(cell == null ? null : cell.getCards());
                    for (Long cardId : cardIds) {
                        KanbanCardDO card = cardMap.get(cardId);
                        // 格子里的悬空编号直接跳过（禅道物理删卡片时不会清格子）
                        if (card != null) {
                            cellVO.getCards().add(convertCard(card));
                        }
                    }
                    cellVO.setCardCount(cellVO.getCards().size());
                    Integer limit = column.getLimit();
                    cellVO.setOverWip(limit != null && limit != LIMIT_UNLIMITED && cellVO.getCardCount() > limit);
                    lane.getCells().add(cellVO);
                }
                region.getLanes().add(lane);
            }
            data.getRegions().add(region);
        }
        return data;
    }

    // ================================================================
    // 区域
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRegion(KanbanRegionSaveReqVO reqVO) {
        KanbanDO kanban = validateKanbanExists(reqVO.getKanban());
        return createRegionInternal(kanban, reqVO.getName(), reqVO.getOrder());
    }

    /**
     * 建区域 + 默认布局（分组、默认泳道、四个默认列、所有格子）。
     *
     * <p>对应禅道 {@code createRegion} → {@code createGroup + createDefaultLane + createDefaultColumns}。
     */
    private Long createRegionInternal(KanbanDO kanban, String name, Integer order) {
        KanbanRegionDO existed = regionMapper.selectByKanbanAndName(kanban.getId(), kanban.getSpace(), name);
        if (existed != null) {
            throw exception(KANBAN_REGION_NAME_DUPLICATE, name);
        }
        String account = currentAccount();
        KanbanRegionDO region = new KanbanRegionDO();
        region.setKanban(kanban.getId());
        region.setSpace(kanban.getSpace());
        region.setName(name);
        region.setOrder(order == null ? regionMapper.selectMaxOrder(kanban.getId()).intValue() + 1 : order);
        region.setCreatedBy(account);
        region.setCreatedDate(LocalDateTime.now());
        region.setLastEditedBy(account);
        region.setLastEditedDate(LocalDateTime.now());
        regionMapper.insert(region);

        // 分组 + 默认泳道 + 默认列
        Long groupId = createGroup(kanban.getId(), region.getId());
        createLaneRow(region, groupId, DEFAULT_LANE_NAME, KanbanLaneTypeEnum.COMMON.getType(), DEFAULT_LANE_COLOR, 1, true, false);
        int columnOrder = 1;
        for (String columnName : DEFAULT_COLUMNS) {
            createColumnRow(region, groupId, columnName, DEFAULT_COLUMN_COLOR, LIMIT_UNLIMITED, columnOrder++, 0L);
        }
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_REGION, region.getId(),
                ActionTypeEnum.CREATED, "新建看板区域：" + name);
        return region.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRegion(KanbanRegionSaveReqVO reqVO) {
        KanbanRegionDO old = validateRegionExists(reqVO.getId());
        KanbanRegionDO existed = regionMapper.selectByKanbanAndName(old.getKanban(), old.getSpace(), reqVO.getName());
        if (existed != null && !Objects.equals(existed.getId(), old.getId())) {
            throw exception(KANBAN_REGION_NAME_DUPLICATE, reqVO.getName());
        }
        KanbanRegionDO updateObj = new KanbanRegionDO();
        updateObj.setId(old.getId());
        updateObj.setName(reqVO.getName());
        updateObj.setOrder(reqVO.getOrder());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        regionMapper.updateById(updateObj);
        actionService.recordActionWithChanges(ActionServiceImpl.OBJECT_TYPE_KANBAN_REGION, old.getId(),
                ActionTypeEnum.EDITED, "修改看板区域：" + reqVO.getName(), old, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRegion(Long id) {
        KanbanRegionDO region = validateRegionExists(id);
        softDeleteRegionChildren(region);
        regionMapper.deleteById(id);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_REGION, id,
                ActionTypeEnum.DELETED, "删除看板区域：" + region.getName());
    }

    /**
     * 软删区域下的泳道/列/卡片 + 物理清理格子（区域本身由调用方删）。
     *
     * <p>顺序要紧：**先把泳道列表取出来**，再删泳道；软删之后再查就查不到泳道了，
     * 格子（没有 deleted 列）会留下来变成垃圾。
     */
    private void softDeleteRegionChildren(KanbanRegionDO region) {
        List<KanbanLaneDO> lanes = laneMapper.selectListByRegion(region.getId());
        List<KanbanColumnDO> columns = columnMapper.selectListByRegion(region.getId());
        for (KanbanLaneDO lane : lanes) {
            cellMapper.deleteByLane(lane.getId());
            laneMapper.deleteById(lane.getId());
        }
        for (KanbanColumnDO column : columns) {
            cellMapper.deleteByColumn(column.getId());
            columnMapper.deleteById(column.getId());
        }
        for (KanbanCardDO card : cardMapper.selectListByKanban(region.getKanban())) {
            if (Objects.equals(card.getRegion(), region.getId())) {
                cardMapper.deleteById(card.getId());
            }
        }
        KanbanGroupDO group = groupMapper.selectByRegion(region.getId());
        if (group != null) {
            groupMapper.deleteById(group.getId());
        }
    }

    @Override
    public KanbanRegionDO validateRegionExists(Long id) {
        KanbanRegionDO region = id == null ? null : regionMapper.selectById(id);
        if (region == null) {
            throw exception(KANBAN_REGION_NOT_EXISTS, id);
        }
        return region;
    }

    @Override
    public List<KanbanRegionRespVO> getRegionList(Long kanban) {
        List<KanbanRegionRespVO> list = new ArrayList<>();
        for (KanbanRegionDO region : regionMapper.selectListByKanban(kanban)) {
            KanbanRegionRespVO vo = BeanUtils.toBean(region, KanbanRegionRespVO.class);
            KanbanGroupDO group = groupMapper.selectByRegion(region.getId());
            if (group != null) {
                vo.setGroupId(group.getId());
                vo.setLaneCount((long) laneMapper.selectListByGroup(group.getId()).size());
                vo.setColumnCount((long) columnMapper.selectListByGroup(group.getId()).size());
            }
            list.add(vo);
        }
        return list;
    }

    // ================================================================
    // 泳道
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLane(KanbanLaneSaveReqVO reqVO) {
        KanbanRegionDO region = validateRegionExists(reqVO.getRegion());
        String type = StringUtils.hasText(reqVO.getType()) ? reqVO.getType() : KanbanLaneTypeEnum.COMMON.getType();
        if (!KanbanLaneTypeEnum.isValid(type)) {
            throw exception(KANBAN_LANE_TYPE_INVALID, type);
        }
        KanbanGroupDO group = groupMapper.selectByRegion(region.getId());
        if (group == null) {
            throw exception(KANBAN_REGION_NOT_EXISTS, region.getId());
        }
        if (reqVO.getOrder() != null && reqVO.getOrder() > 0) {
            laneMapper.shiftOrder(group.getId(), reqVO.getOrder());
        }
        return createLaneRow(region, group.getId(), reqVO.getName(), type,
                StringUtils.hasText(reqVO.getColor()) ? reqVO.getColor() : DEFAULT_LANE_COLOR,
                reqVO.getOrder(), true, true);
    }

    private Long createLaneRow(KanbanRegionDO region, Long groupId, String name, String type, String color,
                               Integer order, boolean createCells, boolean recordAction) {
        KanbanLaneDO lane = new KanbanLaneDO();
        lane.setRegion(region.getId());
        lane.setGroupId(groupId);
        lane.setExecution(0L);
        lane.setName(name);
        lane.setType(type);
        lane.setColor(color);
        lane.setGroupby("");
        lane.setExtra("");
        lane.setOrder(order == null || order <= 0 ? laneMapper.selectMaxOrder(groupId).intValue() + 1 : order);
        lane.setLastEditedTime(LocalDateTime.now());
        laneMapper.insert(lane);
        if (createCells) {
            createCellsForLane(region.getId(), groupId, lane.getId(), type);
        }
        if (recordAction) {
            actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_LANE, lane.getId(),
                    ActionTypeEnum.CREATED, "新建看板泳道：" + name);
        }
        return lane.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLane(KanbanLaneSaveReqVO reqVO) {
        KanbanLaneDO old = validateLaneExists(reqVO.getId());
        if (StringUtils.hasText(reqVO.getType()) && !KanbanLaneTypeEnum.isValid(reqVO.getType())) {
            throw exception(KANBAN_LANE_TYPE_INVALID, reqVO.getType());
        }
        KanbanLaneDO updateObj = new KanbanLaneDO();
        updateObj.setId(old.getId());
        updateObj.setName(reqVO.getName());
        updateObj.setType(reqVO.getType());
        updateObj.setColor(reqVO.getColor());
        updateObj.setGroupby(reqVO.getGroupby());
        updateObj.setExtra(reqVO.getExtra());
        updateObj.setOrder(reqVO.getOrder());
        updateObj.setLastEditedTime(LocalDateTime.now());
        laneMapper.updateById(updateObj);
        // 泳道类型变了，格子的 type 也要跟着改（格子的唯一键是 kanban+type+lane+column）
        if (StringUtils.hasText(reqVO.getType()) && !Objects.equals(old.getType(), reqVO.getType())) {
            for (KanbanCellDO cell : cellMapper.selectListByKanban(kanbanIdOfLane(old))) {
                if (Objects.equals(cell.getLane(), old.getId())) {
                    KanbanCellDO cellUpdate = new KanbanCellDO();
                    cellUpdate.setId(cell.getId());
                    cellUpdate.setType(reqVO.getType());
                    cellMapper.updateById(cellUpdate);
                }
            }
        }
        actionService.recordActionWithChanges(ActionServiceImpl.OBJECT_TYPE_KANBAN_LANE, old.getId(),
                ActionTypeEnum.EDITED, "修改看板泳道：" + reqVO.getName(), old, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLane(Long id) {
        KanbanLaneDO lane = validateLaneExists(id);
        // 泳道逻辑删除，格子物理清理（禅道删泳道后泳道里的卡片会被「隐藏」）
        cellMapper.deleteByLane(id);
        laneMapper.deleteById(id);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_LANE, id,
                ActionTypeEnum.DELETED, "删除看板泳道：" + lane.getName());
    }

    @Override
    public KanbanLaneDO validateLaneExists(Long id) {
        KanbanLaneDO lane = id == null ? null : laneMapper.selectById(id);
        if (lane == null) {
            throw exception(KANBAN_LANE_NOT_EXISTS, id);
        }
        return lane;
    }

    @Override
    public List<KanbanLaneRespVO> getLaneList(Long region) {
        List<KanbanLaneRespVO> list = new ArrayList<>();
        for (KanbanLaneDO lane : laneMapper.selectListByRegion(region)) {
            list.add(convertLane(lane));
        }
        return list;
    }

    // ================================================================
    // 列
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createColumn(KanbanColumnSaveReqVO reqVO) {
        KanbanGroupDO group = groupMapper.selectById(reqVO.getGroup());
        if (group == null) {
            throw exception(KANBAN_REGION_NOT_EXISTS, reqVO.getGroup());
        }
        validateColumnLimit(reqVO.getLimit(), reqVO.getParent());
        if (reqVO.getOrder() != null && reqVO.getOrder() > 0) {
            columnMapper.shiftOrder(group.getId(), reqVO.getOrder());
        }
        KanbanRegionDO region = validateRegionExists(group.getRegion());
        return createColumnRow(region, group.getId(), reqVO.getName(),
                StringUtils.hasText(reqVO.getColor()) ? reqVO.getColor() : DEFAULT_COLUMN_COLOR,
                reqVO.getLimit() == null ? LIMIT_UNLIMITED : reqVO.getLimit(), reqVO.getOrder(),
                reqVO.getParent() == null ? 0L : reqVO.getParent());
    }

    private Long createColumnRow(KanbanRegionDO region, Long groupId, String name, String color, Integer limit,
                                 Integer order, Long parent) {
        KanbanColumnDO column = new KanbanColumnDO();
        column.setRegion(region.getId());
        column.setGroupId(groupId);
        column.setParent(parent == null ? 0L : parent);
        column.setName(name);
        column.setColor(color);
        column.setLimit(limit == null ? LIMIT_UNLIMITED : limit);
        column.setOrder(order == null || order <= 0 ? columnMapper.selectMaxOrder(groupId).intValue() + 1 : order);
        column.setArchived(0);
        columnMapper.insert(column);
        // 禅道建列后把 type 回写成 column{自己id}（列的「身份」，前端据此渲染）
        KanbanColumnDO typeUpdate = new KanbanColumnDO();
        typeUpdate.setId(column.getId());
        typeUpdate.setType("column" + column.getId());
        columnMapper.updateById(typeUpdate);
        createCellsForColumn(region.getId(), groupId, column.getId());
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_COLUMN, column.getId(),
                ActionTypeEnum.CREATED, "新建看板列：" + name);
        return column.getId();
    }

    /**
     * 校验在制品上限（WIP）。禅道 {@code createColumn} / {@code checkChildColumn}：
     * <ul>
     *   <li>limit 只能是 -1（不限）或正整数</li>
     *   <li>父列有限额时：子列不能是 -1，且「已有子列之和 + 本列」不能超过父列限额</li>
     * </ul>
     */
    private void validateColumnLimit(Integer limit, Long parent) {
        int value = limit == null ? LIMIT_UNLIMITED : limit;
        if (value != LIMIT_UNLIMITED && value <= 0) {
            throw exception(KANBAN_COLUMN_LIMIT_INVALID, value);
        }
        if (parent == null || parent <= 0) {
            return;
        }
        KanbanColumnDO parentColumn = validateColumnExists(parent);
        Integer parentLimit = parentColumn.getLimit();
        if (parentLimit == null || parentLimit == LIMIT_UNLIMITED) {
            return;
        }
        long sum = columnMapper.sumChildLimit(parent);
        if (value == LIMIT_UNLIMITED || value + sum > parentLimit) {
            throw exception(KANBAN_COLUMN_CHILD_LIMIT_EXCEEDED, value == LIMIT_UNLIMITED ? "不限" : (value + sum), parentLimit);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateColumn(KanbanColumnSaveReqVO reqVO) {
        KanbanColumnDO old = validateColumnExists(reqVO.getId());
        validateColumnLimit(reqVO.getLimit(), old.getParent());
        KanbanColumnDO updateObj = new KanbanColumnDO();
        updateObj.setId(old.getId());
        updateObj.setName(reqVO.getName());
        updateObj.setColor(reqVO.getColor());
        updateObj.setLimit(reqVO.getLimit());
        updateObj.setOrder(reqVO.getOrder());
        columnMapper.updateById(updateObj);
        actionService.recordActionWithChanges(ActionServiceImpl.OBJECT_TYPE_KANBAN_COLUMN, old.getId(),
                ActionTypeEnum.EDITED, "修改看板列：" + reqVO.getName(), old, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveColumn(Long id) {
        KanbanColumnDO column = validateColumnExists(id);
        KanbanColumnDO updateObj = new KanbanColumnDO();
        updateObj.setId(id);
        updateObj.setArchived(1);
        columnMapper.updateById(updateObj);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_COLUMN, id,
                ActionTypeEnum.EDITED, "归档看板列：" + column.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreColumn(Long id) {
        KanbanColumnDO column = validateColumnExists(id);
        KanbanColumnDO updateObj = new KanbanColumnDO();
        updateObj.setId(id);
        updateObj.setArchived(0);
        columnMapper.updateById(updateObj);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_COLUMN, id,
                ActionTypeEnum.EDITED, "还原看板列：" + column.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteColumn(Long id) {
        KanbanColumnDO column = validateColumnExists(id);
        long childCount = columnMapper.selectListByGroup(column.getGroupId()).stream()
                .filter(c -> Objects.equals(c.getParent(), id)).count();
        if (childCount > 0) {
            throw exception(KANBAN_COLUMN_HAS_CHILD, childCount);
        }
        // 列是**物理删除**（禅道 control:deleteColumn 就是 dao->delete），格子一起清理
        cellMapper.deleteByColumn(id);
        columnMapper.deleteById(id);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_COLUMN, id,
                ActionTypeEnum.DELETED, "删除看板列：" + column.getName());
    }

    @Override
    public KanbanColumnDO validateColumnExists(Long id) {
        KanbanColumnDO column = id == null ? null : columnMapper.selectById(id);
        if (column == null) {
            throw exception(KANBAN_COLUMN_NOT_EXISTS, id);
        }
        return column;
    }

    @Override
    public List<KanbanColumnRespVO> getColumnList(Long group) {
        List<KanbanColumnRespVO> list = new ArrayList<>();
        for (KanbanColumnDO column : columnMapper.selectListByGroup(group)) {
            list.add(convertColumn(column));
        }
        return list;
    }

    // ================================================================
    // 卡片
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCard(KanbanCardSaveReqVO reqVO) {
        validateCardFields(reqVO);
        KanbanColumnDO column = validateColumnExists(reqVO.getColumn());
        KanbanLaneDO lane = validateLaneExists(reqVO.getLane());
        Long kanbanId = reqVO.getKanban() == null ? kanbanIdOfLane(lane) : reqVO.getKanban();
        validateKanbanExists(kanbanId);
        String account = currentAccount();
        KanbanCardDO card = new KanbanCardDO();
        card.setKanban(kanbanId);
        card.setRegion(lane.getRegion());
        card.setGroupId(lane.getGroupId());
        card.setFromID(0L);
        card.setFromType("");
        card.setName(reqVO.getName());
        card.setStatus(KanbanCardStatusEnum.DOING.getStatus());
        card.setPri(reqVO.getPri() == null ? 0 : reqVO.getPri());
        card.setAssignedTo(reqVO.getAssignedTo() == null ? "" : reqVO.getAssignedTo());
        card.setDesc(reqVO.getDesc() == null ? "" : reqVO.getDesc());
        card.setBegin(reqVO.getBegin());
        card.setEnd(reqVO.getEnd());
        card.setEstimate(reqVO.getEstimate() == null ? BigDecimal.ZERO : reqVO.getEstimate());
        card.setProgress(BigDecimal.ZERO);
        card.setColor(reqVO.getColor() == null ? "#fff" : reqVO.getColor());
        card.setAcl("open");
        card.setWhitelist("");
        card.setOrder(0);
        card.setArchived(0);
        card.setCreatedBy(account);
        card.setCreatedDate(LocalDateTime.now());
        card.setLastEditedBy(account);
        card.setLastEditedDate(LocalDateTime.now());
        cardMapper.insert(card);
        // ★ 卡片的位置 = 格子的 cards 列表（追加到末尾）
        addCardToCell(kanbanId, lane.getId(), column.getId(), lane.getType(), card.getId());
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_CARD, card.getId(),
                ActionTypeEnum.CREATED, "新建看板卡片：" + card.getName());
        return card.getId();
    }

    private void validateCardFields(KanbanCardSaveReqVO reqVO) {
        if (reqVO.getEstimate() != null && reqVO.getEstimate().compareTo(BigDecimal.ZERO) < 0) {
            throw exception(KANBAN_CARD_ESTIMATE_INVALID);
        }
        if (reqVO.getBegin() != null && reqVO.getEnd() != null && reqVO.getBegin().isAfter(reqVO.getEnd())) {
            throw exception(KANBAN_CARD_DATE_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCard(KanbanCardSaveReqVO reqVO) {
        KanbanCardDO old = validateCardExists(reqVO.getId());
        validateCardFields(reqVO);
        KanbanCardDO updateObj = new KanbanCardDO();
        updateObj.setId(old.getId());
        updateObj.setName(reqVO.getName());
        updateObj.setPri(reqVO.getPri());
        updateObj.setAssignedTo(reqVO.getAssignedTo());
        updateObj.setDesc(reqVO.getDesc());
        updateObj.setBegin(reqVO.getBegin());
        updateObj.setEnd(reqVO.getEnd());
        updateObj.setEstimate(reqVO.getEstimate());
        updateObj.setColor(reqVO.getColor());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        cardMapper.updateById(updateObj);
        actionService.recordActionWithChanges(ActionServiceImpl.OBJECT_TYPE_KANBAN_CARD, old.getId(),
                ActionTypeEnum.EDITED, "修改看板卡片：" + reqVO.getName(), old, updateObj);
    }

    @Override
    public KanbanCardDO validateCardExists(Long id) {
        KanbanCardDO card = id == null ? null : cardMapper.selectById(id);
        if (card == null) {
            throw exception(KANBAN_CARD_NOT_EXISTS, id);
        }
        return card;
    }

    @Override
    public KanbanCardRespVO getCard(Long id) {
        return convertCard(validateCardExists(id));
    }

    @Override
    public PageResult<KanbanCardRespVO> getCardPage(KanbanCardPageReqVO reqVO) {
        PageResult<KanbanCardDO> page = cardMapper.selectPage(reqVO.getKanban(), reqVO.getName(), reqVO.getArchived(), reqVO);
        List<KanbanCardRespVO> list = new ArrayList<>(page.getList().size());
        for (KanbanCardDO card : page.getList()) {
            list.add(convertCard(card));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveCard(Long cardId, Long fromColumnId, Long toColumnId, Long fromLaneId, Long toLaneId) {
        KanbanCardDO card = validateCardExists(cardId);
        KanbanLaneDO fromLane = fromLaneId == null ? null : validateLaneExists(fromLaneId);
        KanbanLaneDO toLane = validateLaneExists(toLaneId);
        KanbanColumnDO toColumn = validateColumnExists(toColumnId);
        if (toColumn.getArchived() != null && toColumn.getArchived() == 1) {
            throw exception(KANBAN_COLUMN_NOT_EXISTS, toColumnId);
        }
        Long kanbanId = card.getKanban();
        // ① 先按「同类型泳道」把卡片从该区域所有格子里摘掉（禅道 moveCard 的 fromCells）。
        //    注意用的是**源泳道**的类型：换泳道时源、目标类型可能不同（common ↔ story），
        //    用目标类型去扫会扫不到源格子，卡片就同时留在两个格子里了（本轮实测踩到过）。
        String scanType = fromLane != null && StringUtils.hasText(fromLane.getType())
                ? fromLane.getType() : card != null ? laneTypeOfCard(card) : KanbanLaneTypeEnum.COMMON.getType();
        removeCardFromCells(kanbanId, scanType, cardId);
        // ② 再追加到目标格子末尾
        addCardToCell(kanbanId, toLane.getId(), toColumn.getId(), toLane.getType(), cardId);
        // ③ 卡片的分组跟着目标泳道走
        KanbanCardDO updateObj = new KanbanCardDO();
        updateObj.setId(cardId);
        updateObj.setGroupId(toLane.getGroupId());
        updateObj.setRegion(toLane.getRegion());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        cardMapper.updateById(updateObj);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_CARD, cardId,
                ActionTypeEnum.EDITED, "移动看板卡片：" + card.getName()
                        + "（列 " + fromColumnId + " → " + toColumnId + "，泳道 " + fromLaneId + " → " + toLaneId + "）");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishCard(Long id) {
        KanbanCardDO card = validateCardExists(id);
        KanbanCardDO updateObj = new KanbanCardDO();
        updateObj.setId(id);
        updateObj.setProgress(new BigDecimal("100"));
        updateObj.setStatus(KanbanCardStatusEnum.DONE.getStatus());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        cardMapper.updateById(updateObj);
        actionService.recordActionWithChanges(ActionServiceImpl.OBJECT_TYPE_KANBAN_CARD, id,
                ActionTypeEnum.EDITED, "完成看板卡片：" + card.getName(), card, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateCard(Long id, BigDecimal progress) {
        KanbanCardDO card = validateCardExists(id);
        BigDecimal value = progress == null ? BigDecimal.ZERO : progress;
        // 禅道：progress >= 100 或 < 0 都拒绝（100 属于「完成」）
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) >= 0) {
            throw exception(KANBAN_CARD_PROGRESS_INVALID);
        }
        KanbanCardDO updateObj = new KanbanCardDO();
        updateObj.setId(id);
        updateObj.setProgress(value);
        updateObj.setStatus(KanbanCardStatusEnum.DOING.getStatus());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        cardMapper.updateById(updateObj);
        actionService.recordActionWithChanges(ActionServiceImpl.OBJECT_TYPE_KANBAN_CARD, id,
                ActionTypeEnum.ACTIVATED, "激活看板卡片：" + card.getName(), card, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveCard(Long id) {
        KanbanCardDO card = validateCardExists(id);
        KanbanCardDO updateObj = new KanbanCardDO();
        updateObj.setId(id);
        updateObj.setArchived(1);
        updateObj.setArchivedBy(currentAccount());
        updateObj.setArchivedDate(LocalDateTime.now());
        cardMapper.updateById(updateObj);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_CARD, id,
                ActionTypeEnum.EDITED, "归档看板卡片：" + card.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreCard(Long id) {
        KanbanCardDO card = validateCardExists(id);
        KanbanCardDO updateObj = new KanbanCardDO();
        updateObj.setId(id);
        updateObj.setArchived(0);
        updateObj.setArchivedBy("");
        updateObj.setArchivedDate(null);
        cardMapper.updateById(updateObj);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_CARD, id,
                ActionTypeEnum.EDITED, "还原看板卡片：" + card.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCard(Long id) {
        KanbanCardDO card = validateCardExists(id);
        // 禅道：fromType 为空时走通用 delete（逻辑删），否则物理删；这里统一物理删 + 摘格子
        removeCardFromCells(card.getKanban(), laneTypeOfCard(card), id);
        cardMapper.deleteById(id);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_KANBAN_CARD, id,
                ActionTypeEnum.DELETED, "删除看板卡片：" + card.getName());
    }

    // ================================================================
    // 格子（卡片位置）与内部工具
    // ================================================================

    /** 卡片追加到格子末尾（禅道 addKanbanCell：`,$cardID` + 原有列表） */
    private void addCardToCell(Long kanbanId, Long laneId, Long columnId, String type, Long cardId) {
        KanbanCellDO cell = cellMapper.selectByLaneAndColumn(laneId, columnId);
        if (cell == null) {
            cell = new KanbanCellDO();
            cell.setKanban(kanbanId);
            cell.setLane(laneId);
            cell.setColumn(columnId);
            cell.setType(type);
            cell.setCards(appendCard("", cardId));
            cellMapper.insert(cell);
            return;
        }
        cellMapper.updateCards(cell.getId(), appendCard(cell.getCards(), cardId));
    }

    /**
     * 把卡片从「该区域同类型泳道」的所有格子里摘掉。
     *
     * <p>为什么要按类型扫一片而不是只摘一个格子：研发看板里同一条需求会同时出现在
     * story/bug/task 三类泳道中，卡片换列时要把所有出现位置一起更新（禅道 moveCard 的 fromCells）。
     */
    private void removeCardFromCells(Long kanbanId, String type, Long cardId) {
        if (kanbanId == null) {
            return;
        }
        for (KanbanCellDO cell : cellMapper.selectListByKanban(kanbanId)) {
            if (!Objects.equals(cell.getType(), type)) {
                continue;
            }
            List<Long> ids = parseIds(cell.getCards());
            if (!ids.contains(cardId)) {
                continue;
            }
            ids.remove(cardId);
            cellMapper.updateCards(cell.getId(), joinIds(ids));
        }
    }

    /** 补建「某列在所有泳道下的格子」（建列时用；禅道 createColumn 末尾就是这么干的） */
    private void createCellsForColumn(Long regionId, Long groupId, Long columnId) {
        KanbanDO kanban = kanbanOfRegion(regionId);
        for (KanbanLaneDO lane : laneMapper.selectListByGroup(groupId)) {
            if (cellMapper.selectByLaneAndColumn(lane.getId(), columnId) == null) {
                KanbanCellDO cell = new KanbanCellDO();
                cell.setKanban(kanban == null ? 0L : kanban.getId());
                cell.setLane(lane.getId());
                cell.setColumn(columnId);
                cell.setType(lane.getType());
                cell.setCards("");
                cellMapper.insert(cell);
            }
        }
    }

    /** 补建「某泳道在所有列下的格子」（建泳道时用） */
    private void createCellsForLane(Long regionId, Long groupId, Long laneId, String type) {
        KanbanDO kanban = kanbanOfRegion(regionId);
        for (KanbanColumnDO column : columnMapper.selectListByGroup(groupId)) {
            if (cellMapper.selectByLaneAndColumn(laneId, column.getId()) == null) {
                KanbanCellDO cell = new KanbanCellDO();
                cell.setKanban(kanban == null ? 0L : kanban.getId());
                cell.setLane(laneId);
                cell.setColumn(column.getId());
                cell.setType(type);
                cell.setCards("");
                cellMapper.insert(cell);
            }
        }
    }

    private Long createGroup(Long kanbanId, Long regionId) {
        KanbanGroupDO group = new KanbanGroupDO();
        group.setKanban(kanbanId);
        group.setRegion(regionId);
        group.setOrder(1);
        groupMapper.insert(group);
        return group.getId();
    }

    private KanbanDO kanbanOfRegion(Long regionId) {
        KanbanRegionDO region = regionMapper.selectById(regionId);
        return region == null ? null : kanbanMapper.selectById(region.getKanban());
    }

    private Long kanbanIdOfLane(KanbanLaneDO lane) {
        KanbanDO kanban = kanbanOfRegion(lane.getRegion());
        return kanban == null ? 0L : kanban.getId();
    }

    /** 卡片所在泳道的类型（删卡片时用来定位格子） */
    private String laneTypeOfCard(KanbanCardDO card) {
        for (KanbanCellDO cell : cellMapper.selectListByKanban(card.getKanban())) {
            if (parseIds(cell.getCards()).contains(card.getId())) {
                return cell.getType();
            }
        }
        return KanbanLaneTypeEnum.COMMON.getType();
    }

    /** 逗号列表 → 编号列表（`,1,2,` → [1, 2]） */
    private List<Long> parseIds(String ids) {
        List<Long> result = new ArrayList<>();
        if (!StringUtils.hasText(ids)) {
            return result;
        }
        for (String part : ids.split(",")) {
            if (StringUtils.hasText(part)) {
                result.add(Long.valueOf(part.trim()));
            }
        }
        return result;
    }

    /** 编号列表 → 逗号列表（[1, 2] → `,1,2,`），与禅道一样两头都带逗号 */
    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(",");
        for (Long id : ids) {
            sb.append(id).append(',');
        }
        return sb.toString();
    }

    private String appendCard(String cards, Long cardId) {
        List<Long> ids = parseIds(cards);
        ids.add(cardId);
        return joinIds(ids);
    }

    // ================================================================
    // VO 转换
    // ================================================================

    private KanbanSpaceRespVO convertSpace(KanbanSpaceDO space) {
        KanbanSpaceRespVO vo = BeanUtils.toBean(space, KanbanSpaceRespVO.class);
        vo.setTypeName(KanbanSpaceTypeEnum.nameOf(space.getType()));
        vo.setKanbanCount(kanbanMapper.countBySpace(space.getId()));
        return vo;
    }

    private KanbanRespVO convertKanban(KanbanDO kanban) {
        KanbanRespVO vo = BeanUtils.toBean(kanban, KanbanRespVO.class);
        KanbanSpaceDO space = kanban.getSpace() == null ? null : spaceMapper.selectById(kanban.getSpace());
        vo.setSpaceName(space == null ? "" : space.getName());
        vo.setRegionCount((long) regionMapper.selectListByKanban(kanban.getId()).size());
        vo.setCardCount(cardMapper.countByKanban(kanban.getId()));
        return vo;
    }

    private KanbanLaneRespVO convertLane(KanbanLaneDO lane) {
        KanbanLaneRespVO vo = BeanUtils.toBean(lane, KanbanLaneRespVO.class);
        vo.setTypeName(KanbanLaneTypeEnum.nameOf(lane.getType()));
        return vo;
    }

    private KanbanColumnRespVO convertColumn(KanbanColumnDO column) {
        KanbanColumnRespVO vo = BeanUtils.toBean(column, KanbanColumnRespVO.class);
        vo.setArchived(column.getArchived() != null && column.getArchived() == 1);
        return vo;
    }

    private KanbanCardRespVO convertCard(KanbanCardDO card) {
        KanbanCardRespVO vo = BeanUtils.toBean(card, KanbanCardRespVO.class);
        vo.setStatusName(KanbanCardStatusEnum.nameOf(card.getStatus()));
        vo.setArchived(card.getArchived() != null && card.getArchived() == 1);
        return vo;
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
