package cn.iocoder.yudao.module.zentao.service.repo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.repo.vo.RepoCommitPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.repo.vo.RepoCommitRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.repo.vo.RepoPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.repo.vo.RepoSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.repo.RelationDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.repo.RepoDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.repo.RepoFilesDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.repo.RepoHistoryDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.task.TaskDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.bug.BugMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.repo.RelationMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.repo.RepoFilesMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.repo.RepoHistoryMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.repo.RepoMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.story.StoryMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.task.TaskMapper;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.COMMIT_NOT_EXISTS;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.REPO_NAME_DUPLICATE;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.REPO_NOT_EXISTS;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.REPO_PATH_INVALID;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.REPO_SCM_UNSUPPORTED;

/**
 * 代码库（禅道 {@code module/repo}）。
 *
 * <h3>同步做什么</h3>
 * 跑一次 {@code git log}（增量：从上次同步到的 sha 往后），把提交写进 {@code zt_repohistory}、
 * 改动文件写进 {@code zt_repofiles}，再把提交说明里解析出的 {@code Story #1 / Task #2 / Bug #3}
 * 写成 {@code zt_relation} 的关系行 —— 于是「这个需求是哪几次提交做完的」可以反查。
 *
 * <h3>为什么同步要幂等</h3>
 * 同步是可以反复点的（禅道也有定时任务），所以：同一个 sha 只入库一次；
 * 重新同步某条提交时先清掉它的文件清单与关系行再写 —— 不然点两次就会出现重复文件/重复关联。
 */
@Service
@Slf4j
public class RepoService {

    /** 一次同步最多拉多少条提交（防止第一次把几万条历史全灌进来） */
    private static final int DEFAULT_SYNC_LIMIT = 200;

    @Resource
    private RepoMapper repoMapper;

    @Resource
    private RepoHistoryMapper repoHistoryMapper;

    @Resource
    private RepoFilesMapper repoFilesMapper;

    @Resource
    private RelationMapper relationMapper;

    @Resource
    private StoryMapper storyMapper;

    @Resource
    private TaskMapper taskMapper;

    @Resource
    private BugMapper bugMapper;

    @Resource
    private ActionService actionService;

    // ==================== 增删改查 ====================

    public Long createRepo(RepoSaveReqVO reqVO) {
        validateNameUnique(null, reqVO.getName());
        validatePath(reqVO.getPath());
        RepoDO repo = BeanUtils.toBean(reqVO, RepoDO.class);
        repo.setId(null);
        applyDefaults(repo);
        repoMapper.insert(repo);
        return repo.getId();
    }

    public void updateRepo(RepoSaveReqVO reqVO) {
        RepoDO old = validateRepoExists(reqVO.getId());
        validateNameUnique(reqVO.getId(), reqVO.getName());
        validatePath(reqVO.getPath());
        RepoDO update = BeanUtils.toBean(reqVO, RepoDO.class);
        applyDefaults(update);
        // 路径换了要把「同步到哪儿了」清掉，否则增量同步会从旧仓库的 sha 开始找
        if (!old.getPath().equals(update.getPath())) {
            update.setSynced(0);
            update.setLastSyncRevision("");
        }
        repoMapper.updateById(update);
    }

    public void deleteRepo(Long id) {
        validateRepoExists(id);
        // 提交记录与改动文件是事实数据（没有 deleted 列），删库时一起物理清掉
        relationMapper.deleteByRepo(id);
        repoFilesMapper.deleteByRepo(id);
        repoHistoryMapper.delete(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<RepoHistoryDO>()
                .eq(RepoHistoryDO::getRepo, id));
        repoMapper.deleteById(id);
    }

    public RepoDO getRepo(Long id) {
        return validateRepoExists(id);
    }

    public PageResult<RepoDO> getRepoPage(RepoPageReqVO reqVO) {
        return repoMapper.selectPage(reqVO);
    }

    public List<RepoDO> getSimpleList() {
        return repoMapper.selectSimpleList();
    }

    // ==================== 同步 ====================

