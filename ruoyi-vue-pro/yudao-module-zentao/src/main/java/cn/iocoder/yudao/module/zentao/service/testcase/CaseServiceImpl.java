package cn.iocoder.yudao.module.zentao.service.testcase;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CasePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseReviewReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseSpecRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseStepVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testcase.CaseDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testcase.CaseSpecDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testcase.CaseStepDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.testcase.CaseMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testcase.CaseSpecMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testcase.CaseStepMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.testcase.CaseStageEnum;
import cn.iocoder.yudao.module.zentao.enums.testcase.CaseStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.testcase.CaseStepTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.testcase.CaseTypeEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import cn.iocoder.yudao.module.zentao.service.module.ModuleService;
import cn.iocoder.yudao.module.zentao.service.story.StoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 测试用例 Service 实现
 *
 * <p>对齐禅道 {@code module/testcase} 的 create / update / delete / review /
 * getById / getSteps / processSteps / processStepsChanged / confirmStoryChange。
 *
 * <p>三处最容易写错的地方，代码里都单独标了注释：
 * <ol>
 *   <li><b>版本判据是「步骤」</b>：标题、前置条件、优先级、状态改了都不升版本；
 *       只有步骤内容变了才 version+1，并把状态打回 wait</li>
 *   <li><b>步骤的层级编号要算出来</b>：数据库里只有 parent 指向步骤组，
 *       {@code 1. / 1.1 / 1.1.1} 是读的时候按禅道 processSteps 的算法推的</li>
 *   <li><b>storyVersion 是冻结值</b>：需求升版后用例不会自己变，要人确认</li>
 * </ol>
 */
@Slf4j
@Service
public class CaseServiceImpl implements CaseService {

    /**
     * 步骤最大层级：禅道 processSteps 里 grade 只算到 3（组 / 子组 / 具体步骤）
     */
    private static final int MAX_STEP_GRADE = 3;

    @Resource
    private CaseMapper caseMapper;

    @Resource
    private CaseSpecMapper caseSpecMapper;

    @Resource
    private CaseStepMapper caseStepMapper;

    @Resource
    private ModuleService moduleService;

    @Resource
    private StoryService storyService;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ================================================================
    // 新建
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCase(CaseSaveReqVO reqVO) {
        validateTypeAndStage(reqVO);
        // 归属：产品用例（product>0, lib=0）或用例库用例（product=0, lib>0），二选一
        Long product = resolveProduct(reqVO);
        boolean inLib = reqVO.getLib() != null && reqVO.getLib() > 0;

        // 关联需求：必须存在，且和用例在同一个产品下（禅道 createCase 里也校验）。
        // 用例库用例不关联需求（库用例的 story 恒为 0）
        StoryDO story = null;
        if (!inLib && reqVO.getStory() != null && reqVO.getStory() > 0) {
            story = storyService.validateStoryExists(reqVO.getStory());
            if (!Objects.equals(story.getProduct(), product)) {
                throw exception(CASE_STORY_NOT_IN_PRODUCT, reqVO.getStory());
            }
        }

        String account = currentAccount();
        LocalDateTime now = LocalDateTime.now();

        CaseDO caseDO = new CaseDO();
        caseDO.setProduct(product);
        caseDO.setLib(inLib ? reqVO.getLib() : 0L);
        caseDO.setBranch(reqVO.getBranch() == null ? 0L : reqVO.getBranch());
        caseDO.setModule(reqVO.getModule() == null ? 0L : reqVO.getModule());
        caseDO.setStory(story == null ? 0L : story.getId());
        // 冻结需求版本：之后需求升版不会自动同步，而是让用例进入「待确认」
        caseDO.setStoryVersion(story == null ? 1 : story.getVersion());
        caseDO.setTitle(reqVO.getTitle());
        caseDO.setPrecondition(reqVO.getPrecondition() == null ? "" : reqVO.getPrecondition());
        caseDO.setKeywords(reqVO.getKeywords() == null ? "" : reqVO.getKeywords());
        caseDO.setPri(reqVO.getPri() == null ? 3 : reqVO.getPri());
        caseDO.setType(reqVO.getType());
        caseDO.setStage(reqVO.getStage() == null ? "" : reqVO.getStage());
        caseDO.setStatus(StringUtils.hasText(reqVO.getStatus()) ? reqVO.getStatus()
                : CaseStatusEnum.NORMAL.getStatus());
        caseDO.setLastRunResult("");
        caseDO.setLastRunner("");
        caseDO.setVersion(1);
        caseDO.setOrder(0);
        caseDO.setSort(0);
        caseDO.setOpenedBy(account);
        caseDO.setOpenedDate(now);
        caseDO.setLastEditedBy(account);
        caseDO.setLastEditedDate(now);
        caseDO.setFromBug(0L);
        caseDO.setFromCaseID(0L);
        caseDO.setFromCaseVersion(1);
        caseMapper.insert(caseDO);

        // 禅道把 order 初始化成 id，保证新用例排在最后
        CaseDO orderUpdate = new CaseDO();
        orderUpdate.setId(caseDO.getId());
        orderUpdate.setOrder(caseDO.getId().intValue());
        orderUpdate.setSort(caseDO.getId().intValue());
        caseMapper.updateById(orderUpdate);

        insertSpec(caseDO.getId(), 1, reqVO.getTitle(), reqVO.getPrecondition(), "");
        // ★ 新建也必须走一遍 normalizeSteps：
        //   它同时承担「校验」和「把 parent 从批内下标规范化」两件事。
        //   直接把原始 steps 丢给 insertSteps 会绕过全部校验 ——
        //   步骤组带预期结果能建成功，parent 传个越界下标直接 500。
        insertSteps(caseDO.getId(), 1, normalizeSteps(reqVO.getSteps()));

        actionService.recordAction("case", caseDO.getId(), ActionTypeEnum.CREATED,
                "新建用例：" + reqVO.getTitle());
        return caseDO.getId();
    }

