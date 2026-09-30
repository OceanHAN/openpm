package cn.iocoder.yudao.module.zentao.service.doc;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocContentRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocLibRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocLibSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocMoveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.doc.DocContentDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.doc.DocDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.doc.DocLibDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.doc.DocContentMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.doc.DocLibMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.doc.DocMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.doc.DocLibTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.doc.DocStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.doc.DocTypeEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 文档 Service 实现
 *
 * <p>对齐禅道 {@code module/doc/model.php} 的 createLib / create / update / delete /
 * saveDocContent / getByID / getContent / view / moveDoc。
 *
 * <p>本模块有两个「跟别处不一样」的地方，代码里的注释都围绕它们展开：
 * <ol>
 *   <li><b>一张表两种对象</b>：{@code zt_doc.type='chapter'} 是章节，其余是文档。
 *       章节不写版本内容，但和文档共用 parent/path/grade 树。</li>
 *   <li><b>版本链 + 草稿位</b>：{@code zt_doccontent} 的 {@code (doc, version)} 唯一，
 *       {@code version=0} 是草稿。同一次保存到底该 UPDATE 还是 INSERT，
 *       取决于「目标版本号是否已存在」——这是本模块最容易写错的一处。</li>
 * </ol>
 */
@Slf4j
@Service
public class DocServiceImpl implements DocService {