    /**
     * 同步提交记录。
     *
     * @param repoId   代码库编号
     * @param maxCount 最多拉多少条，空则默认 200
     * @return 这次新入库的提交数
     */
    @Transactional(rollbackFor = Exception.class)
    public int sync(Long repoId, Integer maxCount) {
        RepoDO repo = validateRepoExists(repoId);
        validatePath(repo.getPath());
        int limit = maxCount == null || maxCount <= 0 ? DEFAULT_SYNC_LIMIT : maxCount;
        String stopAt = Integer.valueOf(1).equals(repo.getSynced()) ? repo.getLastSyncRevision() : null;

        List<GitLogReader.Commit> commits = GitLogReader.read(repo.getPath(), limit, stopAt);
        int seq = repoHistoryMapper.selectMaxCommit(repoId);
        int imported = 0;
        // git log 是「新 → 旧」，倒过来入库，commit 序号才和时间同向
        for (int i = commits.size() - 1; i >= 0; i--) {
            GitLogReader.Commit commit = commits.get(i);
            RepoHistoryDO existing = repoHistoryMapper.selectByRevision(repoId, commit.getRevision());
            Long historyId;
            if (existing == null) {
                RepoHistoryDO history = new RepoHistoryDO();
                history.setRepo(repoId);
                history.setRevision(commit.getRevision());
                history.setCommit(++seq);
                history.setComment(commit.getComment());
                history.setCommitter(commit.getCommitter());
                history.setTime(commit.getTime());
                repoHistoryMapper.insert(history);
                historyId = history.getId();
                imported++;
            } else {
                historyId = existing.getId();
                // 说明可能被改过（amend/rebase），重新同步时一并刷新
                RepoHistoryDO update = new RepoHistoryDO();
                update.setId(existing.getId());
                update.setComment(commit.getComment());
                update.setCommitter(commit.getCommitter());
                update.setTime(commit.getTime());
                repoHistoryMapper.updateById(update);
            }
            saveFiles(repoId, commit);
            saveRelations(historyId, commit);
        }

        RepoDO update = new RepoDO();
        update.setId(repoId);
        update.setSynced(1);
        update.setLastSyncRevision(GitLogReader.headRevision(repo.getPath()));
        update.setLastSyncDate(LocalDateTime.now());
        update.setLastSyncCount(imported);
        repoMapper.updateById(update);

        if (imported > 0) {
            actionService.recordAction("repo", repoId, ActionTypeEnum.EDITED, "同步提交：" + imported + " 条");
        }
        return imported;
    }

    /** 落盘改动文件（先清后写，保证重复同步不重复） */
    private void saveFiles(Long repoId, GitLogReader.Commit commit) {
        repoFilesMapper.deleteByRevision(repoId, commit.getRevision());
        for (GitLogReader.ChangedFile file : commit.getFiles()) {
            RepoFilesDO row = new RepoFilesDO();
            row.setRepo(repoId);
            row.setRevision(commit.getRevision());
            row.setPath(file.getPath());
            row.setOldPath(file.getOldPath() == null ? "" : file.getOldPath());
            row.setType(file.getPath() != null && file.getPath().contains("/") ? "file" : "file");
            row.setAction(file.getAction());
            repoFilesMapper.insert(row);
        }
    }

    /** 把提交说明里解析出的对象关系写进 zt_relation（先清后写） */
    private void saveRelations(Long historyId, GitLogReader.Commit commit) {
        relationMapper.deleteByRevision(historyId);
        for (String key : GitLogReader.parseLinkedKeys(commit.getComment())) {
            String[] parts = key.split(":");
            RelationDO relation = new RelationDO();
            relation.setAType("revision");
            relation.setAId(historyId);
            relation.setRelation("commit");
            relation.setBType(parts[0]);
            relation.setBId(Long.parseLong(parts[1]));
            relation.setProduct(0L);
            relationMapper.insert(relation);
        }
    }

    // ==================== 查询 ====================

    /** 提交分页：可以按代码库，也可以按「某个需求/任务/缺陷关联的提交」 */
    public PageResult<RepoCommitRespVO> getCommitPage(RepoCommitPageReqVO reqVO) {
        Map<Long, String> repoNames = repoNames();
        if (StringUtils.hasText(reqVO.getObjectType()) && reqVO.getObjectID() != null) {
            List<RepoHistoryDO> all = repoHistoryMapper.selectListByObject(reqVO.getObjectType(), reqVO.getObjectID(),
                    reqVO.getRepo());
            int from = Math.max(0, (reqVO.getPageNo() - 1) * reqVO.getPageSize());
            int to = Math.min(all.size(), from + reqVO.getPageSize());
            List<RepoCommitRespVO> list = new ArrayList<>();
            for (RepoHistoryDO history : from >= all.size() ? List.<RepoHistoryDO>of() : all.subList(from, to)) {
                list.add(toRespVO(history, repoNames, false));
            }
            return new PageResult<>(list, (long) all.size());
        }
        PageResult<RepoHistoryDO> page = repoHistoryMapper.selectPage(reqVO);
        List<RepoCommitRespVO> list = new ArrayList<>();
        for (RepoHistoryDO history : page.getList()) {
            list.add(toRespVO(history, repoNames, false));
        }
        return new PageResult<>(list, page.getTotal());
    }