    // ================================================================
    // 修改（版本规则的核心）
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCase(CaseSaveReqVO reqVO) {
        CaseDO old = validateCaseExists(reqVO.getId());
        validateTypeAndStage(reqVO);
        // 归属不可改：产品用例改不成库用例（反之亦然），只能改内容。
        // 所以这里用**库里已有的**归属来校验，而不是让调用方重新传一遍 product/lib。
        boolean inLib = old.getLib() != null && old.getLib() > 0;
        Long product = inLib ? 0L : old.getProduct();

        StoryDO story = null;
        if (!inLib && reqVO.getStory() != null && reqVO.getStory() > 0) {
            story = storyService.validateStoryExists(reqVO.getStory());
            if (!Objects.equals(story.getProduct(), product)) {
                throw exception(CASE_STORY_NOT_IN_PRODUCT, reqVO.getStory());
            }
        }

        String account = currentAccount();
        LocalDateTime now = LocalDateTime.now();
        Integer oldVersion = old.getVersion() == null ? 1 : old.getVersion();

        // ★ 版本判据：新提交的步骤 与 当前版本的步骤 是否一致。
        //   注意比的是「归一化后」的形态（desc/expect/type + 父组在列表里的位置），
        //   不是行 id —— 因为每次升版本都会重新插入一批步骤行，id 必然不同。
        boolean stepChanged = reqVO.getSteps() != null
                && stepsChanged(reqVO.getSteps(), old.getId(), oldVersion);

        List<CaseStepVO> normalized = normalizeSteps(reqVO.getSteps());
        Integer newVersion = stepChanged ? oldVersion + 1 : oldVersion;

        CaseDO updateObj = new CaseDO();
        updateObj.setId(old.getId());
        updateObj.setProduct(product);
        updateObj.setBranch(reqVO.getBranch());
        updateObj.setModule(reqVO.getModule());
        updateObj.setStory(story == null ? 0L : story.getId());
        updateObj.setStoryVersion(story == null ? old.getStoryVersion() : story.getVersion());
        updateObj.setTitle(reqVO.getTitle());
        updateObj.setPrecondition(reqVO.getPrecondition());
        updateObj.setKeywords(reqVO.getKeywords());
        updateObj.setPri(reqVO.getPri());
        updateObj.setType(reqVO.getType());
        updateObj.setStage(reqVO.getStage());
        updateObj.setStatus(StringUtils.hasText(reqVO.getStatus()) ? reqVO.getStatus() : old.getStatus());
        updateObj.setLastEditedBy(account);
        updateObj.setLastEditedDate(now);
        if (stepChanged) {
            updateObj.setVersion(newVersion);
            // 步骤是用例的核心，改了步骤等于换了一条用例 —— 打回「待评审」
            updateObj.setStatus(CaseStatusEnum.WAIT.getStatus());
        }
        caseMapper.updateById(updateObj);

        if (stepChanged) {
            // 追加新版本：新的一批步骤 + 一条新快照
            insertSteps(old.getId(), newVersion, normalized);
            insertSpec(old.getId(), newVersion, reqVO.getTitle(), reqVO.getPrecondition(), "");
        } else {
            // 没升版本：**原地改写当前版本的快照**，与需求模块「普通编辑改写最后一版快照」
            // 保持同一口径（禅道这里只改 zt_case、不更新 zt_casespec，
            // 会让当前版本的快照标题变成旧值，本实现有意修正）
            updateSpecInPlace(old.getId(), oldVersion, reqVO.getTitle(), reqVO.getPrecondition());
            if (reqVO.getSteps() != null) {
                // 步骤内容相同但可能顺序/分组有细微差别，仍然按提交的形态重写一遍，
                // 保证「提交什么就存什么」，避免归一化差异累积
                caseStepMapper.deleteByCaseAndVersionPhysical(old.getId(), oldVersion);
                insertSteps(old.getId(), oldVersion, normalized);
            }
        }

        actionService.recordActionWithChanges("case", old.getId(), ActionTypeEnum.EDITED,
                "编辑用例：" + reqVO.getTitle() + (stepChanged ? "（步骤变化，升到 v" + newVersion + "，待评审）" : ""),
                old, updateObj);
    }

