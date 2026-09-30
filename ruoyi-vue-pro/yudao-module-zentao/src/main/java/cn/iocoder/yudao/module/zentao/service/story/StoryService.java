package cn.iocoder.yudao.module.zentao.service.story;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryChangeReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryCloseReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryReviewStartReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryReviewSubmitReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StorySaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryTreeNodeRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.story.vo.StoryTypeRespVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryReviewDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StorySpecDO;

import java.util.List;
import java.util.Map;

/**
 * 需求 Service 接口
 *
 * 版本语义（对齐禅道）：
 * <ul>
 *   <li>{@link #updateStory} —— 普通编辑。原地修改「当前版本」的快照，版本号不变。</li>
 *   <li>{@link #changeStory} —— 正式变更。版本号 +1，并向 {@code zt_storyspec} 追加一条新快照。</li>
 * </ul>
 */
public interface StoryService {

    /**
     * 创建需求。同时写入 version=1 的版本快照。
     *
     * @param createReqVO 需求信息
     * @return 需求编号
     */
    Long createStory(StorySaveReqVO createReqVO);

    /**
     * 修改需求（普通编辑，不改版本号）。
     * 若标题/描述/验收标准有变化，则原地更新当前版本的快照。
     *
     * @param updateReqVO 需求信息
     */
    void updateStory(StorySaveReqVO updateReqVO);

    /**
     * 变更需求（正式变更，版本号 +1，追加历史快照）
     *
     * @param reqVO 变更信息
     * @return 变更后的新版本号
     */
    Integer changeStory(StoryChangeReqVO reqVO);

    /**
     * 关闭需求
     *
     * @param reqVO 关闭信息（原因、重复需求编号）
     */
    void closeStory(StoryCloseReqVO reqVO);

    /**
     * 激活需求（从已关闭回到激活态）
     *
     * @param id 需求编号
     */
    void activateStory(Long id);

    /**
     * 删除需求（逻辑删除）
     *
     * @param id 需求编号
     */
    void deleteStory(Long id);

    /**
     * 批量删除需求
     *
     * @param ids 需求编号数组
     */
    void deleteStoryList(List<Long> ids);

    /**
     * 获得需求（当前版本）
     *
     * @param id 需求编号
     * @return 需求（含从快照表叠加的 spec / verify）
     */
    StoryDO getStory(Long id);

    /**
     * 获得指定版本的需求
     *
     * @param id      需求编号
     * @param version 版本号。传 null 或 0 表示当前版本（对应禅道 getById 的 version=0 语义）
     * @return 该版本的需求内容
     */
    StoryDO getStoryByVersion(Long id, Integer version);

    /**
     * 校验需求是否存在
     *
     * @param id 需求编号
     * @return 需求
     */
    StoryDO validateStoryExists(Long id);

    /**
     * 获得需求的版本历史（版本号倒序）
     *
     * @param storyId 需求编号
     * @return 版本快照列表
     */
    List<StorySpecDO> getStorySpecList(Long storyId);

    /**
     * 获得需求分页
     *
     * @param reqVO 分页条件
     * @return 分页结果
     */
    PageResult<StoryDO> getStoryPage(StoryPageReqVO reqVO);

    /**
     * 获得某产品下的全部需求
     *
     * @param product 产品编号
     * @return 需求列表
     */
    List<StoryDO> getStoryListByProduct(Long product);

    // ==================== 需求评审 ====================

    /**
     * 提交需求评审。
     * 把评审人写入 {@code zt_storyreview}（每人一行，结果为空），并把需求置为 reviewing。
     * 重复提交时会剔除不再参与的评审人（对应禅道 doUpdateReviewer）。
     *
     * @param reqVO 提交信息
     */
    void startReview(StoryReviewStartReqVO reqVO);

    /**
     * 评审人表决。
     * 只有当前版本的全部评审人都提交后，才会按聚合规则触发需求状态流转
     * （对应禅道 updateStoryByReview + getReviewResult + setStatusByReviewResult）。
     *
     * @param reqVO 表决信息
     * @return 聚合结果；为空表示还有人未评审
     */
    String submitReview(StoryReviewSubmitReqVO reqVO);

    /**
     * 获得某个需求某个版本的评审情况
     *
     * @param storyId 需求编号
     * @param version 版本号，为空或 0 表示当前版本
     * @return 评审记录列表
     */
    List<StoryReviewDO> getStoryReviewList(Long storyId, Integer version);

    /**
     * 某需求关联的用例
     */
    // ==================== 父子需求（分解） ====================

    /**
     * 某需求分解出来的子需求（按 path 排序）
     */
    List<StoryDO> getChildList(Long parentId);

    /**
     * 批量把已有需求挂到父需求下（禅道 subdivide）
     *
     * @return 实际挂上的子需求数
     */
    int subdivide(Long parentId, List<Long> childIds);

    /**
     * 把一条需求拆成若干子需求（只给标题，其余字段从父需求继承）
     *
     * @return 子需求编号列表
     */
    List<Long> batchCreateChild(Long parentId, List<String> titles);

    /**
     * 把父需求的信息补进 VO（父需求标题 / 子需求数 / 父需求是否已变更）
     */
    void fillParentInfo(List<StoryRespVO> list);

    /**
     * 子需求状态或工时变化后，回头刷新父需求（禅道 updateParentStatus）
     */
    void refreshParent(Long childId);

    // ==================== 需求分层（业务需求 / 用户需求 / 研发需求） ====================

    /**
     * 需求分层类型字典。
     *
     * <p>禅道用 {@code $config->enableER} / {@code $config->URAndSR} 决定要不要展示
     * 业务需求/用户需求（开源版默认关闭），本实现不做这个开关，三层始终可用。
     */
    List<StoryTypeRespVO> getTypeList();

    /**
     * 某产品下各需求分层类型的数量（需求池页签角标）
     *
     * @param product 产品编号
     * @return key 为类型值（epic/requirement/story），value 为数量；没有数据的类型也会返回 0
     */
    Map<String, Long> getTypeSummary(Long product);

    /**
     * 需求分层树：业务需求 → 用户需求 → 研发需求（按 parent 组装的嵌套结构）
     *
     * @param product 产品编号
     * @param rootId  只看某一棵子树（传 null 看整个产品的需求森林）
     */
    List<StoryTreeNodeRespVO> getTypeTree(Long product, Long rootId);

}
