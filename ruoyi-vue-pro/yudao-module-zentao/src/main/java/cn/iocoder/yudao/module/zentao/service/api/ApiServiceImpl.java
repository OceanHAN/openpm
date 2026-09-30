package cn.iocoder.yudao.module.zentao.service.api;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiDetailRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiLibRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiReleaseRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiReleaseSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiStructPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiStructRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiStructSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiVersionVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.api.ApiDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.api.ApiLibReleaseDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.api.ApiSpecDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.api.ApiStructDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.api.ApiStructSpecDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.doc.DocLibDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.module.ModuleDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.api.ApiLibReleaseMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.api.ApiMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.api.ApiSpecMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.api.ApiStructMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.api.ApiStructSpecMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.doc.DocLibMapper;
import cn.iocoder.yudao.module.zentao.enums.api.ApiParamsScopeEnum;
import cn.iocoder.yudao.module.zentao.enums.api.ApiStatusEnum;
import cn.iocoder.yudao.module.zentao.service.action.ChangeDetector;
import cn.iocoder.yudao.module.zentao.service.module.ModuleService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.API_LIB_NOT_EXISTS;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.API_EDITED_BY_OTHER;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.API_FROZEN_BY_RELEASE;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.API_NOT_EXISTS;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.API_PATH_DUPLICATE;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.API_REFERENCED_BY_STRUCT;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.API_RELEASE_VERSION_DUPLICATE;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.API_TITLE_DUPLICATE;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.API_VERSION_NOT_EXISTS;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.API_RELEASE_NOT_EXISTS;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.API_STRUCT_NOT_EXISTS;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.API_TITLE_REQUIRED;

/**
 * 接口文档库（禅道 {@code module/api}）。
 *
 * <h3>三处最容易做错的地方，代码里都留了注释</h3>
 * <ol>
 *   <li><b>版本号什么时候 +1</b>：接口是「先 {@code createChanges}，真有变更才 +1」
 *       （{@code model.php:139}），结构是「无条件 +1」（{@code control.php:544}）。
 *       而且接口的 spec 是**先 DELETE 再 INSERT**（{@code model.php:162}）——
 *       等价于原地重写当前版本，所以一个 (doc, version) 永远只有一行。</li>
 *   <li><b>发布是快照</b>：{@code snap} 里存的是 {@code (id, version)}，
 *       内容仍在 spec 表里；按发布浏览时先读 snap 拿版本号，再回查 spec。</li>
 *   <li><b>库不在本模块</b>：接口库是 {@code zt_doclib} 的 {@code type='api'} 记录，
 *       目录是 {@code zt_module} 的 {@code type='api'} 节点，都只读不建。</li>
 * </ol>
 *
 * <p><b>两处有意偏离</b>（都写在对应方法的注释里）：
 * ① 编辑接口时 {@code null} 表示「这一项不改」（禅道整表单提交会把没填的写成默认值）；
 * ② 删除接口/结构时加了一道禅道没有的引用保护。
 */
@Slf4j
@Service
public class ApiServiceImpl implements ApiService {

    /** 接口库类型（{@code zt_doclib.type}） */
    private static final String LIB_TYPE_API = "api";

    /** 目录树类型（{@code zt_module.type}） */
    private static final String MODULE_TYPE_API = "api";

    /**
     * 请求参数树的默认形状。
     *
     * <p>照抄禅道 {@code ui/create.html.php:346}：
     * {@code formHidden('params', '{"header":[],"params":[],"paramsType":"formData","query":[]}')}。
     * 新建接口时如果调用方没给 params，就落这个默认值（禅道的表单永远会提交它）。
     */
    private static final String DEFAULT_PARAMS =
            "{\"header\":[],\"params\":[],\"paramsType\":\"formData\",\"query\":[]}";

    /** 请求体的默认类型（{$lang->struct->typeOptions} 的默认项） */
    private static final String DEFAULT_PARAMS_TYPE = "formData";

    /** 结构类型默认值（{@code form->createStruct['type']['default']}） */
    private static final String DEFAULT_STRUCT_TYPE = "formData";

    /** 快照 JSON 里判断「结构引用了哪个接口」时要看的字段名（paramsType 是禅道原生的那个） */
    private static final List<String> REF_KEYS = List.of("paramsType", "refTarget", "apiRef", "ref");

    @Resource
    private ApiMapper apiMapper;

    @Resource
    private ApiSpecMapper apiSpecMapper;

    @Resource
    private ApiStructMapper apiStructMapper;

    @Resource
    private ApiStructSpecMapper apiStructSpecMapper;

    @Resource
    private ApiLibReleaseMapper apiLibReleaseMapper;

    /** 接口库/目录分别复用已迁移的 doc 与 module：本模块只读它们 */
    @Resource
    private DocLibMapper docLibMapper;

    @Resource
    private ModuleService moduleService;

    @Resource
    private AdminUserApi adminUserApi;

    // ==================================================================
    // 接口库（zt_doclib，只读）
    // ==================================================================

    @Override
    public List<ApiLibRespVO> getLibList() {
        List<DocLibDO> libs = docLibMapper.selectListByObject(LIB_TYPE_API, null);
        List<ApiLibRespVO> result = new ArrayList<>(libs.size());
        for (DocLibDO lib : libs) {
            ApiLibRespVO vo = BeanUtils.toBean(lib, ApiLibRespVO.class);
            if (vo == null) {
                continue;
            }
            // 计数：禅道在接口空间首页用 ajaxGetHome 算同样的数（有接口的库才显示）
            vo.setApiCount(Math.toIntExact(apiMapper.selectCount(
                    new LambdaQueryWrapperX<ApiDO>().eq(ApiDO::getLib, lib.getId()))));
            vo.setStructCount(apiStructMapper.selectListByLib(lib.getId()).size());
            result.add(vo);
        }
        return result;
    }

    /**
     * 校验接口库存在、且确实是接口库（{@code type='api'}）。
     *
     * <p><b>这是本实现加的校验</b>：禅道 {@code api::create()} 拿到 libID 直接写库，从不检查它
     * （{@code control.php:710}）。加这道校验是为了拦住「把产品文档库的 id 当接口库传进来」，
     * 那种情况下接口会挂到一个不会出现在接口页面的库上。
     */
    private DocLibDO validateLibExists(Long lib) {
        if (lib == null) {
            throw exception(API_LIB_NOT_EXISTS, "0");
        }
        DocLibDO docLib = docLibMapper.selectById(lib);
        if (docLib == null || !LIB_TYPE_API.equals(docLib.getType())) {
            throw exception(API_LIB_NOT_EXISTS, lib);
        }
        return docLib;
    }

