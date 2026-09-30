package cn.iocoder.yudao.module.zentao.service.caselib;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.caselib.vo.CaseLibPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.caselib.vo.CaseLibRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.caselib.vo.CaseLibSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CasePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseStepVO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.module.ModuleDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testcase.CaseDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testcase.CaseStepDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testreport.TestSuiteDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.module.ModuleMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testcase.CaseMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testcase.CaseStepMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.testreport.TestSuiteMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.module.ModuleTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.testreport.TestSuiteTypeEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import cn.iocoder.yudao.module.zentao.service.action.ActionServiceImpl;
import cn.iocoder.yudao.module.zentao.service.testcase.CaseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 用例库 Service 实现。
 *
 * <p>业务规则来源：禅道 {@code module/caselib/*} + {@code module/testcase/model.php} 的
 * {@code importToLib} / {@code getCanImportCases}。
 *
 * <h3>数据模型（三张已有表，不新建表）</h3>
 * <pre>
 *   用例库    zt_testsuite (product=0, type='library')
 *   库内用例  zt_case      (product=0, lib=&lt;库编号&gt;)，用例的模块树是 (root=库编号, type='caselib')
 *   用例集    zt_testsuite (product=&lt;产品&gt;, type in ('public','private'))
 * </pre>
 * 也就是说用例库与用例集共用 {@code zt_testsuite}，与产品用例共用 {@code zt_case} ——
 * 两边查询都必须带区分条件，否则互相串数据（README 坑位 #12）。
 *
 * <h3>与禅道的差异</h3>
 * <ul>
 *   <li>禅道删用例库不做任何检查（直接软删，库里那批用例变成孤儿）；本实现沿用项目里
 *       「父对象还有子对象时拒绝删除」的统一策略，库内还有用例时拒绝删除。</li>
 *   <li>禅道向库里导入用例时按 {@code fromCaseID} 判重、重复导入会把库里那条覆盖成最新版本
 *       （并给来源用例的 {@code fromCaseVersion} +1）；本实现**拒绝重复导入**，
 *       只提供「源用例已更新」标记，把「要不要同步」交给使用者决定。</li>
 * </ul>
 */
@Slf4j
@Service
public class CaseLibServiceImpl implements CaseLibService {

    @Resource
    private TestSuiteMapper suiteMapper;

    @Resource
    private CaseMapper caseMapper;

    @Resource
    private CaseStepMapper caseStepMapper;

    @Resource
    private ModuleMapper moduleMapper;

    @Resource
    private CaseService caseService;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ================================================================
    // 用例库 CRUD
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLib(CaseLibSaveReqVO reqVO) {
        validateLibNameUnique(reqVO.getName(), null);
        String account = currentAccount();
        TestSuiteDO lib = new TestSuiteDO();
        // 用例库的两个「身份标记」：product=0 + type='library'
        lib.setProduct(0L);
        lib.setProject(0L);
        lib.setType(TestSuiteTypeEnum.LIBRARY.getType());
        lib.setName(reqVO.getName());
        lib.setDesc(reqVO.getDesc() == null ? "" : reqVO.getDesc());
        lib.setOrder(reqVO.getOrder() == null ? 0 : reqVO.getOrder());
        lib.setAddedBy(account);
        lib.setAddedDate(LocalDateTime.now());
        lib.setLastEditedBy(account);
        lib.setLastEditedDate(LocalDateTime.now());
        suiteMapper.insert(lib);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_CASELIB, lib.getId(),
                ActionTypeEnum.CREATED, "新建用例库：" + lib.getName());
        return lib.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLib(CaseLibSaveReqVO reqVO) {
        TestSuiteDO old = validateLibExists(reqVO.getId());
        validateLibNameUnique(reqVO.getName(), reqVO.getId());
        TestSuiteDO updateObj = new TestSuiteDO();
        updateObj.setId(old.getId());
        updateObj.setName(reqVO.getName());
        updateObj.setDesc(reqVO.getDesc());
        updateObj.setOrder(reqVO.getOrder());
        updateObj.setLastEditedBy(currentAccount());
        updateObj.setLastEditedDate(LocalDateTime.now());
        suiteMapper.updateById(updateObj);
        actionService.recordActionWithChanges(ActionServiceImpl.OBJECT_TYPE_CASELIB, old.getId(),
                ActionTypeEnum.EDITED, "修改用例库：" + reqVO.getName(), old, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLib(Long id) {
        TestSuiteDO lib = validateLibExists(id);
        long caseCount = countLibCase(id);
        if (caseCount > 0) {
            throw exception(CASE_LIB_HAS_CASE, caseCount);
        }
        // 逻辑删除（BaseDO 的 @TableLogic），禅道 caselib/delete 也是 set deleted=1
        suiteMapper.deleteById(id);
        actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_CASELIB, id,
                ActionTypeEnum.DELETED, "删除用例库：" + lib.getName());
    }

    @Override
    public TestSuiteDO validateLibExists(Long id) {
        TestSuiteDO lib = id == null ? null : suiteMapper.selectById(id);
        if (lib == null) {
            throw exception(CASE_LIB_NOT_EXISTS, id);
        }
        // 共用表：编号存在不等于「它是个用例库」——用例集/私有集都在同一张表里
        if (!TestSuiteTypeEnum.LIBRARY.getType().equals(lib.getType())) {
            throw exception(CASE_LIB_NOT_A_LIBRARY, id);
        }
        return lib;
    }

    @Override
    public CaseLibRespVO getLib(Long id) {
        return convertLib(validateLibExists(id));
    }

    @Override
    public PageResult<CaseLibRespVO> getLibPage(CaseLibPageReqVO reqVO) {
        PageResult<TestSuiteDO> page = suiteMapper.selectLibPage(reqVO, reqVO.getName());
        List<CaseLibRespVO> list = new ArrayList<>(page.getList().size());
        for (TestSuiteDO lib : page.getList()) {
            list.add(convertLib(lib));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public List<CaseLibRespVO> getLibList() {
        List<TestSuiteDO> libs = suiteMapper.selectLibList();
        List<CaseLibRespVO> list = new ArrayList<>(libs.size());
        for (TestSuiteDO lib : libs) {
            list.add(convertLib(lib));
        }
        return list;
    }

    // ================================================================
    // 库内用例
    // ================================================================

    @Override
    public PageResult<CaseRespVO> getLibCasePage(Long libId, CasePageReqVO reqVO) {
        validateLibExists(libId);
        // 强制按 lib 过滤（调用方传了 product 也忽略 —— 库用例不属于任何产品）
        reqVO.setLib(libId);
        reqVO.setProduct(null);
        PageResult<CaseRespVO> page = caseService.getCasePage(reqVO);
        fillSourceChanged(page.getList());
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLibCase(Long libId, CaseSaveReqVO reqVO) {
        validateLibExists(libId);
        // lib 由路径参数决定，不接受 body 里再传一个（避免「参数里的库」和「路径里的库」不一致）
        reqVO.setLib(libId);
        reqVO.setProduct(0L);
        // 库用例不关联需求：清掉，免得带着产品的需求编号进来
        reqVO.setStory(0L);
        reqVO.setBranch(0L);
        return caseService.createCase(reqVO);
    }

    @Override
    public CaseRespVO getLibCase(Long caseId) {
        CaseRespVO vo = caseService.getCase(caseId, 0);
        fillSourceChanged(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<CaseRespVO> getCanImportCasePage(Long libId, Long product, String title, PageParam pageParam) {
        validateLibExists(libId);
        if (product == null) {
            throw exception(CASE_LIB_CASE_REQUIRED);
        }
        Set<Long> imported = importedSourceIds(libId);
        PageResult<CaseDO> page = caseMapper.selectCanImportPage(product, title, imported, pageParam);
        List<CaseRespVO> list = new ArrayList<>(page.getList().size());
        for (CaseDO caseDO : page.getList()) {
            list.add(caseService.getCase(caseDO.getId(), 0));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> importToLib(Long libId, List<Long> caseIds) {
        validateLibExists(libId);
        if (caseIds == null || caseIds.isEmpty()) {
            throw exception(CASE_LIB_CASE_REQUIRED);
        }
        Set<Long> imported = importedSourceIds(libId);
        List<Long> result = new ArrayList<>(caseIds.size());
        for (Long caseId : caseIds) {
            CaseDO source = caseId == null ? null : caseMapper.selectById(caseId);
            if (source == null) {
                throw exception(CASE_NOT_EXISTS, caseId);
            }
            if (source.getLib() != null && source.getLib() > 0) {
                throw exception(CASE_LIB_IMPORT_NOT_A_PRODUCT_CASE, caseId);
            }
            if (imported.contains(caseId)) {
                throw exception(CASE_LIB_IMPORT_ALREADY, caseId);
            }
            // 复制成库内用例：标题/前置条件/关键词/优先级/类型/环节 + 当前版本的全部步骤
            CaseSaveReqVO reqVO = new CaseSaveReqVO();
            reqVO.setLib(libId);
            reqVO.setProduct(0L);
            reqVO.setModule(mapModuleToLib(source.getModule(), libId));
            reqVO.setTitle(source.getTitle());
            reqVO.setPrecondition(source.getPrecondition());
            reqVO.setKeywords(source.getKeywords());
            reqVO.setPri(source.getPri());
            reqVO.setType(source.getType());
            reqVO.setStage(source.getStage());
            // 状态跟着来源走：来源是「正常」就导入成「正常」，来源待评审就还是待评审
            reqVO.setStatus(source.getStatus());
            reqVO.setSteps(toBatchSteps(
                    caseStepMapper.selectListByCaseAndVersion(source.getId(),
                            source.getVersion() == null ? 1 : source.getVersion())));
            Long newCaseId = caseService.createCase(reqVO);

            // 回填来源（markImported）：禅道 testcase/importToLib 记的就是这两个字段
            CaseDO mark = new CaseDO();
            mark.setId(newCaseId);
            mark.setFromCaseID(source.getId());
            mark.setFromCaseVersion(source.getVersion() == null ? 1 : source.getVersion());
            caseMapper.updateById(mark);

            actionService.recordAction(ActionServiceImpl.OBJECT_TYPE_CASELIB, libId,
                    ActionTypeEnum.EDITED, "从产品用例 " + source.getId() + " 导入用例：" + source.getTitle());
            result.add(newCaseId);
            imported.add(caseId);
        }
        return result;
    }

    // ================================================================
    // 内部
    // ================================================================

    private long countLibCase(Long libId) {
        Long count = caseMapper.selectCount(new LambdaQueryWrapperX<CaseDO>()
                .eq(CaseDO::getLib, libId));
        return count == null ? 0L : count;
    }

    /**
     * 用例库名称唯一：禅道 {@code caselib/create} 的
     * {@code check('name', 'unique', "deleted = '0'")} 是**整张 zt_testsuite 全局唯一**
     * （不区分用例集/用例库），这里照搬。已逻辑删除的行不参与判重。
     */
    private void validateLibNameUnique(String name, Long excludeId) {
        List<TestSuiteDO> list = suiteMapper.selectList(new LambdaQueryWrapperX<TestSuiteDO>()
                .eq(TestSuiteDO::getName, name));
        for (TestSuiteDO existed : list) {
            if (!Objects.equals(existed.getId(), excludeId)) {
                throw exception(CASE_LIB_NAME_DUPLICATE, name);
            }
        }
    }

    private CaseLibRespVO convertLib(TestSuiteDO lib) {
        CaseLibRespVO vo = BeanUtils.toBean(lib, CaseLibRespVO.class);
        vo.setCaseCount(countLibCase(lib.getId()));
        return vo;
    }

    /**
     * 补「源用例已更新」标记：库内用例的来源产品用例版本 > 导入时冻结的版本。
     *
     * <p>与需求/用例的「版本冻结」是同一个套路：导入时把来源版本冻在
     * {@code fromCaseVersion}，来源后来升版只提示、不自动同步。
     */
    private void fillSourceChanged(List<CaseRespVO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> sourceIds = new ArrayList<>();
        for (CaseRespVO vo : list) {
            if (vo.getFromCaseID() != null && vo.getFromCaseID() > 0) {
                sourceIds.add(vo.getFromCaseID());
            }
        }
        Map<Long, Integer> sourceVersions = new HashMap<>();
        for (Long sourceId : sourceIds) {
            CaseDO source = caseMapper.selectById(sourceId);
            if (source != null) {
                sourceVersions.put(sourceId, source.getVersion() == null ? 1 : source.getVersion());
            }
        }
        for (CaseRespVO vo : list) {
            Integer sourceVersion = vo.getFromCaseID() == null ? null : sourceVersions.get(vo.getFromCaseID());
            int frozen = vo.getFromCaseVersion() == null ? 1 : vo.getFromCaseVersion();
            vo.setSourceChanged(sourceVersion != null && sourceVersion > frozen);
        }
    }

    private Set<Long> importedSourceIds(Long libId) {
        Set<Long> ids = new HashSet<>();
        for (CaseDO caseDO : caseMapper.selectListByLib(libId)) {
            if (caseDO.getFromCaseID() != null && caseDO.getFromCaseID() > 0) {
                ids.add(caseDO.getFromCaseID());
            }
        }
        return ids;
    }

    /**
     * 把产品用例的模块映射到用例库的模块树（禅道 {@code importCaseRelatedModules}）。
     *
     * <p>产品的用例模块树是 {@code (root=产品, type='case')}，库的是
     * {@code (root=库, type='caselib')}，两棵树互不相通，所以导入用例时要把模块也带过来：
     * <ul>
     *   <li>{@code zt_module.from} 记住来源模块编号 —— 已经导入过的模块直接复用，不重复建</li>
     *   <li>父模块先递归导入，保证树形结构（父在子前），path/grade 按项目里的通用树规则重算</li>
     * </ul>
     */
    private Long mapModuleToLib(Long sourceModuleId, Long libId) {
        if (sourceModuleId == null || sourceModuleId <= 0) {
            return 0L;
        }
        // 已经导入过（同 from）→ 直接复用
        for (ModuleDO existed : moduleMapper.selectList(libId, ModuleTypeEnum.CASELIB.getType(), 0L)) {
            if (Objects.equals(existed.getFrom(), sourceModuleId.intValue())) {
                return existed.getId();
            }
        }
        ModuleDO source = moduleMapper.selectById(sourceModuleId);
        if (source == null) {
            return 0L;
        }
        Long newParentId = source.getParent() == null ? 0L : mapModuleToLib(source.getParent(), libId);

        ModuleDO copy = new ModuleDO();
        copy.setRoot(libId);
        copy.setType(ModuleTypeEnum.CASELIB.getType());
        copy.setBranch(0L);
        copy.setName(source.getName());
        copy.setParent(newParentId);
        copy.setGrade(1);
        copy.setOrder(source.getOrder());
        copy.setFrom(sourceModuleId.intValue());
        moduleMapper.insert(copy);

        // path 与 grade 要以**新父**为准重算（禅道 processNode 的同一套规则）
        ModuleDO parent = newParentId > 0 ? moduleMapper.selectById(newParentId) : null;
        ModuleDO tree = new ModuleDO();
        tree.setId(copy.getId());
        tree.setPath(parent == null || !StringUtils.hasText(parent.getPath())
                ? "," + copy.getId() + "," : parent.getPath() + copy.getId() + ",");
        tree.setGrade(parent == null || parent.getGrade() == null ? 1 : parent.getGrade() + 1);
        moduleMapper.updateById(tree);
        return copy.getId();
    }

    /**
     * 把库里存的步骤（{@code parent} 是真实的步骤编号）转成「批量提交」形态
     * （{@code parent} 是同一批数组里的下标）—— 这是 {@code normalizeSteps} 约定的入参格式。
     */
    private List<CaseStepVO> toBatchSteps(List<CaseStepDO> storedSteps) {
        List<CaseStepVO> list = new ArrayList<>();
        if (storedSteps == null || storedSteps.isEmpty()) {
            return list;
        }
        Map<Long, Integer> idToIndex = new HashMap<>();
        for (CaseStepDO stored : storedSteps) {
            CaseStepVO vo = new CaseStepVO();
            vo.setType(stored.getType());
            vo.setDesc(stored.getDesc());
            vo.setExpect(stored.getExpect());
            Long parentId = stored.getParent();
            if (parentId == null || parentId <= 0) {
                vo.setParent(-1L);
            } else {
                Integer index = idToIndex.get(parentId);
                // 步骤表按 id 升序取出，父步骤组一定排在子步骤之前；万一没找到就按顶层处理
                vo.setParent(index == null ? -1L : index.longValue());
            }
            idToIndex.put(stored.getId(), list.size());
            list.add(vo);
        }
        return list;
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
