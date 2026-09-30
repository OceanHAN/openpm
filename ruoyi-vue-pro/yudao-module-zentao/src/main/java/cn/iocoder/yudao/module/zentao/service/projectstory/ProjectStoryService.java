package cn.iocoder.yudao.module.zentao.service.projectstory;

import cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo.ProjectProductLinkReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo.ProjectProductRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.projectstory.vo.ProjectStoryRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;

import java.util.List;

/**
 * 项目/执行需求范围 Service 接口
 *
 * <h3>禅道语义（execution::linkStory / unlinkStory）</h3>
 * <ul>
 *   <li>项目要先<b>关联产品</b>（{@code zt_projectproduct}），该产品的需求才能进入候选</li>
 *   <li>关联需求（{@code zt_projectstory}）时记录<b>需求当时的版本</b>：
 *       需求后续正式变更不会改掉项目已排期的版本，列表里标出「版本已变更」</li>
 *   <li>已关联的、以及状态为 draft/reviewing/closed 的需求会被跳过</li>
 *   <li>项目（type=project）上移除需求时，若其<b>子执行</b>已关联该需求则拒绝</li>
 *   <li>移除后剩余关系重新编号 1..n</li>
 * </ul>
 */
public interface ProjectStoryService {

    /**
     * 项目关联产品
     */
    Long linkProduct(ProjectProductLinkReqVO reqVO);

    /**
     * 解除项目与产品的关联（产品下还有需求在范围内时拒绝）
     */
    void unlinkProduct(Long project, Long product, Long branch);

    /**
     * 项目关联的产品列表
     */
    List<ProjectProductRespVO> getLinkedProducts(Long project);

    /**
     * 批量关联需求到项目/执行
     *
     * @return 实际关联成功的需求编号
     */
    List<Long> linkStories(Long project, List<Long> storyIds);

    /**
     * 从项目/执行移除需求
     */
    void unlinkStory(Long project, Long storyId);

    /**
     * 项目/执行下的需求列表（带「版本已变更」标记）
     */
    List<ProjectStoryRespVO> getProjectStories(Long project);

    /**
     * 还没纳入项目范围的需求候选（来自已关联的产品，且状态允许）
     */
    List<StoryDO> getUnlinkedStories(Long project);

    /**
     * 某个需求被哪些项目/执行关联
     */
    List<Long> getRelatedProjects(Long storyId);

    /**
     * 项目/执行下的需求数量
     */
    Long countStories(Long project);

}