    // ==================================================================
    // 接口：查
    // ==================================================================

    @Override
    public PageResult<ApiRespVO> getApiPage(ApiPageReqVO reqVO) {
        // 按发布版本浏览：走冻结快照（禅道 getListByModuleID 的 release 分支）
        if (reqVO.getReleaseID() != null && reqVO.getReleaseID() > 0) {
            return getApiPageByRelease(reqVO);
        }

        // 目录过滤要把目录展开成「自己 + 全部子孙」；展开为空必须直接返回空页，
        // 否则 inIfPresent 会把条件整条丢掉变成全量查询（README 第 16 条坑）
        Collection<Long> moduleIds = expandModule(reqVO.getModule());
        if (moduleIds != null && moduleIds.isEmpty()) {
            return PageResult.empty();
        }
        PageResult<ApiDO> page = apiMapper.selectPage(reqVO, moduleIds);
        List<ApiRespVO> list = new ArrayList<>(page.getList().size());
        Map<Long, String> libNames = libNames(collectLibIds(page.getList()));
        Map<Long, Map<Long, String>> moduleNames = new HashMap<>();
        Map<Long, Integer> versionCounts = versionCounts(ids(page.getList()));
        for (ApiDO api : page.getList()) {
            ApiRespVO vo = toVO(api, libNames.get(api.getLib()),
                    moduleNames.computeIfAbsent(api.getLib(), this::moduleNames));
            vo.setVersionCount(versionCounts.getOrDefault(api.getId(), 0));
            list.add(vo);
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public ApiDetailRespVO getApiDetail(Long id, Integer version, Long releaseID) {
        Integer viewingVersion = version == null ? 0 : version;

        // ① 发布版本优先：从 snap 里查这个接口被冻结在第几版
        String releaseVersion = null;
        if (releaseID != null && releaseID > 0) {
            ApiLibReleaseDO release = validateReleaseExists(releaseID);
            releaseVersion = release.getVersion();
            Integer frozen = findFrozenVersion(release.getSnap(), id);
            // 禅道 getByID 的行为：snap 里没有这个接口时 $version 保持原值（0）→ 返回当前值，
            // 不报错。这里照抄（宁可给当前值，也别让详情页打不开）
            if (frozen != null && frozen > 0) {
                viewingVersion = frozen;
            }
        }

        ApiDO api;
        ApiSpecDO spec = null;
        if (viewingVersion != null && viewingVersion > 0) {
            // 历史版本：接口行可能已被逻辑删除，但历史照样可读（禅道 findByID 不过滤 deleted）
            api = apiMapper.selectListByIdsIgnoreDeleted(List.of(id)).stream().findFirst().orElse(null);
            if (api == null) {
                throw exception(API_NOT_EXISTS);
            }
            spec = apiSpecMapper.selectByDocAndVersion(id, viewingVersion);
            if (spec == null) {
                throw exception(API_VERSION_NOT_EXISTS, viewingVersion);
            }
        } else {
            api = validateApiExists(id);
        }

        ApiDetailRespVO vo = BeanUtils.toBean(api, ApiDetailRespVO.class);
        vo.setLibName(libNames(List.of(api.getLib())).get(api.getLib()));
        vo.setModuleName(moduleNames(api.getLib()).getOrDefault(api.getModule() == null ? 0L : api.getModule(), ""));
        vo.setStatusName(ApiStatusEnum.textOf(spec != null ? spec.getStatus() : api.getStatus()));
        if (spec != null) {
            // 历史版本：内容取 spec，库/产品/最后修改人取主表 —— 与 model.php:329 的字段清单一致
            applySpecContent(vo, spec);
        }
        vo.setViewingVersion(spec == null ? 0 : spec.getVersion());
        vo.setReleaseID(releaseID == null || releaseID <= 0 ? null : releaseID);
        vo.setReleaseVersion(releaseVersion);
        vo.setVersionList(versionList(id));
        vo.setVersionCount(apiSpecMapper.countByDoc(id));
        return vo;
    }

    /** 禅道 {@code getByID} 里「从 snap 找出该接口被冻结的版本号」那一段（{@code model.php:317-323}） */
    private Integer findFrozenVersion(String snapJson, Long apiId) {
        for (ApiReleaseRespVO.SnapItemVO item : parseSnap(snapJson).apis) {
            if (Objects.equals(item.getId(), apiId)) {
                return item.getVersion();
            }
        }
        return null;
    }

    /** 版本链（新版本在前） */
    private List<ApiVersionVO> versionList(Long apiId) {
        List<ApiVersionVO> list = new ArrayList<>();
        for (ApiSpecDO spec : apiSpecMapper.selectListByDoc(apiId)) {
            ApiVersionVO vo = new ApiVersionVO();
            vo.setVersion(spec.getVersion());
            vo.setAddedBy(spec.getAddedBy());
            vo.setAddedDate(spec.getAddedDate());
            list.add(vo);
        }
        return list;
    }

    // ==================================================================
    // 接口：写
    // ==================================================================

    @Override
    public Long createApi(ApiSaveReqVO reqVO) {
        String title = trimToEmpty(reqVO.getTitle());
        if (!StringUtils.hasText(title)) {
            throw exception(API_TITLE_REQUIRED);
        }
        validateLibExists(reqVO.getLib());
        Long module = reqVO.getModule() == null ? 0L : reqVO.getModule();
        String path = trimToEmpty(reqVO.getPath());
        String method = defaultIfBlank(reqVO.getMethod(), "GET");
        validateUnique(title, path, reqVO.getLib(), module, method, null);

        String account = currentAccount();
        LocalDateTime now = LocalDateTime.now();
        ApiDO api = new ApiDO();
        // 禅道 control.php:713 的 add()：product 固定 0、version 固定 1、addedDate=editedDate=now
        api.setProduct(0L);
        api.setLib(reqVO.getLib());
        api.setModule(module);
        api.setTitle(title);
        api.setPath(path);
        api.setProtocol(defaultIfBlank(reqVO.getProtocol(), "HTTP"));
        api.setMethod(method);
        api.setRequestType(trimToEmpty(reqVO.getRequestType()));
        api.setResponseType("");
        api.setStatus(defaultIfBlank(reqVO.getStatus(), "done"));
        api.setOwner(trimToEmpty(reqVO.getOwner()));
        api.setDesc(trimToEmpty(reqVO.getDesc()));
        api.setVersion(1);
        api.setParams(normalizeParams(reqVO.getParams()));
        api.setParamsExample(trimToEmpty(reqVO.getParamsExample()));
        api.setResponseExample(trimToEmpty(reqVO.getResponseExample()));
        api.setResponse(trimToEmpty(reqVO.getResponse()));
        api.setCommonParams("");
        api.setAddedBy(account);
        api.setAddedDate(now);
        api.setEditedDate(now);
        apiMapper.insert(api);

        // 版本链第一格（禅道 model.php:110-112）
        saveSpec(api, 1, now, account);
        return api.getId();
    }

    @Override
    public void updateApi(ApiSaveReqVO reqVO) {
        ApiDO old = validateApiExists(reqVO.getId());

        // 乐观锁：禅道 model.php:130-135「提交里的 editedDate 与库里不一致 = 别人已经改过」
        if (reqVO.getEditedDate() != null && !reqVO.getEditedDate().equals(old.getEditedDate())) {
            throw exception(API_EDITED_BY_OTHER);
        }

        // 只把「调用方真的传了的字段」放进变更集：
        // 禅道是整表单提交（没填的会被写成默认值），yudao 这边把 null 当作「这一项不改」。
        // 前端提交的也是完整表单，所以实际行为一致；差异只影响「只传部分字段」的调用方。
        ApiDO patch = new ApiDO();
        patch.setModule(reqVO.getModule());
        if (reqVO.getTitle() != null) {
            String title = reqVO.getTitle().trim();
            if (!StringUtils.hasText(title)) {
                throw exception(API_TITLE_REQUIRED);
            }
            patch.setTitle(title);
        }
        patch.setPath(reqVO.getPath());
        patch.setProtocol(reqVO.getProtocol());
        patch.setMethod(reqVO.getMethod());
        patch.setRequestType(reqVO.getRequestType());
        patch.setStatus(reqVO.getStatus());
        patch.setOwner(reqVO.getOwner());
        patch.setDesc(reqVO.getDesc());
        patch.setParams(reqVO.getParams() == null ? null : normalizeParams(reqVO.getParams()));
        patch.setParamsExample(reqVO.getParamsExample());
        patch.setResponse(reqVO.getResponse());
        patch.setResponseExample(reqVO.getResponseExample());

        // 唯一性校验用「合并后的值」——禅道是在整个 formData 上做 check
        Long mergedModule = patch.getModule() == null ? old.getModule() : patch.getModule();
        String mergedTitle = patch.getTitle() == null ? old.getTitle() : patch.getTitle();
        String mergedPath = patch.getPath() == null ? old.getPath() : patch.getPath();
        String mergedMethod = patch.getMethod() == null ? old.getMethod() : patch.getMethod();
        validateUnique(mergedTitle, mergedPath, old.getLib(), mergedModule, mergedMethod, old.getId());

        // 「真有变更才 +1」——禅道 model.php:139-140
        boolean changed = !ChangeDetector.detect(old, patch).isEmpty();
        int newVersion = changed ? old.getVersion() + 1 : old.getVersion();

        String account = currentAccount();
        LocalDateTime now = LocalDateTime.now();

        // 更新头部：只写传了的字段 + 版本号 + 修改人/时间（responseType / commonParams / lib 不动）
        ApiDO updateObj = new ApiDO();
        updateObj.setId(old.getId());
        updateObj.setModule(patch.getModule());
        updateObj.setTitle(patch.getTitle());
        updateObj.setPath(patch.getPath());
        updateObj.setProtocol(patch.getProtocol());
        updateObj.setMethod(patch.getMethod());
        updateObj.setRequestType(patch.getRequestType());
        updateObj.setStatus(patch.getStatus());
        updateObj.setOwner(patch.getOwner());
        updateObj.setDesc(patch.getDesc());
        updateObj.setParams(patch.getParams());
        updateObj.setParamsExample(patch.getParamsExample());
        updateObj.setResponse(patch.getResponse());
        updateObj.setResponseExample(patch.getResponseExample());
        updateObj.setVersion(newVersion);
        updateObj.setEditedBy(account);
        updateObj.setEditedDate(now);
        apiMapper.updateById(updateObj);

        // spec：先 DELETE (doc, version) 再 INSERT —— 等价于原地重写当前版本（model.php:162-163）。
        // 内容用「合并后的当前值」，并且空值按禅道 getApiSpecByData() 的默认值补齐。
        ApiDO merged = merge(old, updateObj, newVersion);
        apiSpecMapper.deleteByDocAndVersion(old.getId(), newVersion);
        saveSpec(merged, newVersion, now, account);
    }

    @Override
    public void deleteApi(Long id) {
        ApiDO api = validateApiExists(id);

        // 保护①：被发布版本冻结（发布快照的 apis 里有这个编号）
        ApiLibReleaseDO frozen = findFrozenRelease(api.getLib(), id);
        if (frozen != null) {
            throw exception(API_FROZEN_BY_RELEASE, api.getTitle() + "」所在的发布版本「"
                    + frozen.getVersion() + "」冻结，请先删除该发布版本再删除接口");
        }
        // 保护②：被数据结构引用（结构字段树里 type 指向这个接口编号）
        ApiStructDO referrer = findStructReferencing(api.getLib(), id);
        if (referrer != null) {
            throw exception(API_REFERENCED_BY_STRUCT, api.getTitle() + "」被数据结构「"
                    + referrer.getName() + "」引用，请先解除引用再删除");
        }

        // 逻辑删除**只删头部**：zt_apispec 的行保留，这样已发布的历史版本仍然读得出来
        // （禅道 control.php:784 也是只 delete TABLE_API）
        apiMapper.deleteById(id);
    }

    // ==================================================================
    // 数据结构
    // ==================================================================

    @Override
    public PageResult<ApiStructRespVO> getStructPage(ApiStructPageReqVO reqVO) {
        if (reqVO.getReleaseID() != null && reqVO.getReleaseID() > 0) {
            return getStructPageByRelease(reqVO);
        }
        PageResult<ApiStructDO> page = apiStructMapper.selectPage(reqVO);
        List<ApiStructRespVO> list = new ArrayList<>(page.getList().size());
        Map<Long, String> libNames = libNames(collectStructLibIds(page.getList()));
        Map<String, String> realnames = realnames(accounts(page.getList()));
        for (ApiStructDO struct : page.getList()) {
            ApiStructRespVO vo = toStructVO(struct, null);
            vo.setLibName(libNames.get(struct.getLib()));
            vo.setAddedName(realnames.getOrDefault(struct.getAddedBy(), struct.getAddedBy()));
            vo.setVersionCount(apiStructSpecMapper.countByName(struct.getName()));
            list.add(vo);
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public ApiStructRespVO getStructDetail(Long id, Integer version) {
        ApiStructDO struct = validateStructExists(id);
        ApiStructSpecDO spec = null;
        if (version != null && version > 0 && !version.equals(struct.getVersion())) {
            // 禅道没有「按版本读结构」的入口，但发布回溯是这么用的：
            // getStructListByRelease 按 (name, version) 取冻结版本的 spec（model.php:497-518）
            spec = apiStructSpecMapper.selectByNameAndVersion(struct.getName(), version);
            if (spec == null) {
                throw exception(API_STRUCT_NOT_EXISTS, "结构「" + struct.getName() + "」没有第 " + version + " 版");
            }
        }
        ApiStructRespVO vo = toStructVO(struct, spec);
        vo.setLibName(libNames(List.of(struct.getLib())).get(struct.getLib()));
        vo.setAddedName(realnames(List.of(struct.getAddedBy())).getOrDefault(struct.getAddedBy(), struct.getAddedBy()));
        vo.setVersionCount(apiStructSpecMapper.countByName(struct.getName()));
        List<ApiVersionVO> versionList = new ArrayList<>();
        for (ApiStructSpecDO item : apiStructSpecMapper.selectListByName(struct.getName())) {
            ApiVersionVO versionVO = new ApiVersionVO();
            versionVO.setVersion(item.getVersion());
            versionVO.setAddedBy(item.getAddedBy());
            versionVO.setAddedDate(item.getAddedDate());
            versionVO.setCurrent(item.getVersion().equals(struct.getVersion()));
            versionList.add(versionVO);
        }
        vo.setVersionList(versionList);
        return vo;
    }

    @Override
    public Long createStruct(ApiStructSaveReqVO reqVO) {
        validateLibExists(reqVO.getLib());
        String name = trimToEmpty(reqVO.getName());
        String account = currentAccount();
        LocalDateTime now = LocalDateTime.now();

        ApiStructDO struct = new ApiStructDO();
        struct.setLib(reqVO.getLib());
        struct.setName(name);
        struct.setType(defaultIfBlank(reqVO.getType(), DEFAULT_STRUCT_TYPE));
        struct.setDesc(trimToEmpty(reqVO.getDesc()));
        struct.setVersion(1);
        struct.setAttribute(normalizeAttribute(reqVO.getAttribute()));
        struct.setAddedBy(account);
        struct.setAddedDate(now);
        apiStructMapper.insert(struct);

        // 结构版本链第一格（禅道 model.php:190-191）
        ApiStructSpecDO spec = new ApiStructSpecDO();
        spec.setName(name);
        spec.setType(struct.getType());
        spec.setDesc(struct.getDesc());
        spec.setAttribute(struct.getAttribute());
        spec.setVersion(1);
        spec.setAddedBy(account);
        spec.setAddedDate(now);
        apiStructSpecMapper.insert(spec);
        return struct.getId();
    }

    // ==================================================================
    // 发布版本（快照）
    // ==================================================================

    @Override
    public List<ApiReleaseRespVO> getReleaseList(Long libID) {
        List<ApiLibReleaseDO> releases = apiLibReleaseMapper.selectListByLib(
                libID == null || libID <= 0 ? null : libID);
        List<ApiReleaseRespVO> result = new ArrayList<>(releases.size());
        for (ApiLibReleaseDO release : releases) {
            result.add(toReleaseVO(release));
        }
        return result;
    }

    @Override
    public Long publishLib(ApiReleaseSaveReqVO reqVO) {
        validateLibExists(reqVO.getLib());
        String version = trimToEmpty(reqVO.getVersion());
        // 禅道 control.php:441：同一库下版本号不能重复
        if (apiLibReleaseMapper.selectByLibAndVersion(reqVO.getLib(), version) != null) {
            throw exception(API_RELEASE_VERSION_DUPLICATE, version);
        }

        // ① 目录树：禅道 publishLib 的 orderBy('grade desc, `order`')
        List<ModuleDO> modules = new ArrayList<>(moduleService.getModuleList(reqVO.getLib(), MODULE_TYPE_API, null));
        modules.sort(Comparator
                .comparing((ModuleDO module) -> module.getGrade() == null ? 0 : module.getGrade())
                .reversed()
                .thenComparing((ModuleDO module) -> module.getOrder() == null ? 0 : module.getOrder()));

        // ② 接口与结构：**只存 id + version**（内容仍在 spec 表里，读的时候按版本回查）
        List<Map<String, Object>> apiItems = new ArrayList<>();
        for (ApiDO api : apiMapper.selectIdAndVersionByLib(reqVO.getLib())) {
            apiItems.add(snapItem(api.getId(), api.getVersion()));
        }
        List<Map<String, Object>> structItems = new ArrayList<>();
        for (ApiStructDO struct : apiStructMapper.selectListByLib(reqVO.getLib())) {
            structItems.add(snapItem(struct.getId(), struct.getVersion()));
        }

        // ③ 组装 snap：键名与顺序与禅道 model.php:61 完全一致
        Map<String, Object> snap = new LinkedHashMap<>();
        List<Map<String, Object>> moduleItems = new ArrayList<>(modules.size());
        for (ModuleDO module : modules) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", module.getId());
            item.put("root", module.getRoot());
            item.put("branch", module.getBranch());
            item.put("name", module.getName());
            item.put("parent", module.getParent());
            item.put("path", module.getPath());
            item.put("grade", module.getGrade());
            item.put("order", module.getOrder());
            item.put("type", module.getType());
            moduleItems.add(item);
        }
        snap.put("modules", moduleItems);
        snap.put("apis", apiItems);
        snap.put("structs", structItems);

        ApiLibReleaseDO release = new ApiLibReleaseDO();
        release.setLib(reqVO.getLib());
        release.setDesc(trimToEmpty(reqVO.getDesc()));
        release.setVersion(version);
        release.setSnap(JsonUtils.toJsonString(snap));
        release.setAddedBy(currentAccount());
        release.setAddedDate(LocalDateTime.now());
        apiLibReleaseMapper.insert(release);
        return release.getId();
    }

    @Override
    public void deleteRelease(Long id) {
        validateReleaseExists(id);
        // 物理删除（禅道 model.php:82 就是 delete）
        apiLibReleaseMapper.deleteByIdPhysical(id);
    }

    // ==================================================================
    // 校验入口
    // ==================================================================

    @Override
    public ApiDO validateApiExists(Long id) {
        if (id == null) {
            throw exception(API_NOT_EXISTS);
        }
        ApiDO api = apiMapper.selectById(id);
        if (api == null) {
            throw exception(API_NOT_EXISTS);
        }
        return api;
    }

    @Override
    public ApiLibReleaseDO validateReleaseExists(Long id) {
        if (id == null) {
            throw exception(API_RELEASE_NOT_EXISTS);
        }
        ApiLibReleaseDO release = apiLibReleaseMapper.selectById(id);
        if (release == null) {
            throw exception(API_RELEASE_NOT_EXISTS);
        }
        return release;
    }

    private ApiStructDO validateStructExists(Long id) {
        if (id == null) {
            throw exception(API_STRUCT_NOT_EXISTS);
        }
        ApiStructDO struct = apiStructMapper.selectById(id);
        if (struct == null) {
            throw exception(API_STRUCT_NOT_EXISTS);
        }
        return struct;
    }

    // ==================================================================
    // 内部：唯一性 / 版本内容
    // ==================================================================

    /**
     * 两条唯一性（禅道 model.php:99-100 / :146-147）：
     * title 在 (lib, module) 内唯一、path 在 (lib, module, method) 内唯一。
     *
     * <p><b>刻意带上已逻辑删除的行</b>：禅道的 {@code check('title','unique', ...)}
     * 生成的 SQL 里没有 deleted 条件，所以「删掉的接口名/路径仍然占位」。
     */
    private void validateUnique(String title, String path, Long lib, Long module, String method, Long excludeId) {
        Long byTitle = apiMapper.selectIdByTitleIgnoreDeleted(lib, module, title);
        if (byTitle != null && !byTitle.equals(excludeId)) {
            throw exception(API_TITLE_DUPLICATE, title + "」（#" + byTitle + "）");
        }
        Long byPath = apiMapper.selectIdByPathIgnoreDeleted(lib, module, method, path);
        if (byPath != null && !byPath.equals(excludeId)) {
            throw exception(API_PATH_DUPLICATE, method, path, byPath);
        }
    }

    /**
     * 把接口的当前值写进版本表 —— 对应禅道 {@code getApiSpecByData()}（{@code model.php:1027}）。
     *
     * <p>注意它的三条默认值：协议缺省 {@code HTTP}、方法缺省 {@code GET}、状态缺省 {@code done}，
     * 以及**它不写 lib/product/commonParams/editedBy/editedDate**（所以 spec 表没有这些列）。
     */
    private void saveSpec(ApiDO api, int version, LocalDateTime now, String account) {
        ApiSpecDO spec = new ApiSpecDO();
        spec.setDoc(api.getId());
        spec.setModule(api.getModule() == null ? 0L : api.getModule());
        spec.setTitle(trimToEmpty(api.getTitle()));
        spec.setPath(trimToEmpty(api.getPath()));
        spec.setProtocol(defaultIfBlank(api.getProtocol(), "HTTP"));
        spec.setMethod(defaultIfBlank(api.getMethod(), "GET"));
        spec.setRequestType(trimToEmpty(api.getRequestType()));
        spec.setResponseType(trimToEmpty(api.getResponseType()));
        spec.setStatus(defaultIfBlank(api.getStatus(), "done"));
        spec.setOwner(trimToEmpty(api.getOwner()));
        spec.setDesc(trimToEmpty(api.getDesc()));
        spec.setVersion(version);
        spec.setParams(normalizeParams(api.getParams()));
        spec.setParamsExample(trimToEmpty(api.getParamsExample()));
        spec.setResponseExample(trimToEmpty(api.getResponseExample()));
        spec.setResponse(trimToEmpty(api.getResponse()));
        spec.setAddedBy(account);
        spec.setAddedDate(now);
        apiSpecMapper.insert(spec);
    }

    /** 合并「旧值 + 本次更新」，用于给 spec 取当前值 */
    private ApiDO merge(ApiDO old, ApiDO updateObj, int newVersion) {
        ApiDO merged = new ApiDO();
        merged.setId(old.getId());
        merged.setLib(old.getLib());
        merged.setProduct(old.getProduct());
        merged.setResponseType(old.getResponseType());
        merged.setCommonParams(old.getCommonParams());
        merged.setModule(updateObj.getModule() == null ? old.getModule() : updateObj.getModule());
        merged.setTitle(updateObj.getTitle() == null ? old.getTitle() : updateObj.getTitle());
        merged.setPath(updateObj.getPath() == null ? old.getPath() : updateObj.getPath());
        merged.setProtocol(updateObj.getProtocol() == null ? old.getProtocol() : updateObj.getProtocol());
        merged.setMethod(updateObj.getMethod() == null ? old.getMethod() : updateObj.getMethod());
        merged.setRequestType(updateObj.getRequestType() == null ? old.getRequestType() : updateObj.getRequestType());
        merged.setStatus(updateObj.getStatus() == null ? old.getStatus() : updateObj.getStatus());
        merged.setOwner(updateObj.getOwner() == null ? old.getOwner() : updateObj.getOwner());
        merged.setDesc(updateObj.getDesc() == null ? old.getDesc() : updateObj.getDesc());
        merged.setParams(updateObj.getParams() == null ? old.getParams() : updateObj.getParams());
        merged.setParamsExample(updateObj.getParamsExample() == null
                ? old.getParamsExample() : updateObj.getParamsExample());
        merged.setResponse(updateObj.getResponse() == null ? old.getResponse() : updateObj.getResponse());
        merged.setResponseExample(updateObj.getResponseExample() == null
                ? old.getResponseExample() : updateObj.getResponseExample());
        merged.setVersion(newVersion);
        return merged;
    }

    /** 把 spec 的内容字段贴到 VO 上（库/产品/修改人仍取主表） */
    private void applySpecContent(ApiRespVO vo, ApiSpecDO spec) {
        vo.setModule(spec.getModule());
        vo.setTitle(spec.getTitle());
        vo.setPath(spec.getPath());
        vo.setProtocol(spec.getProtocol());
        vo.setMethod(spec.getMethod());
        vo.setRequestType(spec.getRequestType());
        vo.setResponseType(spec.getResponseType());
        vo.setStatus(spec.getStatus());
        vo.setOwner(spec.getOwner());
        vo.setDesc(spec.getDesc());
        vo.setVersion(spec.getVersion());
        vo.setParams(spec.getParams());
        vo.setParamsExample(spec.getParamsExample());
        vo.setResponseExample(spec.getResponseExample());
        vo.setResponse(spec.getResponse());
        vo.setAddedBy(spec.getAddedBy());
        vo.setAddedDate(spec.getAddedDate());
        vo.setStatusName(ApiStatusEnum.textOf(spec.getStatus()));
    }

    // ==================================================================
    // 内部：按发布版本浏览
    // ==================================================================

    /**
     * 按发布版本浏览接口（禅道 {@code getListByModuleID} 的 release 分支）。
     *
     * <p>快照给的是有界集合（一次发布冻结的接口），所以这里在内存里过滤 + 分页：
     * 用「snap 的 (id, version) + 主表的库信息 + spec 的冻结内容」拼出那一版的视图。
     * 过滤条件作用在 **spec（冻结内容）** 上 —— 看历史版本就该按历史版本筛。
     */
    private PageResult<ApiRespVO> getApiPageByRelease(ApiPageReqVO reqVO) {
        ApiLibReleaseDO release = validateReleaseExists(reqVO.getReleaseID());
        if (reqVO.getLib() != null && !reqVO.getLib().equals(release.getLib())) {
            // 一个发布只属于一个库
            return PageResult.empty();
        }
        Collection<Long> moduleIds = expandModule(reqVO.getModule());
        if (moduleIds != null && moduleIds.isEmpty()) {
            return PageResult.empty();
        }
        List<ApiReleaseRespVO.SnapItemVO> items = parseSnap(release.getSnap()).apis;
        if (items.isEmpty()) {
            return PageResult.empty();
        }
        List<Long> ids = new ArrayList<>(items.size());
        for (ApiReleaseRespVO.SnapItemVO item : items) {
            ids.add(item.getId());
        }
        Map<Long, ApiDO> apiRows = new HashMap<>();
        for (ApiDO api : apiMapper.selectListByIdsIgnoreDeleted(ids)) {
            apiRows.put(api.getId(), api);
        }
        Map<String, ApiSpecDO> specs = new HashMap<>();
        for (ApiSpecDO spec : apiSpecMapper.selectListByDocs(ids)) {
            specs.put(specKey(spec.getDoc(), spec.getVersion()), spec);
        }

        Map<Long, String> libNameMap = libNames(List.of(release.getLib()));
        Map<Long, String> moduleNameMap = moduleNames(release.getLib());
        List<ApiRespVO> all = new ArrayList<>();
        for (ApiReleaseRespVO.SnapItemVO item : items) {
            ApiDO api = apiRows.get(item.getId());
            ApiSpecDO spec = specs.get(specKey(item.getId(), item.getVersion()));
            if (api == null || spec == null) {
                // 快照指向的行没了（脏数据）：跳过这一条，不要整页打不开（README 第 34 条坑）
                log.warn("接口发布快照指向的行不存在：release={} api={} version={}",
                        release.getId(), item.getId(), item.getVersion());
                continue;
            }
            if (!matches(reqVO, spec, moduleIds)) {
                continue;
            }
            ApiRespVO vo = toVO(api, libNameMap.get(api.getLib()), moduleNameMap);
            applySpecContent(vo, spec);
            vo.setModuleName(moduleNameMap.getOrDefault(spec.getModule() == null ? 0L : spec.getModule(), ""));
            all.add(vo);
        }
        return pageOf(all, reqVO);
    }

    /** 快照浏览时的过滤：按冻结内容筛 */
    private boolean matches(ApiPageReqVO reqVO, ApiSpecDO spec, Collection<Long> moduleIds) {
        if (moduleIds != null && !moduleIds.contains(spec.getModule())) {
            return false;
        }
        if (StringUtils.hasText(reqVO.getTitle()) && !containsIgnoreCase(spec.getTitle(), reqVO.getTitle())) {
            return false;
        }
        if (StringUtils.hasText(reqVO.getPath()) && !containsIgnoreCase(spec.getPath(), reqVO.getPath())) {
            return false;
        }
        if (StringUtils.hasText(reqVO.getMethod()) && !reqVO.getMethod().equals(spec.getMethod())) {
            return false;
        }
        if (StringUtils.hasText(reqVO.getStatus()) && !reqVO.getStatus().equals(spec.getStatus())) {
            return false;
        }
        return !StringUtils.hasText(reqVO.getOwner()) || reqVO.getOwner().equals(spec.getOwner());
    }

    /** 按发布版本浏览结构（禅道 {@code getStructListByRelease}，按 name+version 取冻结内容） */
    private PageResult<ApiStructRespVO> getStructPageByRelease(ApiStructPageReqVO reqVO) {
        ApiLibReleaseDO release = validateReleaseExists(reqVO.getReleaseID());
        if (reqVO.getLib() != null && !reqVO.getLib().equals(release.getLib())) {
            return PageResult.empty();
        }
        List<ApiReleaseRespVO.SnapItemVO> items = parseSnap(release.getSnap()).structs;
        if (items.isEmpty()) {
            return PageResult.empty();
        }
        List<Long> ids = new ArrayList<>(items.size());
        for (ApiReleaseRespVO.SnapItemVO item : items) {
            ids.add(item.getId());
        }
        Map<Long, ApiStructDO> rows = new HashMap<>();
        for (ApiStructDO struct : apiStructMapper.selectListByIdsIgnoreDeleted(ids)) {
            rows.put(struct.getId(), struct);
        }
        Map<Long, String> libNameMap = libNames(List.of(release.getLib()));
        Map<String, String> realnames = realnames(accountsOfStructs(rows.values()));
        List<ApiStructRespVO> all = new ArrayList<>();
        for (ApiReleaseRespVO.SnapItemVO item : items) {
            ApiStructDO struct = rows.get(item.getId());
            if (struct == null) {
                continue;
            }
            if (StringUtils.hasText(reqVO.getName()) && !containsIgnoreCase(struct.getName(), reqVO.getName())) {
                continue;
            }
            ApiStructSpecDO spec = apiStructSpecMapper.selectByNameAndVersion(struct.getName(), item.getVersion());
            if (spec == null) {
                log.warn("结构发布快照指向的版本不存在：release={} name={} version={}",
                        release.getId(), struct.getName(), item.getVersion());
                continue;
            }
            ApiStructRespVO vo = toStructVO(struct, spec);
            vo.setLibName(libNameMap.get(struct.getLib()));
            vo.setAddedName(realnames.getOrDefault(spec.getAddedBy(), spec.getAddedBy()));
            vo.setVersionCount(apiStructSpecMapper.countByName(struct.getName()));
            all.add(vo);
        }
        return pageOf(all, reqVO);
    }

    // ==================================================================
    // 内部：删除保护
    // ==================================================================

    /**
     * 这个接口被哪个发布版本冻结了（{@code snap.apis} 里有这个编号）。
     *
     * <p>禅道**没有**这道检查（{@code control.php:784 delete()} 拿到就删），是本实现加的加固：
     * 发布快照虽然只存版本号（所以删接口不会让历史版本读不出来），但「已经被对外发布过」
     * 这件事应该让人先确认一次 —— 想删就先删发布版本。
     */
    private ApiLibReleaseDO findFrozenRelease(Long lib, Long apiId) {
        for (ApiLibReleaseDO release : apiLibReleaseMapper.selectListByLib(lib)) {
            for (ApiReleaseRespVO.SnapItemVO item : parseSnap(release.getSnap()).apis) {
                if (Objects.equals(item.getId(), apiId)) {
                    return release;
                }
            }
        }
        return null;
    }

    /**
     * 哪个数据结构引用了这个接口。
     *
     * <p>判定方式：扫 {@code zt_apistruct.attribute} 的字段树，看有没有节点的
     * {@code paramsType}/{@code refTarget}/{@code apiRef}/{@code ref} 等于这个接口编号。
     *
     * <h3>诚实说明：禅道的数据模型里不存在「结构引用接口」这条关系</h3>
     * 结构的字段类型下拉里只有「内置类型 + 同库的结构编号」（{@code control.php:1031 getTypeOptions}），
     * 数字型的 {@code paramsType} 语义上一定是**结构编号**。所以正常用禅道写出来的数据里，
     * 这个检查永远不会命中 —— 它是一道**防御性**保护：只要有人（脚本/导入/脏数据）在字段树里
     * 写了接口编号，删除就会被拦下来告诉你哪条结构引用了它。
     * 反过来那条**真实会命中**的引用（结构被接口/结构引用）在禅道里同样没有检查，
     * 本模块也没有实现结构删除接口，所以没有落点 —— 这一点在交付说明里如实登记。
     */
    private ApiStructDO findStructReferencing(Long lib, Long apiId) {
        List<ApiStructDO> sameLib = apiStructMapper.selectListByLib(lib);
        ApiStructDO found = findStructReferencing(sameLib, apiId);
        if (found != null) {
            return found;
        }
        Set<Long> scanned = new LinkedHashSet<>();
        for (ApiStructDO struct : sameLib) {
            scanned.add(struct.getId());
        }
        List<ApiStructDO> others = new ArrayList<>();
        for (ApiStructDO struct : apiStructMapper.selectAllList()) {
            if (!scanned.contains(struct.getId())) {
                others.add(struct);
            }
        }
        return findStructReferencing(others, apiId);
    }

    private ApiStructDO findStructReferencing(List<ApiStructDO> structs, Long apiId) {
        for (ApiStructDO struct : structs) {
            JsonNode tree = parseTreeQuietly(struct.getAttribute());
            if (tree != null && treeReferencesId(tree, apiId)) {
                return struct;
            }
        }
        return null;
    }

    /** 递归扫字段树：命中 REF_KEYS 里任一键等于目标编号就返回 true */
    private boolean treeReferencesId(JsonNode node, Long targetId) {
        String target = String.valueOf(targetId);
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (treeReferencesId(child, targetId)) {
                    return true;
                }
            }
            return false;
        }
        if (!node.isObject()) {
            return false;
        }
        for (String key : REF_KEYS) {
            JsonNode value = node.get(key);
            // 用 asString()（Jackson 3 里 asText() 已过时）：数字型的 paramsType 也能拿到文本
            if (value != null && !value.isNull() && target.equals(value.asString())) {
                return true;
            }
        }
        JsonNode children = node.get("children");
        return children != null && treeReferencesId(children, targetId);
    }

    // ==================================================================
    // 内部：转换与工具
    // ==================================================================

    private ApiRespVO toVO(ApiDO api, String libName, Map<Long, String> moduleNameMap) {
        ApiRespVO vo = BeanUtils.toBean(api, ApiRespVO.class);
        vo.setLibName(libName);
        vo.setModuleName(moduleNameMap == null ? ""
                : moduleNameMap.getOrDefault(api.getModule() == null ? 0L : api.getModule(), ""));
        vo.setStatusName(ApiStatusEnum.textOf(api.getStatus()));
        return vo;
    }

    /**
     * 结构与版本合成一个 VO。
     *
     * @param spec 非空时内容取这一版的 spec（字段树/类型/说明/版本号/写者）
     */
    private ApiStructRespVO toStructVO(ApiStructDO struct, ApiStructSpecDO spec) {
        ApiStructRespVO vo = BeanUtils.toBean(struct, ApiStructRespVO.class);
        if (spec != null) {
            vo.setName(spec.getName());
            vo.setType(spec.getType());
            vo.setDesc(spec.getDesc());
            vo.setAttribute(spec.getAttribute());
            vo.setVersion(spec.getVersion());
            vo.setAddedBy(spec.getAddedBy());
            vo.setAddedDate(spec.getAddedDate());
        }
        return vo;
    }

    private ApiReleaseRespVO toReleaseVO(ApiLibReleaseDO release) {
        ApiReleaseRespVO vo = BeanUtils.toBean(release, ApiReleaseRespVO.class);
        SnapData snap = parseSnap(release.getSnap());
        vo.setModuleCount(snap.moduleCount);
        vo.setApiCount(snap.apis.size());
        vo.setStructCount(snap.structs.size());
        vo.setSnapApis(snap.apis);
        vo.setSnapStructs(snap.structs);
        return vo;
    }

    /** 快照 JSON 的解析结果（modules 只需要个数，apis/structs 需要 id+version） */
    private static final class SnapData {
        private int moduleCount;
        private List<ApiReleaseRespVO.SnapItemVO> apis = new ArrayList<>();
        private List<ApiReleaseRespVO.SnapItemVO> structs = new ArrayList<>();
    }

    /**
     * 解析 snap。**脏 JSON 不抛异常**（返回空快照），
     * 否则一个坏快照会让整个发布列表 500（README 第 34 条坑的思路）。
     */
    private SnapData parseSnap(String snapJson) {
        SnapData data = new SnapData();
        JsonNode root = parseTreeQuietly(snapJson);
        if (root == null || !root.isObject()) {
            return data;
        }
        JsonNode modules = root.get("modules");
        data.moduleCount = modules != null && modules.isArray() ? modules.size() : 0;
        data.apis = readSnapItems(root.get("apis"));
        data.structs = readSnapItems(root.get("structs"));
        return data;
    }

    private List<ApiReleaseRespVO.SnapItemVO> readSnapItems(JsonNode array) {
        List<ApiReleaseRespVO.SnapItemVO> items = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return items;
        }
        for (JsonNode node : array) {
            JsonNode id = node.get("id");
            if (id == null || id.isNull()) {
                continue;
            }
            ApiReleaseRespVO.SnapItemVO item = new ApiReleaseRespVO.SnapItemVO();
            item.setId(id.asLong());
            JsonNode version = node.get("version");
            item.setVersion(version == null || version.isNull() ? 1 : version.asInt());
            items.add(item);
        }
        return items;
    }

