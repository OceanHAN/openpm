package cn.iocoder.yudao.module.zentao.service.plan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.plan.vo.PlanPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.plan.vo.PlanSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.plan.PlanDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;

import java.util.List;
import java.util.Map;

/**
 * 产品计划 Service 接口
 *
 * <h3>禅道语义（module/productplan/model.php）</h3>
 * <ul>
 *   <li>计划是<b>产品维度</b>的：需求用 {@code zt_story.plan} 挂到计划上，发布再引用计划</li>
 *   <li>{@code branch} 是<b>多值</b>（逗号列表），一个计划可以覆盖多个分支；
 *       多分支产品的计划<b>必须选分支</b>（{@code create()} 里的校验）</li>
 *   <li>「待定」用日期哨兵 {@code 2030-01-01} 表示，不是状态</li>
 *   <li>{@code parent} 三态：0 独立 / &gt;0 子计划 / -1 有子计划</li>
 *   <li>状态：wait → doing → done；任意态 → closed（含原因）；closed → doing（激活）</li>
 *   <li>父计划的状态由子计划聚合推导（{@code updateParentStatus()}）</li>
 *   <li>关联需求：{@code type='story'} 的需求是<b>独占</b>一个计划（挂新计划会从旧计划移走），
 *       其它类型（需求/史诗）会<b>累加</b>成逗号列表</li>
 * </ul>
 */
public interface PlanService {

    /**
     * 创建计划。多分支产品必须选分支；子计划日期必须在父计划范围内
     */
    Long createPlan(PlanSaveReqVO createReqVO);

    /**
     * 修改计划。改分支后，超出新分支范围的需求/Bug 会自动解除关联
     */
    void updatePlan(PlanSaveReqVO updateReqVO);

    /**
     * 开始计划：wait → doing
     */
    void startPlan(Long id);

    /**
     * 完成计划：wait/doing → done
     */
    void finishPlan(Long id);

    /**
     * 关闭计划：非关闭态 → closed
     *
     * @param reason done 已完成 / cancel 已取消
     */
    void closePlan(Long id, String reason);

    /**
     * 激活计划：closed → doing（注意禅道激活后是「进行中」）
     */
    void activatePlan(Long id);

    /**
     * 删除计划。有子计划的父计划不能删除
     */
    void deletePlan(Long id);

    /**
     * 获得计划
     */
    PlanDO getPlan(Long id);

    /**
     * 校验计划存在
     */
    PlanDO validatePlanExists(Long id);

    /**
     * 获得计划分页
     */
    PageResult<PlanDO> getPlanPage(PlanPageReqVO reqVO);

    /**
     * 某个产品下的计划列表
     */
    List<PlanDO> getPlanListByProduct(Long product, Long branch);

    /**
     * 计划下的需求（按 plan 逗号列表匹配）
     */
    List<StoryDO> getPlanStories(Long planId);

    /**
     * 计划下还没有关联的需求候选（同产品、同分支、未挂在任何计划上的需求）
     */
    List<StoryDO> getUnlinkedStories(Long planId);

    /**
     * 关联需求到计划
     */
    void linkStories(Long planId, List<Long> storyIds);

    /**
     * 解除需求与计划的关联
     */
    void unlinkStory(Long planId, Long storyId);

    /**
     * 关联 Bug 到计划
     */
    void linkBugs(Long planId, List<Long> bugIds);

    /**
     * 解除 Bug 与计划的关联
     */
    void unlinkBug(Long planId, Long bugId);

    /**
     * 计划下关联的需求数量（实时统计）
     */
    Long countStoriesByPlan(Long planId);

    /**
     * 计划下关联的 Bug 数量（实时统计）
     */
    Long countBugsByPlan(Long planId);

    /**
     * 子计划数量
     */
    Long getPlanChildrenCount(Long planId);

    /**
     * 批量统计多个计划的需求数（列表页用，一次查询，避免 N+1）
     *
     * @param planIds 计划编号集合
     * @return 计划编号 → 需求数
     */
    Map<Long, Long> countStoriesByPlans(List<Long> planIds);

}
