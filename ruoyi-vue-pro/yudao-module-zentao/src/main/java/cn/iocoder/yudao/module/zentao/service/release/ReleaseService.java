package cn.iocoder.yudao.module.zentao.service.release;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.release.vo.ReleasePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.release.vo.ReleaseSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.release.ReleaseDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布 Service 接口
 *
 * <h3>禅道语义（module/release/model.php）</h3>
 * <ul>
 *   <li>发布名<b>全局唯一</b>（禅道按 {@code system} 查重，system 默认 0）</li>
 *   <li>创建发布时会**自动生成影子构建**（同名/同产品/同分支/同日期的 zt_build），
 *       {@code release.shadow} 指向它；改名称/构建/日期时影子构建同步更新</li>
 *   <li>选了构建就从构建（含集成构建的子构建）**同步需求与 Bug** 到发布里</li>
 *   <li>三份清单：{@code stories} 完成的需求 / {@code bugs} 解决的 Bug / {@code leftBugs} 遗留的 Bug</li>
 *   <li>状态：wait/normal/fail/terminate；publish 到 normal 时把需求阶段置为「已发布」</li>
 *   <li>{@code zt_releaserelated} 记录发布 ↔ 项目/构建/分支/子发布/需求/Bug 的泛化关系</li>
 * </ul>
 */
public interface ReleaseService {

    /**
     * 创建发布。会同时创建影子构建，并按需从构建同步需求/Bug
     */
    Long createRelease(ReleaseSaveReqVO createReqVO);

    /**
     * 修改发布。名称/构建/日期变化会同步影子构建
     */
    void updateRelease(ReleaseSaveReqVO updateReqVO);

    /**
     * 删除发布（连带影子构建与关联关系）
     */
    void deleteRelease(Long id);

    /**
     * 发布：状态置为 normal，写入实际发布日期，并把关联需求的阶段推进到「已发布」
     */
    void publishRelease(Long id, LocalDateTime releasedDate);

    /**
     * 修改发布状态：fail / terminate / wait 等
     */
    void changeStatus(Long id, String status, LocalDateTime releasedDate);

    /**
     * 获得发布
     */
    ReleaseDO getRelease(Long id);

    /**
     * 校验发布存在
     */
    ReleaseDO validateReleaseExists(Long id);

    /**
     * 获得发布分页
     */
    PageResult<ReleaseDO> getReleasePage(ReleasePageReqVO reqVO);

    /**
     * 产品下的发布列表
     */
    List<ReleaseDO> getReleaseListByProduct(Long product, Long branch);

    /**
     * 发布下的需求
     */
    List<StoryDO> getReleaseStories(Long releaseId);

    /**
     * 还没关联到该发布的需求候选（同产品、未关闭）
     */
    List<StoryDO> getUnlinkedStories(Long releaseId);

    /**
     * 发布下的 Bug
     *
     * @param type bug 本次解决 / leftBug 遗留
     */
    List<BugDO> getReleaseBugs(Long releaseId, String type);

    /**
     * 还没关联到该发布的 Bug 候选
     */
    List<BugDO> getUnlinkedBugs(Long releaseId, String type);

    /**
     * 关联需求到发布
     */
    void linkStories(Long releaseId, List<Long> storyIds);

    /**
     * 解除需求与发布的关联
     */
    void unlinkStory(Long releaseId, Long storyId);

    /**
     * 关联 Bug 到发布
     *
     * @param type bug 本次解决 / leftBug 遗留
     */
    void linkBugs(Long releaseId, String type, List<Long> bugIds);

    /**
     * 解除 Bug 与发布的关联
     */
    void unlinkBug(Long releaseId, String type, Long bugId);

    /**
     * 该发布是否被其它发布包含
     */
    boolean isIncludedByOther(Long releaseId);

    /**
     * 发布关联的项目编号（从 zt_releaserelated 读，供列表展示）
     */
    List<Long> getRelatedIds(Long releaseId, String objectType);

    /**
     * 缺陷解决时按「解决版本」自动回写（禅道 {@code module/bug/model.php:2075}）：
     * <ol>
     *   <li>把该 Bug 并进「解决版本」对应的构建的 Bug 清单（{@code zt_build.bugs}）；</li>
     *   <li>再找到**包含这个构建**的发布（或它的影子构建），把 Bug 并进发布的 Bug 清单，
     *       并同步 {@code zt_releaserelated} 关系行。</li>
     * </ol>
     *
     * @param product       缺陷所属产品（发布的 product 必须一致）
     * @param bugId         缺陷编号
     * @param resolvedBuild 解决版本（禅道存的是**构建编号**的字符串）
     * @return 被回写的发布编号；没有匹配的发布时返回 null
     */
    Long appendBugByResolvedBuild(Long product, Long bugId, String resolvedBuild);

}