    private JsonNode parseTreeQuietly(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JsonUtils.parseTree(json);
        } catch (RuntimeException e) {
            log.warn("JSON 解析失败，按空处理：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 请求参数树的归一化：空 → 默认四键结构；能解析成对象 → 补齐缺失的键；解析不了 → **原样保留**。
     *
     * <p>禅道的表单永远提交 {@code {header,params,paramsType,query}} 四个键，这里补齐是为了让
     * 前端不必到处判空；但坏 JSON 不做拦截（宁可存下来让人看见，也不要 500）。
     */
    @SuppressWarnings("unchecked")
    private String normalizeParams(String raw) {
        if (!StringUtils.hasText(raw)) {
            return DEFAULT_PARAMS;
        }
        Object parsed = JsonUtils.parseObjectQuietly(raw, Map.class);
        if (!(parsed instanceof Map)) {
            return raw;
        }
        Map<String, Object> map = (Map<String, Object>) parsed;
        // params JSON 只有 header/query/params 三个容器（另外三个 scope 在禅道里没有独立容器，
        // 见 ApiParamsScopeEnum 的 jsonKey），这里按枚举补齐，把「scope ↔ JSON 键」固化在一处
        for (ApiParamsScopeEnum scope : ApiParamsScopeEnum.values()) {
            if (scope.getJsonKey() != null) {
                map.putIfAbsent(scope.getJsonKey(), new ArrayList<>());
            }
        }
        if (!StringUtils.hasText(String.valueOf(map.get("paramsType")))
                || "null".equals(String.valueOf(map.get("paramsType")))) {
            map.put("paramsType", DEFAULT_PARAMS_TYPE);
        }
        return JsonUtils.toJsonString(map);
    }

    /** 字段树的归一化：空 → 空数组（结构允许没有字段） */
    private String normalizeAttribute(String raw) {
        return StringUtils.hasText(raw) ? raw : "[]";
    }

    private Collection<Long> expandModule(Long moduleId) {
        if (moduleId == null) {
            return null;
        }
        return moduleService.getSelfAndDescendantIds(moduleId);
    }

    private Map<Long, String> libNames(Collection<Long> libIds) {
        Map<Long, String> map = new HashMap<>();
        if (libIds == null || libIds.isEmpty()) {
            return map;
        }
        for (DocLibDO lib : docLibMapper.selectBatchIds(libIds)) {
            map.put(lib.getId(), lib.getName());
        }
        return map;
    }

    private Map<Long, String> moduleNames(Long lib) {
        Map<Long, String> map = new HashMap<>();
        if (lib == null) {
            return map;
        }
        for (ModuleDO module : moduleService.getModuleList(lib, MODULE_TYPE_API, null)) {
            map.put(module.getId(), module.getName());
        }
        return map;
    }

    /** 账号 → 姓名（禅道 struct 列表 join zt_user 取 realname），复用 system 的 AdminUserApi */
    private Map<String, String> realnames(Collection<String> accounts) {
        Map<String, String> map = new HashMap<>();
        List<String> distinct = new ArrayList<>(new LinkedHashSet<>(
                accounts.stream().filter(StringUtils::hasText).toList()));
        if (distinct.isEmpty()) {
            return map;
        }
        for (AdminUserRespDTO user : adminUserApi.getUserListByUsernames(distinct)) {
            map.put(user.getUsername(), user.getNickname());
        }
        return map;
    }

    private Map<Long, Integer> versionCounts(List<Long> apiIds) {
        Map<Long, Integer> map = new HashMap<>();
        if (apiIds == null || apiIds.isEmpty()) {
            return map;
        }
        for (Map<String, Object> row : apiSpecMapper.countByDocs(apiIds)) {
            map.put(((Number) row.get("doc")).longValue(), ((Number) row.get("cnt")).intValue());
        }
        return map;
    }

    /** 内存分页（快照浏览用：一次发布冻结的条目是有界的） */
    private static <T> PageResult<T> pageOf(List<T> all, PageParam req) {
        int pageNo = req.getPageNo() == null || req.getPageNo() < 1 ? 1 : req.getPageNo();
        int pageSize = req.getPageSize() == null || req.getPageSize() < 1 ? 10 : req.getPageSize();
        int from = (pageNo - 1) * pageSize;
        if (from >= all.size()) {
            return new PageResult<>(new ArrayList<>(), (long) all.size());
        }
        int to = Math.min(all.size(), from + pageSize);
        return new PageResult<>(new ArrayList<>(all.subList(from, to)), (long) all.size());
    }

    private static List<Long> ids(List<ApiDO> list) {
        List<Long> ids = new ArrayList<>(list.size());
        for (ApiDO item : list) {
            ids.add(item.getId());
        }
        return ids;
    }

    private static Set<Long> collectLibIds(List<ApiDO> list) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ApiDO item : list) {
            if (item.getLib() != null) {
                ids.add(item.getLib());
            }
        }
        return ids;
    }

