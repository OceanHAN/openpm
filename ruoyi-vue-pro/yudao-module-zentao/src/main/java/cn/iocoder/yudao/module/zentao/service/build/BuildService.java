package cn.iocoder.yudao.module.zentao.service.build;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.build.vo.BuildPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.build.vo.BuildSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.build.BuildDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;

import java.util.List;
import java.util.Map;

/**
 * 构建 Service 接口
 *
 * <h3>禅道语义（module/build/model.php）</h3>
 * <ul>
 *   <li>构建属于某个执行、引用某个产品，记录本次完成的需求（{@code stories}）与解决的 Bug（{@code bugs}），
 *       两者都是<b>逗号列表</b></li>
 *   <li><b>集成构建</b>：{@code builds} 列子构建，{@code execution} 固定 0，
 *       {@code branch} 取子构建分支的并集；读取时 stories/bugs 要把子构建的并进来</li>
 *   <li>构建名在同一 (product, branch) 下唯一；多分支产品必须选分支</li>
 *   <li><b>关联 Bug 会顺手解决它</b>：未解决/未关闭的 Bug 会被置为
 *       {@code resolved} + {@code resolution=fixed} + {@code resolvedBuild=构建编号}，
 *       并指派回创建人（禅道 {@code updateLinkedBug()}）</li>
 *   <li>被集成构建或发布引用的构建（{@code isChild}）不能改产品/执行/子构建</li>
 * </ul>
 */
public interface BuildService {

    /**
     * 创建构建
     */
    Long createBuild(BuildSaveReqVO createReqVO);

    /**
     * 修改构建
     */
    void updateBuild(BuildSaveReqVO updateReqVO);

    /**
     * 删除构建
     */
    void deleteBuild(Long id);

    /**
     * 获得构建。集成构建会把子构建的 stories/bugs 并进来
     */
    BuildDO getBuild(Long id);

    /**
     * 校验构建存在
     */
    BuildDO validateBuildExists(Long id);

    /**
     * 获得构建分页
     */
    PageResult<BuildDO> getBuildPage(BuildPageReqVO reqVO);

    /**
     * 某个产品下的构建列表
     */
    List<BuildDO> getBuildListByProduct(Long product, Long branch);

    /**
     * 某个执行下的构建列表
     */
    List<BuildDO> getBuildListByExecution(Long execution);

    /**
     * 构建下的需求
     */
    List<StoryDO> getBuildStories(Long buildId);

    /**
     * 还没关联到该构建的需求候选（同产品、且未关联任何构建）
     */
    List<StoryDO> getUnlinkedStories(Long buildId);

    /**
     * 构建下的 Bug
     */
    List<BugDO> getBuildBugs(Long buildId);

    /**
     * 还没关联到该构建的 Bug 候选
     */
    List<BugDO> getUnlinkedBugs(Long buildId);

    /**
     * 关联需求到构建
     */
    void linkStories(Long buildId, List<Long> storyIds);

    /**
     * 解除需求与构建的关联
     */
    void unlinkStory(Long buildId, Long storyId);

    /**
     * 关联 Bug 到构建。未解决的 Bug 会被顺手置为已解决，resolvedBuild 指向本构建
     *
     * @param resolvedBy Bug 编号 → 解决者账号，可空
     */
    void linkBugs(Long buildId, List<Long> bugIds, Map<Long, String> resolvedBy);

    /**
     * 解除 Bug 与构建的关联（不会回退 Bug 的解决状态，与禅道一致）
     */
    void unlinkBug(Long buildId, Long bugId);

    /**
     * 构建是否是「子构建」：被别的构建的 builds 引用（发布引用检查在 release 模块接入后补充）
     */
    boolean isChildBuild(Long buildId);

}