    // ================================================================
    // 查询
    // ================================================================

    @Override
    public CaseDO validateCaseExists(Long id) {
        CaseDO caseDO = id == null ? null : caseMapper.selectById(id);
        if (caseDO == null) {
            throw exception(CASE_NOT_EXISTS, id);
        }
        return caseDO;
    }

    @Override
    public CaseRespVO getCase(Long id, Integer version) {
        CaseDO caseDO = validateCaseExists(id);
        return convert(caseDO, version == null ? 0 : version, true);
    }

    @Override
    public List<CaseStepVO> getStepList(Long id, Integer version) {
        CaseDO caseDO = validateCaseExists(id);
        int targetVersion = (version == null || version == 0) ? caseDO.getVersion() : version;
        return processSteps(caseStepMapper.selectListByCaseAndVersion(id, targetVersion));
    }

    @Override
    public List<CaseSpecRespVO> getSpecList(Long id) {
        CaseDO caseDO = validateCaseExists(id);
        List<CaseSpecDO> list = caseSpecMapper.selectListByCase(id);
        List<CaseSpecRespVO> result = new ArrayList<>(list.size());
        for (CaseSpecDO spec : list) {
            CaseSpecRespVO vo = BeanUtils.toBean(spec, CaseSpecRespVO.class);
            vo.setCurrent(Objects.equals(spec.getVersion(), caseDO.getVersion()));
            vo.setStepCount(caseStepMapper.selectListByCaseAndVersion(id, spec.getVersion()).size());
            result.add(vo);
        }
        return result;
    }