    private static Set<Long> collectStructLibIds(List<ApiStructDO> list) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ApiStructDO item : list) {
            if (item.getLib() != null) {
                ids.add(item.getLib());
            }
        }
        return ids;
    }

    private static List<String> accounts(List<ApiStructDO> list) {
        List<String> accounts = new ArrayList<>(list.size());
        for (ApiStructDO item : list) {
            accounts.add(item.getAddedBy());
        }
        return accounts;
    }

    private static List<String> accountsOfStructs(Collection<ApiStructDO> list) {
        List<String> accounts = new ArrayList<>(list.size());
        for (ApiStructDO item : list) {
            accounts.add(item.getAddedBy());
        }
        return accounts;
    }

    private static Map<String, Object> snapItem(Long id, Integer version) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("version", version == null ? 1 : version);
        return item;
    }

    private static String specKey(Long doc, Integer version) {
        return doc + "#" + version;
    }

    private static boolean containsIgnoreCase(String source, String keyword) {
        return trimToEmpty(source).toLowerCase().contains(trimToEmpty(keyword).toLowerCase());
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    /**
     * 取当前登录用户的账号。与 StoryServiceImpl / DocServiceImpl 保持同样的口径
     * （禅道的 {@code addedBy} 存的是账号，yudao 的登录上下文只有 userId）。
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