    /**
     * 禅道 {@code $config->doc->urlValidator}：允许省略协议，域名或 IPv4 + 可选端口 + 可选路径。
     */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://)?((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}"
                    + "|(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})"
                    + "(:\\d{1,5})?([/?#]\\S*)?$",
            Pattern.CASE_INSENSITIVE);

    @Resource
    private DocLibMapper docLibMapper;

    @Resource
    private DocMapper docMapper;

    @Resource
    private DocContentMapper docContentMapper;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ================================================================
    // 文档库
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLib(DocLibSaveReqVO reqVO) {
        String type = reqVO.getType();
        if (DocLibTypeEnum.of(type) == null) {
            throw exception(DOC_LIB_TYPE_INVALID, type);
        }
        Long objectID = objectIDOfLib(type, reqVO.getProduct(), reqVO.getProject(), reqVO.getExecution());
        if (DocLibTypeEnum.isObjectLib(type) && objectID == null) {
            throw exception(DOC_LIB_OBJECT_REQUIRED, DocLibTypeEnum.of(type).getName());
        }
        // 自定义库必须挂在团队空间下（禅道：parent<=0 且 type=custom 直接报「空间不能为空」）
        if (DocLibTypeEnum.CUSTOM.getType().equals(type) && (reqVO.getParent() == null || reqVO.getParent() == 0)) {
            throw exception(DOC_LIB_PARENT_REQUIRED);
        }
        validateLibNameUnique(type, objectID, reqVO.getParent(), reqVO.getName(), null);

        DocLibDO lib = new DocLibDO();
        lib.setType(type);
        lib.setParent(reqVO.getParent() == null ? 0L : reqVO.getParent());
        lib.setProduct(reqVO.getProduct() == null ? 0L : reqVO.getProduct());
        lib.setProject(reqVO.getProject() == null ? 0L : reqVO.getProject());
        lib.setExecution(reqVO.getExecution() == null ? 0L : reqVO.getExecution());
        lib.setName(reqVO.getName());
        lib.setAcl(StringUtils.hasText(reqVO.getAcl()) ? reqVO.getAcl() : "open");
        lib.setGroups(reqVO.getGroups() == null ? "" : reqVO.getGroups());
        lib.setUsers(reqVO.getUsers() == null ? "" : reqVO.getUsers());
        lib.setDesc(reqVO.getDesc() == null ? "" : reqVO.getDesc());
        lib.setOrder(reqVO.getOrder() == null ? 0 : reqVO.getOrder());
        // 跟随对象的库是「主库」，不允许删除；自定义库不是
        lib.setMain(DocLibTypeEnum.isObjectLib(type) ? 1 : 0);
        lib.setArchived(0);
        lib.setOrderBy("id_asc");
        lib.setBaseUrl("");
        lib.setAddedBy(currentAccount());
        lib.setAddedDate(LocalDateTime.now());
        docLibMapper.insert(lib);

        // 禅道把 order 初始化成 id，保证新库排在最后
        DocLibDO orderUpdate = new DocLibDO();
        orderUpdate.setId(lib.getId());
        orderUpdate.setOrder(reqVO.getOrder() == null ? lib.getId().intValue() : reqVO.getOrder());
        docLibMapper.updateById(orderUpdate);

        actionService.recordAction("doclib", lib.getId(), ActionTypeEnum.CREATED, "新建文档库：" + lib.getName());
        return lib.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLib(DocLibSaveReqVO reqVO) {
        DocLibDO old = validateLibExists(reqVO.getId());
        String type = StringUtils.hasText(reqVO.getType()) ? reqVO.getType() : old.getType();
        if (DocLibTypeEnum.of(type) == null) {
            throw exception(DOC_LIB_TYPE_INVALID, type);
        }
        Long objectID = objectIDOfLib(type, reqVO.getProduct(), reqVO.getProject(), reqVO.getExecution());
        if (DocLibTypeEnum.isObjectLib(type) && objectID == null) {
            throw exception(DOC_LIB_OBJECT_REQUIRED, DocLibTypeEnum.of(type).getName());
        }
        validateLibNameUnique(type, objectID, reqVO.getParent(), reqVO.getName(), reqVO.getId());

        DocLibDO updateObj = new DocLibDO();
        updateObj.setId(reqVO.getId());
        updateObj.setType(type);
        updateObj.setParent(reqVO.getParent() == null ? old.getParent() : reqVO.getParent());
        updateObj.setProduct(reqVO.getProduct());
        updateObj.setProject(reqVO.getProject());
        updateObj.setExecution(reqVO.getExecution());
        updateObj.setName(reqVO.getName());
        updateObj.setAcl(reqVO.getAcl());
        updateObj.setGroups(reqVO.getGroups());
        updateObj.setUsers(reqVO.getUsers());
        updateObj.setDesc(reqVO.getDesc());
        updateObj.setOrder(reqVO.getOrder());
        docLibMapper.updateById(updateObj);

        actionService.recordActionWithChanges("doclib", reqVO.getId(), ActionTypeEnum.EDITED,
                "修改文档库：" + reqVO.getName(), old, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLib(Long id) {
        DocLibDO lib = validateLibExists(id);
        // 主库跟随产品/项目/执行，删了它对象页面上就没有文档入口了 —— 禅道也是直接 return
        if (lib.getMain() != null && lib.getMain() == 1) {
            throw exception(DOC_LIB_IS_MAIN);
        }
        // 【有意偏离禅道】禅道删库不检查库内文档，文档会变成孤儿（还挂在已删的库上）。
        // 这里显式拒绝，和「产品下还有需求不能删除」保持同一套保护策略。
        long docCount = docMapper.selectListByLib(id).size();
        if (docCount > 0) {
            throw exception(DOC_LIB_HAS_DOCS, docCount);
        }
        docLibMapper.deleteById(id);
        actionService.recordAction("doclib", id, ActionTypeEnum.DELETED, "删除文档库：" + lib.getName());
    }

    @Override
    public DocLibDO getLib(Long id) {
        return validateLibExists(id);
    }

    @Override
    public DocLibDO validateLibExists(Long id) {
        DocLibDO lib = id == null ? null : docLibMapper.selectById(id);
        if (lib == null) {
            throw exception(DOC_LIB_NOT_EXISTS, id);
        }
        return lib;
    }

    @Override
    public List<DocLibDO> getLibList(String type, Long objectID) {
        if (!StringUtils.hasText(type)) {
            return docLibMapper.selectList();
        }
        return docLibMapper.selectListByObject(type, objectID);
    }

    @Override
    public List<DocLibDO> getLibListByParent(Long parent) {
        if (parent == null || parent == 0) {
            // parent=0：把所有 custom 空间返回（团队空间列表）
            return docLibMapper.selectList(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<DocLibDO>()
                    .eq(DocLibDO::getType, DocLibTypeEnum.CUSTOM.getType())
                    .eq(DocLibDO::getParent, 0L)
                    .orderByAsc(DocLibDO::getOrder));
        }
        return docLibMapper.selectListByParent(parent);
    }

    @Override
    public List<DocLibRespVO> getLibRespList(String type, Long objectID, Long parent) {
        List<DocLibDO> libs = StringUtils.hasText(type)
                ? docLibMapper.selectListByObject(type, objectID)
                : docLibMapper.selectListByParent(parent);
        List<DocLibRespVO> result = new ArrayList<>(libs.size());
        for (DocLibDO lib : libs) {
            DocLibRespVO vo = BeanUtils.toBean(lib, DocLibRespVO.class);
            vo.setMain(lib.getMain() != null && lib.getMain() == 1);
            DocLibTypeEnum typeEnum = DocLibTypeEnum.of(lib.getType());
            vo.setTypeName(typeEnum == null ? lib.getType() : typeEnum.getName());
            // 库内文档数：章节不算文档
            vo.setDocCount((long) countDocsInLib(lib.getId()));
            result.add(vo);
        }
        return result;
    }

    // ================================================================
    // 文档：新建
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDoc(DocSaveReqVO reqVO) {
        DocLibDO lib = validateLibExists(reqVO.getLib());
        DocTypeEnum typeEnum = DocTypeEnum.of(reqVO.getType());
        if (typeEnum == null) {
            throw exception(DOC_TYPE_INVALID, reqVO.getType());
        }
        boolean isChapter = DocTypeEnum.isChapter(reqVO.getType());
        boolean isDraft = DocStatusEnum.DRAFT.getStatus().equals(reqVO.getStatus());

        // 章节与文档在同一库里不能同名（同一父节点下）
        validateTitleUnique(reqVO.getLib(), reqVO.getParent(), reqVO.getTitle(), null);

        // 上级必须是同一库里的章节
        Long parent = reqVO.getParent() == null ? 0L : reqVO.getParent();
        if (parent > 0) {
            DocDO parentDoc = validateDocExists(parent);
            if (!DocTypeEnum.isChapter(parentDoc.getType())) {
                throw exception(DOC_PARENT_NOT_CHAPTER, parent);
            }
            if (!Objects.equals(parentDoc.getLib(), reqVO.getLib())) {
                throw exception(DOC_PARENT_NOT_SAME_LIB);
            }
        }

        // 正文校验：chapter / attachment 不需要正文，url 需要合法的链接
        String content = reqVO.getContent();
        if (!isChapter && DocTypeEnum.needsContent(reqVO.getType())) {
            if (!StringUtils.hasText(content)) {
                throw exception(DOC_CONTENT_REQUIRED, typeEnum.getName());
            }
            if ("url".equals(reqVO.getType())) {
                validateUrl(content);
            }
        }
        if ("attachment".equals(reqVO.getType()) && !StringUtils.hasText(reqVO.getFiles())) {
            throw exception(DOC_ATTACHMENT_REQUIRED);
        }

        String account = currentAccount();
        LocalDateTime now = LocalDateTime.now();

        DocDO doc = new DocDO();
        doc.setLib(reqVO.getLib());
        // 文档归属从库继承（禅道 create 里 $doc->product = $lib->product）
        doc.setProduct(lib.getProduct() == null ? 0L : lib.getProduct());
        doc.setProject(lib.getProject() == null ? 0L : lib.getProject());
        doc.setExecution(lib.getExecution() == null ? 0L : lib.getExecution());
        doc.setModule(reqVO.getModule() == null ? 0L : reqVO.getModule());
        doc.setTitle(reqVO.getTitle());
        doc.setKeywords(reqVO.getKeywords() == null ? "" : reqVO.getKeywords());
        doc.setType(reqVO.getType());
        doc.setStatus(isDraft ? DocStatusEnum.DRAFT.getStatus() : DocStatusEnum.NORMAL.getStatus());
        doc.setParent(parent);
        doc.setGrade(1);
        doc.setPath(",");
        doc.setOrder(0);
        doc.setViews(0);
        doc.setCollects(0);
        doc.setVersion(isDraft ? 0 : 1);
        doc.setFrom(0L);
        doc.setFromVersion(1);
        doc.setAddedBy(account);
        doc.setAddedDate(now);
        doc.setEditedBy(account);
        doc.setEditedDate(now);
        doc.setAssignedTo("");
        doc.setAcl(StringUtils.hasText(reqVO.getAcl()) ? reqVO.getAcl() : "open");
        doc.setGroups(reqVO.getGroups() == null ? "" : reqVO.getGroups());
        doc.setUsers(reqVO.getUsers() == null ? "" : reqVO.getUsers());
        // 草稿的正文存在 zt_doc.draft 里（禅道 $doc->draft = $isDraft ? $docContent->content : ''）
        doc.setDraft(isDraft ? content : "");
        doc.setVision("rnd");
        docMapper.insert(doc);

        // path 里包含自己：先插出 id，再按 parent 拼 path/grade/order（禅道也是先 insert 再 update）
        String path = buildPath(parent, doc.getId());
        Integer grade = buildGrade(parent);
        docMapper.updateStructure(doc.getId(), doc.getLib(), parent, path, grade, doc.getModule());
        doc.setPath(path);
        doc.setGrade(grade);
        doc.setOrder(doc.getId().intValue());

        // 章节没有正文，不写版本表
        if (!isChapter) {
            DocContentDO contentDO = new DocContentDO();
            contentDO.setDoc(doc.getId());
            contentDO.setTitle(reqVO.getTitle());
            contentDO.setDigest("");
            contentDO.setContent(content);
            contentDO.setRawContent(reqVO.getRawContent());
            contentDO.setFiles(reqVO.getFiles() == null ? "" : reqVO.getFiles());
            contentDO.setType(contentTypeOf(reqVO.getType()));
            contentDO.setAddedBy(account);
            contentDO.setAddedDate(now);
            contentDO.setEditedBy(account);
            contentDO.setEditedDate(now);
            contentDO.setVersion(isDraft ? 0 : 1);
            contentDO.setFromVersion(0);
            docContentMapper.insert(contentDO);
        }

        actionService.recordAction("doc", doc.getId(), ActionTypeEnum.CREATED,
                (isChapter ? "新建章节：" : "新建文档：") + doc.getTitle());
        return doc.getId();
    }

    // ================================================================
    // 文档：编辑
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDoc(DocSaveReqVO reqVO) {
        DocDO old = validateDocExists(reqVO.getId());
        DocTypeEnum typeEnum = DocTypeEnum.of(reqVO.getType());
        if (typeEnum == null) {
            throw exception(DOC_TYPE_INVALID, reqVO.getType());
        }
        boolean isChapter = DocTypeEnum.isChapter(reqVO.getType());

        Long parent = reqVO.getParent() == null ? 0L : reqVO.getParent();
        if (parent > 0) {
            if (Objects.equals(parent, old.getId())) {
                throw exception(DOC_PARENT_IS_SELF_DESCENDANT);
            }
            DocDO parentDoc = validateDocExists(parent);
            if (!DocTypeEnum.isChapter(parentDoc.getType())) {
                throw exception(DOC_PARENT_NOT_CHAPTER, parent);
            }
            if (!Objects.equals(parentDoc.getLib(), reqVO.getLib())) {
                throw exception(DOC_PARENT_NOT_SAME_LIB);
            }
            // 不能把节点挂到自己的子孙下（否则那棵子树的 path 会自引用成环）
            if (StringUtils.hasText(old.getPath()) && StringUtils.hasText(parentDoc.getPath())
                    && parentDoc.getPath().startsWith(old.getPath())) {
                throw exception(DOC_PARENT_IS_SELF_DESCENDANT);
            }
        }

        // 正文校验（和新建同一套规则）
        String content = reqVO.getContent();
        if (!isChapter && DocTypeEnum.needsContent(reqVO.getType()) && StringUtils.hasText(content)
                && "url".equals(reqVO.getType())) {
            validateUrl(content);
        }
        if ("attachment".equals(reqVO.getType()) && !StringUtils.hasText(reqVO.getFiles())) {
            throw exception(DOC_ATTACHMENT_REQUIRED);
        }
        if (!isChapter && StringUtils.hasText(reqVO.getStatus())
                && DocStatusEnum.of(reqVO.getStatus()) == null) {
            throw exception(DOC_STATUS_INVALID, reqVO.getStatus());
        }

        // 只有标题 / 正文 / 原始内容变了才产生新版本；
        // 只改「基础信息」（库、章节、权限）时版本号必须保持不变 —— 禅道 update() 的核心判断
        boolean isDraft = DocStatusEnum.DRAFT.getStatus().equals(reqVO.getStatus());
        boolean contentChanged = false;
        if (!isChapter) {
            DocContentDO currentContent = docContentMapper.selectByDocAndVersion(old.getId(),
                    isDraft ? 0 : old.getVersion());
            if (currentContent == null) {
                // 老数据可能没有对应版本行（例如刚转成文档类型），视为有变化，补一行
                contentChanged = true;
            } else {
                contentChanged = !Objects.equals(currentContent.getTitle(), reqVO.getTitle())
                        || !Objects.equals(nullToEmpty(currentContent.getContent()), nullToEmpty(content))
                        || !Objects.equals(nullToEmpty(currentContent.getRawContent()), nullToEmpty(reqVO.getRawContent()))
                        || !Objects.equals(nullToEmpty(currentContent.getFiles()), nullToEmpty(reqVO.getFiles()));
            }
        }

        String account = currentAccount();
        LocalDateTime now = LocalDateTime.now();

        if (!isChapter && contentChanged) {
            Integer version = isDraft ? 0 : (old.getVersion() == null ? 1 : old.getVersion() + 1);
            saveDocContent(old.getId(), reqVO, version, account, now);
            reqVO.setStatus(isDraft ? DocStatusEnum.DRAFT.getStatus() : DocStatusEnum.NORMAL.getStatus());
        }

        DocDO updateObj = new DocDO();
        updateObj.setId(old.getId());
        updateObj.setLib(reqVO.getLib() == null ? old.getLib() : reqVO.getLib());
        updateObj.setModule(reqVO.getModule());
        updateObj.setTitle(reqVO.getTitle());
        updateObj.setKeywords(reqVO.getKeywords());
        updateObj.setType(reqVO.getType());
        updateObj.setStatus(StringUtils.hasText(reqVO.getStatus()) ? reqVO.getStatus()
                : (isDraft ? DocStatusEnum.DRAFT.getStatus() : DocStatusEnum.NORMAL.getStatus()));
        updateObj.setAcl(reqVO.getAcl());
        updateObj.setGroups(reqVO.getGroups());
        updateObj.setUsers(reqVO.getUsers());
        updateObj.setEditedBy(account);
        updateObj.setEditedDate(now);
        if (isDraft) {
            updateObj.setDraft(content);
            updateObj.setVersion(0);
        } else if (contentChanged) {
            updateObj.setDraft("");
            updateObj.setVersion(old.getVersion() == null ? 1 : old.getVersion() + 1);
        }
        docMapper.updateById(updateObj);

        // 换了库或换了父章节：重算自己和整棵子树的 path/grade/lib
        boolean structureChanged = !Objects.equals(old.getLib(), updateObj.getLib())
                || !Objects.equals(old.getParent(), parent);
        if (structureChanged) {
            applyStructureChange(old, updateObj.getLib(), parent);
        }

        actionService.recordActionWithChanges("doc", old.getId(), ActionTypeEnum.EDITED,
                "编辑文档：" + reqVO.getTitle(), old, updateObj);
    }

    /**
     * 写入版本内容 —— 禅道 {@code saveDocContent} 的等价实现。
     *
     * <p>这段逻辑是本模块的核心，三步：
     * <ol>
     *   <li>先看 {@code version=0} 的**草稿行**在不在：在就直接把它改成目标版本（草稿转正）</li>
     *   <li>没有草稿行，再看**目标版本行**是否已存在：存在就 UPDATE（避免撞唯一键）</li>
     *   <li>都不存在才 INSERT 新快照</li>
     * </ol>
     * 少任何一步都会出问题：漏了第 1 步，发布后草稿行会永远留在表里；
     * 漏了第 2 步，重复保存同一版本会撞 {@code UNIQUE(doc, version)}。
     */
    private void saveDocContent(Long docID, DocSaveReqVO reqVO, Integer version, String account, LocalDateTime now) {
        DocContentDO draft = docContentMapper.selectByDocAndVersion(docID, 0);
        if (draft != null) {
            DocContentDO updateObj = new DocContentDO();
            updateObj.setId(draft.getId());
            updateObj.setTitle(reqVO.getTitle());
            updateObj.setContent(reqVO.getContent());
            updateObj.setRawContent(reqVO.getRawContent());
            updateObj.setFiles(reqVO.getFiles() == null ? "" : reqVO.getFiles());
            updateObj.setType(contentTypeOf(reqVO.getType()));
            updateObj.setEditedBy(account);
            updateObj.setEditedDate(now);
            updateObj.setVersion(version);
            updateObj.setFromVersion(version == 0 ? 0 : Math.max(0, version - 1));
            docContentMapper.updateById(updateObj);
            return;
        }

        DocContentDO existed = docContentMapper.selectByDocAndVersion(docID, version);
        if (existed != null) {
            DocContentDO updateObj = new DocContentDO();
            updateObj.setId(existed.getId());
            updateObj.setTitle(reqVO.getTitle());
            updateObj.setContent(reqVO.getContent());
            updateObj.setRawContent(reqVO.getRawContent());
            updateObj.setFiles(reqVO.getFiles() == null ? "" : reqVO.getFiles());
            updateObj.setType(contentTypeOf(reqVO.getType()));
            updateObj.setEditedBy(account);
            updateObj.setEditedDate(now);
            docContentMapper.updateById(updateObj);
            return;
        }

        DocContentDO insertObj = new DocContentDO();
        insertObj.setDoc(docID);
        insertObj.setTitle(reqVO.getTitle());
        insertObj.setDigest("");
        insertObj.setContent(reqVO.getContent());
        insertObj.setRawContent(reqVO.getRawContent());
        insertObj.setFiles(reqVO.getFiles() == null ? "" : reqVO.getFiles());
        insertObj.setType(contentTypeOf(reqVO.getType()));
        insertObj.setAddedBy(account);
        insertObj.setAddedDate(now);
        insertObj.setEditedBy(account);
        insertObj.setEditedDate(now);
        insertObj.setVersion(version);
        insertObj.setFromVersion(Math.max(0, version - 1));
        docContentMapper.insert(insertObj);
    }

    // ================================================================
    // 文档：删除 / 查询
    // ================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDoc(Long id) {
        DocDO doc = validateDocExists(id);
        // 【有意偏离禅道】禅道删章节不管子节点，子节点会指向一个已删的父（悬空）。
        // 这里显式拒绝，避免出现「树断了但不报错」的情况（README 第 14 条坑的同类问题）。
        if (DocTypeEnum.isChapter(doc.getType())) {
            long children = docMapper.selectSelfAndDescendants(id).size() - 1L;
            if (children > 0) {
                throw exception(DOC_CHAPTER_NOT_LEAF, children);
            }
        }
        docMapper.deleteById(id);
        // 版本内容一起物理清理：文档都删了，快照没有存在意义，留着还会占住版本号
        docContentMapper.deleteByDocPhysical(id);
        actionService.recordAction("doc", id, ActionTypeEnum.DELETED, "删除文档：" + doc.getTitle());
    }

    @Override
    public DocDO validateDocExists(Long id) {
        DocDO doc = id == null ? null : docMapper.selectById(id);
        if (doc == null) {
            throw exception(DOC_NOT_EXISTS, id);
        }
        return doc;
    }

    @Override
    public DocRespVO getDoc(Long id, Integer version) {
        DocDO doc = validateDocExists(id);
        return convert(doc, version == null ? 0 : version);
    }

    @Override
    public PageResult<DocDO> getMyDocPage(DocPageReqVO reqVO) {
        return docMapper.selectMyPage(reqVO);
    }

    @Override
    public PageResult<DocDO> getDocPage(DocPageReqVO reqVO) {
        // 按空间查：先把空间下所有库的 id 取出来。
        // 注意空集合必须直接返回空页 —— inIfPresent 遇到空集合会**静默丢掉条件**，
        // 那就变成全量查询了（README 第 16 条坑）。
        List<Long> libIds = null;
        if (StringUtils.hasText(reqVO.getSpaceType())) {
            List<DocLibDO> libs = docLibMapper.selectListByObject(reqVO.getSpaceType(), reqVO.getSpaceObjectID());
            if (libs.isEmpty()) {
                return PageResult.empty();
            }
            libIds = libs.stream().map(DocLibDO::getId).toList();
        }
        if (reqVO.getLib() != null) {
            validateLibExists(reqVO.getLib());
        }
        return docMapper.selectPage(reqVO, libIds);
    }

    @Override
    public List<DocRespVO> getChapterTree(Long lib) {
        validateLibExists(lib);
        List<DocDO> chapters = docMapper.selectChapterList(lib);
        Map<Long, DocRespVO> nodes = new LinkedHashMap<>();
        for (DocDO chapter : chapters) {
            nodes.put(chapter.getId(), convert(chapter, 0));
        }
        List<DocRespVO> roots = new ArrayList<>();
        for (DocDO chapter : chapters) {
            DocRespVO node = nodes.get(chapter.getId());
            DocRespVO parentNode = chapter.getParent() == null ? null : nodes.get(chapter.getParent());
            if (parentNode == null) {
                roots.add(node);
            } else {
                if (parentNode.getChildren() == null) {
                    parentNode.setChildren(new ArrayList<>());
                }
                parentNode.getChildren().add(node);
            }
        }
        // 每个章节挂上「直属文档数」，前端树节点上直接显示
        for (Map.Entry<Long, DocRespVO> entry : nodes.entrySet()) {
            entry.getValue().setDocCount(countDocsInChapter(entry.getKey()));
        }
        return roots;
    }

    @Override
    public List<DocRespVO> getDocRespList(List<DocDO> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        // 批量取库名与父章节名：一次查库、一次查章节，避免逐行查成 N+1
        List<Long> libIds = list.stream().map(DocDO::getLib).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> libNames = new HashMap<>();
        if (!libIds.isEmpty()) {
            for (DocLibDO lib : docLibMapper.selectBatchIds(libIds)) {
                libNames.put(lib.getId(), lib.getName());
            }
        }
        List<Long> parentIds = list.stream().map(DocDO::getParent).filter(id -> id != null && id > 0)
                .distinct().toList();
        Map<Long, String> parentTitles = new HashMap<>();
        if (!parentIds.isEmpty()) {
            for (DocDO parent : docMapper.selectBatchIds(parentIds)) {
                parentTitles.put(parent.getId(), parent.getTitle());
            }
        }
        List<DocRespVO> result = new ArrayList<>(list.size());
        for (DocDO doc : list) {
            DocRespVO vo = BeanUtils.toBean(doc, DocRespVO.class);
            DocTypeEnum typeEnum = DocTypeEnum.of(doc.getType());
            vo.setTypeName(typeEnum == null ? doc.getType() : typeEnum.getName());
            vo.setChapter(DocTypeEnum.isChapter(doc.getType()));
            DocStatusEnum statusEnum = DocStatusEnum.of(doc.getStatus());
            vo.setStatusName(statusEnum == null ? doc.getStatus() : statusEnum.getName());
            vo.setLibName(libNames.getOrDefault(doc.getLib(), ""));
            vo.setParentTitle(doc.getParent() == null ? "" : parentTitles.getOrDefault(doc.getParent(), ""));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<DocContentRespVO> getContentList(Long id) {
        DocDO doc = validateDocExists(id);
        List<DocContentDO> list = docContentMapper.selectListByDoc(id);
        List<DocContentRespVO> result = new ArrayList<>(list.size());
        for (DocContentDO item : list) {
            DocContentRespVO vo = BeanUtils.toBean(item, DocContentRespVO.class);
            vo.setCurrent(Objects.equals(item.getVersion(), doc.getVersion()));
            vo.setDraft(item.getVersion() != null && item.getVersion() == 0);
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocRespVO viewDoc(Long id) {
        DocDO doc = validateDocExists(id);
        // 禅道只在 status=normal 时计数（草稿的浏览没有意义）
        boolean counted = DocStatusEnum.NORMAL.getStatus().equals(doc.getStatus());
        if (counted) {
            docMapper.increaseViews(id);
        }
        DocRespVO vo = convert(doc, 0);
        // 只在自己真的记了数的时候才 +1，否则返回的 views 会和库里对不上
        if (counted) {
            vo.setViews((doc.getViews() == null ? 0 : doc.getViews()) + 1);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveDoc(DocMoveReqVO reqVO) {
        DocDO doc = validateDocExists(reqVO.getId());
        validateLibExists(reqVO.getLib());
        Long parent = reqVO.getParent() == null ? 0L : reqVO.getParent();
        if (parent > 0) {
            if (Objects.equals(parent, doc.getId())) {
                throw exception(DOC_PARENT_IS_SELF_DESCENDANT);
            }
            DocDO parentDoc = validateDocExists(parent);
            if (!DocTypeEnum.isChapter(parentDoc.getType())) {
                throw exception(DOC_PARENT_NOT_CHAPTER, parent);
            }
            if (!Objects.equals(parentDoc.getLib(), reqVO.getLib())) {
                throw exception(DOC_PARENT_NOT_SAME_LIB);
            }
            // 移到自己的子孙下会成环
            if (StringUtils.hasText(doc.getPath()) && StringUtils.hasText(parentDoc.getPath())
                    && parentDoc.getPath().startsWith(doc.getPath())) {
                throw exception(DOC_PARENT_IS_SELF_DESCENDANT);
            }
        }
        validateTitleUnique(reqVO.getLib(), parent, doc.getTitle(), doc.getId());
        applyStructureChange(doc, reqVO.getLib(), parent);
        actionService.recordAction("doc", doc.getId(), ActionTypeEnum.EDITED,
                "移动文档：" + doc.getTitle() + " → 库 " + reqVO.getLib() + " 章节 " + parent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishDoc(Long id) {
        DocDO doc = validateDocExists(id);
        if (!DocStatusEnum.DRAFT.getStatus().equals(doc.getStatus())) {
            throw exception(DOC_ALREADY_PUBLISHED);
        }
        DocContentDO draft = docContentMapper.selectByDocAndVersion(id, 0);
        if (draft == null || !StringUtils.hasText(draft.getContent())) {
            throw exception(DOC_DRAFT_NO_CONTENT);
        }
        // 草稿行升成 v1（而不是插一行新的 v1，再删草稿行）—— 禅道也是直接改版本号
        DocContentDO contentUpdate = new DocContentDO();
        contentUpdate.setId(draft.getId());
        contentUpdate.setVersion(1);
        contentUpdate.setFromVersion(0);
        contentUpdate.setEditedBy(currentAccount());
        contentUpdate.setEditedDate(LocalDateTime.now());
        docContentMapper.updateById(contentUpdate);

        DocDO docUpdate = new DocDO();
        docUpdate.setId(id);
        docUpdate.setStatus(DocStatusEnum.NORMAL.getStatus());
        docUpdate.setVersion(1);
        docUpdate.setDraft("");
        docUpdate.setEditedBy(currentAccount());
        docUpdate.setEditedDate(LocalDateTime.now());
        docMapper.updateById(docUpdate);

        actionService.recordAction("doc", id, ActionTypeEnum.EDITED, "发布文档：" + doc.getTitle());
    }

    // ================================================================
    // 内部工具
    // ================================================================

    /**
     * 结构变更（换库 / 换父章节）：重算自己的 path/grade，并把整棵子树的 path 前缀与 grade 一起改掉。
     *
     * <p>子树用一条 {@code REPLACE} 式 SQL 更新 —— 逐行递归在深树上就是 N 次往返。
     * 注意子孙的 path 前缀是**自己的旧 path**（含自己），不是「旧 path + id」。
     */
    private void applyStructureChange(DocDO doc, Long newLib, Long newParent) {
        String oldPath = doc.getPath();
        String newPath = buildPath(newParent, doc.getId());
        Integer newGrade = buildGrade(newParent);

        docMapper.updateStructure(doc.getId(), newLib, newParent, newPath, newGrade, doc.getModule());

        if (StringUtils.hasText(oldPath) && !Objects.equals(oldPath, newPath)) {
            // 子孙的 path = 旧前缀 + 剩余部分；把它们的前缀替换成新前缀
            docMapper.moveSubtreePath(oldPath, newPath, oldPath.length(), newLib, doc.getId());
            int delta = newGrade - (doc.getGrade() == null ? 1 : doc.getGrade());
            if (delta != 0) {
                docMapper.shiftSubtreeGrade(newPath, delta, doc.getId());
            }
        }
    }

    /**
     * path = 父的 path + 自己的 id，逗号包起来且**包含自己**。
     *
     * <p>与 {@code zt_module} 的格式一致：一级 {@code ,1,}、二级 {@code ,1,2,}。
     * （禅道 doc 的拼法会多出一个逗号变成 {@code ,1,,2,}，本实现统一成单逗号格式，
     * 否则子树前缀匹配会失效。）
     */
    private String buildPath(Long parent, Long selfId) {
        if (parent == null || parent == 0) {
            return "," + selfId + ",";
        }
        DocDO parentDoc = docMapper.selectById(parent);
        if (parentDoc == null || !StringUtils.hasText(parentDoc.getPath())) {
            return "," + selfId + ",";
        }
        return parentDoc.getPath() + selfId + ",";
    }

    private Integer buildGrade(Long parent) {
        if (parent == null || parent == 0) {
            return 1;
        }
        DocDO parentDoc = docMapper.selectById(parent);
        return parentDoc == null || parentDoc.getGrade() == null ? 1 : parentDoc.getGrade() + 1;
    }

    /**
     * 库内的文档数（不含章节，不含子章节里的文档）
     */
    private int countDocsInLib(Long lib) {
        return docMapper.selectListByLib(lib).size();
    }

    /**
     * 某个章节下的直属文档数
     */
    private long countDocsInChapter(Long chapterID) {
        return docMapper.selectList(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<DocDO>()
                .eq(DocDO::getParent, chapterID)
                .ne(DocDO::getType, DocTypeEnum.CHAPTER.getType())).size();
    }

    private Long objectIDOfLib(String type, Long product, Long project, Long execution) {
        if (DocLibTypeEnum.PRODUCT.getType().equals(type)) {
            return product;
        }
        if (DocLibTypeEnum.PROJECT.getType().equals(type)) {
            return project;
        }
        if (DocLibTypeEnum.EXECUTION.getType().equals(type)) {
            return execution;
        }
        return 0L;
    }

    private void validateLibNameUnique(String type, Long objectID, Long parent, String name, Long excludeId) {
        DocLibDO existed = docLibMapper.selectByTypeAndObjectAndName(type, objectID, parent, name);
        if (existed != null && !Objects.equals(existed.getId(), excludeId)) {
            throw exception(DOC_LIB_NAME_DUPLICATE, name);
        }
    }

    private void validateTitleUnique(Long lib, Long parent, String title, Long excludeId) {
        DocDO existed = docMapper.selectByLibAndParentAndTitle(lib, parent == null ? 0L : parent, title);
        if (existed != null && !Objects.equals(existed.getId(), excludeId)) {
            throw exception(DOC_TITLE_DUPLICATE, title);
        }
    }

    private void validateUrl(String url) {
        if (!URL_PATTERN.matcher(url.trim()).matches()) {
            throw exception(DOC_URL_INVALID, url);
        }
    }

    /**
     * zt_doccontent.type 只区分 html / markdown / text；word/ppt/excel/attachment 这些都是附件型文档
     */
    private String contentTypeOf(String docType) {
        if ("markdown".equals(docType)) {
            return "markdown";
        }
        if ("text".equals(docType)) {
            return "text";
        }
        return "html";
    }

    /**
     * 详情转换：把指定版本的正文叠加到文档头上（与需求「头部 + 快照」的读法一致）。
     */
    private DocRespVO convert(DocDO doc, Integer version) {
        DocRespVO vo = BeanUtils.toBean(doc, DocRespVO.class);
        DocTypeEnum typeEnum = DocTypeEnum.of(doc.getType());
        vo.setTypeName(typeEnum == null ? doc.getType() : typeEnum.getName());
        vo.setChapter(DocTypeEnum.isChapter(doc.getType()));
        DocStatusEnum statusEnum = DocStatusEnum.of(doc.getStatus());
        vo.setStatusName(statusEnum == null ? doc.getStatus() : statusEnum.getName());
        if (doc.getLib() != null) {
            DocLibDO lib = docLibMapper.selectById(doc.getLib());
            vo.setLibName(lib == null ? "" : lib.getName());
        }
        if (doc.getParent() != null && doc.getParent() > 0) {
            DocDO parentDoc = docMapper.selectById(doc.getParent());
            vo.setParentTitle(parentDoc == null ? "" : parentDoc.getTitle());
        }
        // 章节没有正文；草稿取 version=0，正式取指定版本（默认当前版本）
        if (!DocTypeEnum.isChapter(doc.getType())) {
            int targetVersion = (version == null || version == 0) ? doc.getVersion() : version;
            DocContentDO content = docContentMapper.selectByDocAndVersion(doc.getId(), targetVersion);
            if (content != null) {
                vo.setContent(content.getContent());
                vo.setRawContent(content.getRawContent());
                vo.setFiles(content.getFiles());
            }
        }
        return vo;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 取当前登录用户的账号。与 StoryServiceImpl / ActionServiceImpl 保持同样的口径：
     * 禅道的人员字段存账号，而 yudao 的登录上下文里只有昵称，需要跨模块解析。
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