    @Override
    public PageResult<CaseRespVO> getCasePage(CasePageReqVO reqVO) {
        // 禅道行为：选中父模块时连带查出子模块下的用例（把模块展开成「自己+全部子孙」）。
        // 空集合必须直接返回空页，否则 inIfPresent 会把条件整条丢掉变成全量查询（第 16 条坑）。
        List<Long> moduleIds = null;
        if (reqVO.getModule() != null) {
            moduleIds = moduleService.getSelfAndDescendantIds(reqVO.getModule());
            if (moduleIds.isEmpty()) {
                return PageResult.empty();
            }
        }
        PageResult<CaseDO> page = caseMapper.selectPage(reqVO, moduleIds, reqVO.getNeedConfirm());
        List<CaseRespVO> list = new ArrayList<>(page.getList().size());
        for (CaseDO caseDO : page.getList()) {
            list.add(convert(caseDO, 0, false));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public List<CaseRespVO> getCaseListByStory(Long story) {
        List<CaseDO> list = caseMapper.selectListByStory(story);
        List<CaseRespVO> result = new ArrayList<>(list.size());
        for (CaseDO caseDO : list) {
            result.add(convert(caseDO, 0, false));
        }
        return result;
    }

    // ================================================================
    // 评审 / 确认需求变更 / 删除
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewCase(CaseReviewReqVO reqVO) {
        CaseDO old = validateCaseExists(reqVO.getId());
        if (!CaseStatusEnum.WAIT.getStatus().equals(old.getStatus())) {
            throw exception(CASE_NOT_WAIT_REVIEW, old.getStatus());
        }
        if (CaseStatusEnum.of(reqVO.getResult()) == null || CaseStatusEnum.WAIT.getStatus().equals(reqVO.getResult())) {
            throw exception(CASE_REVIEW_RESULT_INVALID, reqVO.getResult());
        }

        CaseDO updateObj = new CaseDO();
        updateObj.setId(old.getId());
        updateObj.setStatus(reqVO.getResult());
        updateObj.setReviewedBy(currentAccount());
        updateObj.setReviewedDate(LocalDate.now());
        caseMapper.updateById(updateObj);

        actionService.recordActionWithChanges("case", old.getId(), ActionTypeEnum.REVIEWED,
                "评审用例：" + reqVO.getComment(), old, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmStoryChange(Long id) {
        CaseDO old = validateCaseExists(id);
        if (old.getStory() == null || old.getStory() == 0) {
            throw exception(CASE_STORY_VERSION_IS_LATEST);
        }
        StoryDO story = storyService.validateStoryExists(old.getStory());
        if (Objects.equals(story.getVersion(), old.getStoryVersion())) {
            throw exception(CASE_STORY_VERSION_IS_LATEST);
        }

        CaseDO updateObj = new CaseDO();
        updateObj.setId(id);
        updateObj.setStoryVersion(story.getVersion());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        caseMapper.updateById(updateObj);

        actionService.recordAction("case", id, ActionTypeEnum.EDITED,
                "确认需求变更：需求版本 v" + old.getStoryVersion() + " → v" + story.getVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCase(Long id) {
        CaseDO caseDO = validateCaseExists(id);
        // 步骤与版本快照一起物理清理：
        // 两张表都是「用例的子数据」，留着既没意义，zt_casespec 还会占住 (case,version) 唯一键
        caseStepMapper.deleteByCasePhysical(id);
        caseSpecMapper.deleteByCasePhysical(id);
        caseMapper.deleteById(id);
        actionService.recordAction("case", id, ActionTypeEnum.DELETED, "删除用例：" + caseDO.getTitle());
    }

    // ================================================================
    // 步骤处理（层级编号 / 比较 / 落库）
    // ================================================================

    /**
     * 把数据库里的步骤转成带层级编号的展示结构 —— 禅道 {@code processSteps} 的等价实现。
     *
     * <p>数据库里只有 {@code parent}（指向步骤组），编号 {@code 1. / 1.1 / 1.1.1}
     * 是这里现算的：维护一个三级计数器，遇到更深的层级就进位、遇到更浅的层级就清零。
     */
    private List<CaseStepVO> processSteps(List<CaseStepDO> steps) {
        List<CaseStepVO> result = new ArrayList<>(steps.size());
        Map<Long, Long> parentOf = new LinkedHashMap<>();
        for (CaseStepDO step : steps) {
            parentOf.put(step.getId(), step.getParent() == null ? 0L : step.getParent());
        }
        int[] key = new int[]{0, 0, 0};
        int preGrade = 1;
        for (CaseStepDO step : steps) {
            Long parent = step.getParent() == null ? 0L : step.getParent();
            int grade = 1;
            if (parent != 0 && parentOf.containsKey(parent)) {
                Long grandParent = parentOf.get(parent);
                grade = (grandParent != null && grandParent != 0 && parentOf.containsKey(grandParent)) ? 3 : 2;
            }
            if (grade > preGrade) {
                key[grade - 1] = 1;
            } else {
                if (grade < 2) {
                    key[1] = 0;
                }
                if (grade < 3) {
                    key[2] = 0;
                }
                key[grade - 1]++;
            }
            String name = (key[0] + "." + key[1] + "." + key[2]).replace(".0", "");

            CaseStepVO vo = new CaseStepVO();
            vo.setId(step.getId());
            vo.setParent(parent);
            vo.setType(step.getType());
            vo.setDesc(step.getDesc());
            vo.setExpect(step.getExpect());
            vo.setName(name);
            vo.setGrade(grade);
            result.add(vo);
            // ★ 这一行是禅道 processSteps 里的 $preGrade = $grade，**不能少**。
            //   漏掉它，「当前层级比上一层深还是浅」的判断就永远拿第 1 步的层级去比，
            //   于是同一组下的第 2、3 个步骤会重复第一个的编号（1.1、1.1 而不是 1.1、1.2）。
            //   这是移植时最容易漏的一行 —— 算法本身写对了，但少一次状态更新。
            preGrade = grade;
        }
        return result;
    }

    /**
     * 校验并把提交的步骤整理成「父组已知」的形态。
     *
     * <p><b>接口约定</b>：{@code parent} 是**本次提交数组里父元素的 0 基下标**
     * （不是数据库 id）；**顶层步骤不传 parent（或传负数）** —— 注意 `0` 是合法下标，
     * 表示「第一行的那个步骤组」，不能拿 0 当「顶层」。用下标而不是 id 有两个好处：
     * <ul>
     *   <li>新增用例时步骤还没有 id，天然只能用下标表达层级</li>
     *   <li>编辑时前端把当前形态整体提交回来，不需要关心哪些行是新插入的</li>
     * </ul>
     * 这里把它规范化成 {@code parentIndex}（-1 表示顶层），并校验：
     * 父必须是**排在它前面**的 {@code group}、不能有 expect、层级不超过 3。
     */
    private List<CaseStepVO> normalizeSteps(List<CaseStepVO> steps) {
        if (steps == null) {
            return List.of();
        }
        List<CaseStepVO> cleaned = new ArrayList<>();
        List<Integer> parentIndexes = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            CaseStepVO step = steps.get(i);
            // 禅道会静默跳过描述为空的步骤
            if (!StringUtils.hasText(step.getDesc())) {
                continue;
            }
            String type = StringUtils.hasText(step.getType()) ? step.getType() : CaseStepTypeEnum.STEP.getType();
            if (!CaseStepTypeEnum.GROUP.getType().equals(type) && !CaseStepTypeEnum.STEP.getType().equals(type)) {
                throw exception(CASE_STEP_PARENT_INVALID, i + 1);
            }
            if (CaseStepTypeEnum.GROUP.getType().equals(type) && StringUtils.hasText(step.getExpect())) {
                throw exception(CASE_STEP_GROUP_REQUIRED, i + 1);
            }
            Long parentIndex = step.getParent();
            int resolved = -1;
            if (parentIndex != null && parentIndex >= 0) {
                // 只有「排在它前面的 group」才能当父
                if (parentIndex >= i) {
                    throw exception(CASE_STEP_PARENT_INVALID, i + 1);
                }
                resolved = resolveParentIndex(steps, parentIndex, i);
                if (resolved < 0) {
                    throw exception(CASE_STEP_PARENT_INVALID, i + 1);
                }
            }
            CaseStepVO copy = new CaseStepVO();
            copy.setType(type);
            copy.setDesc(step.getDesc());
            copy.setExpect(CaseStepTypeEnum.GROUP.getType().equals(type) ? "" : step.getExpect());
            copy.setParent((long) resolved);
            cleaned.add(copy);
            parentIndexes.add(resolved);
        }
        // 再算一遍层级，超过 3 的拒绝
        for (int i = 0; i < cleaned.size(); i++) {
            int grade = 1;
            int parent = parentIndexes.get(i);
            if (parent >= 0) {
                grade = 2;
                int grandParent = parentIndexes.get(parent);
                if (grandParent >= 0) {
                    grade = 3;
                }
            }
            if (grade > MAX_STEP_GRADE) {
                throw exception(CASE_STEP_TOO_DEEP, i + 1);
            }
            cleaned.get(i).setGrade(grade);
        }
        return cleaned;
    }

    /**
     * 把「原始数组下标」翻译成「cleaned 里的下标」。
     *
     * <p>因为 normalizeSteps 会跳过空描述的行，两个数组的下标不再一一对应，
     * 所以这里按原始顺序重建一次映射。
     */
    private int resolveParentIndex(List<CaseStepVO> raw, long rawParentIndex, int currentRawIndex) {
        // 重新走一遍 raw（只到 currentRawIndex 为止），统计哪些行被保留了
        int cleanedIndex = -1;
        for (int i = 0; i <= currentRawIndex && i < raw.size(); i++) {
            if (!StringUtils.hasText(raw.get(i).getDesc())) {
                continue;
            }
            cleanedIndex++;
            if (i == rawParentIndex) {
                String type = StringUtils.hasText(raw.get(i).getType())
                        ? raw.get(i).getType() : CaseStepTypeEnum.STEP.getType();
                if (!CaseStepTypeEnum.GROUP.getType().equals(type)) {
                    return -1;
                }
                return cleanedIndex;
            }
        }
        return -1;
    }

    /**
     * 步骤是否变化 —— 禅道 {@code processStepsChanged} 的等价实现。
     *
     * <p>比较的是**归一化后的形态**：类型、描述、预期，以及「父组在列表里的位置」。
     * 不能比较数据库 id：每次升版本都是整批重新插入，id 必然不同，
     * 拿 id 比会导致「每次都认为变了」而无限升版本。
     */
    private boolean stepsChanged(List<CaseStepVO> newSteps, Long caseId, Integer version) {
        List<CaseStepVO> normalized = normalizeSteps(newSteps);
        List<CaseStepVO> oldSteps = processSteps(caseStepMapper.selectListByCaseAndVersion(caseId, version));
        if (normalized.size() != oldSteps.size()) {
            return true;
        }
        // 老步骤的 parent 是数据库 id，换算成「在列表里的位置」后再比
        Map<Long, Integer> idToIndex = new LinkedHashMap<>();
        for (int i = 0; i < oldSteps.size(); i++) {
            idToIndex.put(oldSteps.get(i).getId(), i);
        }
        for (int i = 0; i < normalized.size(); i++) {
            CaseStepVO a = normalized.get(i);
            CaseStepVO b = oldSteps.get(i);
            if (!Objects.equals(a.getType(), b.getType())
                    || !Objects.equals(nullToEmpty(a.getDesc()), nullToEmpty(b.getDesc()))
                    || !Objects.equals(nullToEmpty(a.getExpect()), nullToEmpty(b.getExpect()))) {
                return true;
            }
            int oldParentIndex = b.getParent() == null || b.getParent() == 0 ? -1
                    : idToIndex.getOrDefault(b.getParent(), -2);
            if (oldParentIndex != (a.getParent() == null ? -1 : a.getParent().intValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 插入一批步骤。{@code parent} 是**批内下标**，这里要落成真实的行 id。
     */
    private void insertSteps(Long caseId, Integer version, List<CaseStepVO> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        // 先按顺序插入、记下每个下标生成的行 id，再回填 parent
        List<Long> idByIndex = new ArrayList<>();
        for (CaseStepVO step : steps) {
            CaseStepDO stepDO = new CaseStepDO();
            stepDO.setCaseId(caseId);
            stepDO.setVersion(version);
            stepDO.setType(step.getType());
            stepDO.setDesc(step.getDesc());
            stepDO.setExpect(step.getExpect());
            stepDO.setParent(0L);
            caseStepMapper.insert(stepDO);
            idByIndex.add(stepDO.getId());
        }
        for (int i = 0; i < steps.size(); i++) {
            Long parentIndex = steps.get(i).getParent();
            if (parentIndex == null || parentIndex < 0) {
                continue;
            }
            CaseStepDO parentUpdate = new CaseStepDO();
            parentUpdate.setId(idByIndex.get(i));
            parentUpdate.setParent(idByIndex.get(parentIndex.intValue()));
            caseStepMapper.updateById(parentUpdate);
        }
    }

    // ================================================================
    // 版本快照 / 转换 / 工具
    // ================================================================

    private void insertSpec(Long caseId, Integer version, String title, String precondition, String files) {
        CaseSpecDO spec = new CaseSpecDO();
        spec.setCaseId(caseId);
        spec.setVersion(version);
        spec.setTitle(title);
        spec.setPrecondition(precondition == null ? "" : precondition);
        spec.setFiles(files == null ? "" : files);
        caseSpecMapper.insert(spec);
    }

    /**
     * 原地改写某个版本的快照（只改标题/前置条件）。
     *
     * <p>与需求模块的「普通编辑改写最后一版快照」保持一致；若该版本还没有快照就补一条
     * （历史数据可能缺行）。
     */
    private void updateSpecInPlace(Long caseId, Integer version, String title, String precondition) {
        CaseSpecDO existed = caseSpecMapper.selectByCaseAndVersion(caseId, version);
        if (existed == null) {
            insertSpec(caseId, version, title, precondition, "");
            return;
        }
        CaseSpecDO updateObj = new CaseSpecDO();
        updateObj.setId(existed.getId());
        updateObj.setTitle(title);
        updateObj.setPrecondition(precondition);
        caseSpecMapper.updateById(updateObj);
    }

    private void validateTypeAndStage(CaseSaveReqVO reqVO) {
        if (CaseTypeEnum.of(reqVO.getType()) == null) {
            throw exception(CASE_TYPE_INVALID, reqVO.getType());
        }
        if (StringUtils.hasText(reqVO.getStage())) {
            for (String stage : reqVO.getStage().split(",")) {
                if (!StringUtils.hasText(stage)) {
                    continue;
                }
                if (CaseStageEnum.of(stage.trim()) == null) {
                    throw exception(CASE_STAGE_INVALID, stage.trim());
                }
            }
        }
        if (StringUtils.hasText(reqVO.getStatus()) && CaseStatusEnum.of(reqVO.getStatus()) == null) {
            throw exception(CASE_STATUS_INVALID, reqVO.getStatus());
        }
    }

    /**
     * 解析用例的「产品归属」。
     *
     * <p>禅道里用例的归属是二选一：产品用例 {@code product>0, lib=0}，
     * 用例库用例 {@code product=0, lib>0}（用例库本身是 {@code zt_testsuite} 里
     * {@code (product=0, type='library')} 的行）。所以这里：
     * <ul>
     *   <li>带 lib → 产品强制归零（库用例不属于任何产品）</li>
     *   <li>不带 lib 也不带 product → 拒绝（禅道表单里 product 是必填项，
     *       只是用例库入口会把它换成 lib）</li>
     * </ul>
     */
    private Long resolveProduct(CaseSaveReqVO reqVO) {
        if (reqVO.getLib() != null && reqVO.getLib() > 0) {
            return 0L;
        }
        if (reqVO.getProduct() == null) {
            throw exception(CASE_LIB_CASE_REQUIRED);
        }
        return reqVO.getProduct();
    }

    /**
     * 详情/列表转换。
     *
     * @param withSteps 列表里不带步骤（避免 N+1），只有详情才带
     */
    private CaseRespVO convert(CaseDO caseDO, Integer version, boolean withSteps) {
        CaseRespVO vo = BeanUtils.toBean(caseDO, CaseRespVO.class);
        CaseTypeEnum typeEnum = CaseTypeEnum.of(caseDO.getType());
        vo.setTypeName(typeEnum == null ? caseDO.getType() : typeEnum.getName());
        CaseStatusEnum statusEnum = CaseStatusEnum.of(caseDO.getStatus());
        vo.setStatusName(statusEnum == null ? caseDO.getStatus() : statusEnum.getName());
        vo.setStageName(stageNames(caseDO.getStage()));

        // 关联需求：标题 + 当前版本 + 是否需要确认变更
        if (caseDO.getStory() != null && caseDO.getStory() > 0) {
            StoryDO story = storyService.getStory(caseDO.getStory());
            if (story != null) {
                vo.setStoryTitle(story.getTitle());
                vo.setLatestStoryVersion(story.getVersion());
                boolean active = "active".equals(story.getStatus());
                vo.setNeedConfirm(active && story.getVersion() != null
                        && caseDO.getStoryVersion() != null
                        && story.getVersion() > caseDO.getStoryVersion());
            }
        }
        if (vo.getNeedConfirm() == null) {
            vo.setNeedConfirm(false);
        }

        if (withSteps) {
            int targetVersion = (version == null || version == 0) ? caseDO.getVersion() : version;
            // 历史版本的标题/前置条件从快照叠加（与需求「头部 + 快照」的读法一致）
            CaseSpecDO spec = caseSpecMapper.selectByCaseAndVersion(caseDO.getId(), targetVersion);
            if (spec != null) {
                vo.setTitle(spec.getTitle());
                vo.setPrecondition(spec.getPrecondition());
            }
            vo.setSteps(processSteps(caseStepMapper.selectListByCaseAndVersion(caseDO.getId(), targetVersion)));
        }
        return vo;
    }

    private String stageNames(String stage) {
        if (!StringUtils.hasText(stage)) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (String item : stage.split(",")) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            CaseStageEnum stageEnum = CaseStageEnum.of(item.trim());
            names.add(stageEnum == null ? item.trim() : stageEnum.getName());
        }
        return String.join(",", names);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 取当前登录用户的账号。与 StoryServiceImpl / DocServiceImpl 保持同样的口径。
     */
    private String currentAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : "";
    }

}