    /** 提交详情：带改动文件与关联对象 */
    public RepoCommitRespVO getCommit(Long repo, String revision) {
        RepoHistoryDO history = repoHistoryMapper.selectByRevision(repo, revision);
        if (history == null) {
            throw exception(COMMIT_NOT_EXISTS, revision);
        }
        RepoCommitRespVO vo = toRespVO(history, repoNames(), true);
        List<Map<String, Object>> files = new ArrayList<>();
        for (RepoFilesDO file : repoFilesMapper.selectListByRevision(repo, revision)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("action", file.getAction());
            item.put("path", file.getPath());
            item.put("oldPath", file.getOldPath());
            files.add(item);
        }
        vo.setFiles(files);
        return vo;
    }

    // ==================== 内部 ====================

    private RepoCommitRespVO toRespVO(RepoHistoryDO history, Map<Long, String> repoNames, boolean withLinks) {
        RepoCommitRespVO vo = BeanUtils.toBean(history, RepoCommitRespVO.class);
        vo.setRepoName(repoNames.getOrDefault(history.getRepo(), ""));
        int fileCount = repoFilesMapper.selectListByRevision(history.getRepo(), history.getRevision()).size();
        vo.setFileCount(fileCount);
        if (withLinks) {
            List<Map<String, Object>> links = new ArrayList<>();
            for (RelationDO relation : relationMapper.selectListByRevision(history.getId())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("objectType", relation.getBType());
                item.put("objectID", relation.getBId());
                item.put("objectName", objectName(relation.getBType(), relation.getBId()));
                links.add(item);
            }
            vo.setLinkedObjects(links);
        }
        return vo;
    }

    /** 关联对象的名字：按类型去对应表里取（取不到就留空，不报错 —— 对象可能已被删） */
    private String objectName(String objectType, Long objectId) {
        try {
            switch (objectType) {
                case "story" -> {
                    StoryDO story = storyMapper.selectById(objectId);
                    return story == null ? "" : story.getTitle();
                }
                case "task" -> {
                    TaskDO task = taskMapper.selectById(objectId);
                    return task == null ? "" : task.getName();
                }
                case "bug" -> {
                    BugDO bug = bugMapper.selectById(objectId);
                    return bug == null ? "" : bug.getTitle();
                }
                default -> {
                    return "";
                }
            }
        } catch (Exception e) {
            return "";
        }
    }

    private Map<Long, String> repoNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (RepoDO repo : repoMapper.selectList()) {
            names.put(repo.getId(), repo.getName());
        }
        return names;
    }

    private RepoDO validateRepoExists(Long id) {
        RepoDO repo = id == null ? null : repoMapper.selectById(id);
        if (repo == null) {
            throw exception(REPO_NOT_EXISTS);
        }
        return repo;
    }

    private void validateNameUnique(Long id, String name) {
        RepoDO exists = repoMapper.selectByName(name);
        if (exists != null && !exists.getId().equals(id)) {
            throw exception(REPO_NAME_DUPLICATE, name);
        }
    }

    /** 本实现只支持本地 git 仓库：路径必须存在且含 .git（否则同步时才发现就太晚了） */
    private void validatePath(String path) {
        if (!GitLogReader.isGitRepo(path)) {
            throw exception(REPO_PATH_INVALID, path);
        }
    }

    private void applyDefaults(RepoDO repo) {
        if (!StringUtils.hasText(repo.getScmType())) {
            repo.setScmType("git");
        }
        if (!"git".equalsIgnoreCase(repo.getScmType())) {
            throw exception(REPO_SCM_UNSUPPORTED, repo.getScmType());
        }
        if (!StringUtils.hasText(repo.getAcl())) {
            repo.setAcl("open");
        }
        if (!StringUtils.hasText(repo.getStatus())) {
            repo.setStatus("active");
        }
        if (repo.getProduct() == null) {
            repo.setProduct("");
        }
        if (repo.getSynced() == null) {
            repo.setSynced(0);
        }
    }

}
