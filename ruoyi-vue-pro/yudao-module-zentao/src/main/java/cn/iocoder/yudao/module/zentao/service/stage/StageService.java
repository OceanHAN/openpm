package cn.iocoder.yudao.module.zentao.service.stage;

import cn.iocoder.yudao.module.zentao.controller.admin.stage.vo.StageRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.stage.vo.StageSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.stage.StageDO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 阶段（瀑布流程）Service 接口
 *
 * <h3>禅道语义（module/stage/model.php）</h3>
 * <ul>
 *   <li>{@code zt_stage} 是<b>流程模板</b>：workflowGroup=1 下有 需求/设计/开发/测试/发布 五个阶段，
 *       各自带工作量占比；同一模板下占比累计不能超过 100%</li>
 *   <li>项目里<b>实际的阶段</b>是 {@code zt_project} 里 {@code type='stage'} 的记录，
 *       由「按模板生成」产生；项目用 {@code workflowGroup} 记住自己用的是哪套模板</li>
 *   <li>阶段实例与执行共用表，所以它的状态流转可以直接复用执行模块
 *       （wait → doing → closed）</li>
 * </ul>
 */
public interface StageService {

    /**
     * 新建阶段模板。同组内名称唯一，占比累计不能超过 100%
     */
    Long createStage(StageSaveReqVO createReqVO);

    /**
     * 批量新建阶段模板（禅道 batchCreate 的等价实现）
     */
    List<Long> batchCreateStages(Long workflowGroup, List<StageSaveReqVO> stages);

    /**
     * 修改阶段模板
     */
    void updateStage(StageSaveReqVO updateReqVO);

    /**
     * 删除阶段模板（只删模板，已生成的项目阶段不受影响）
     */
    void deleteStage(Long id);

    /**
     * 批量调整排序（按传入的编号顺序重排为 1..n）
     */
    void updateOrder(List<Long> stageIds);

    /**
     * 获得阶段模板
     */
    StageDO getStage(Long id);

    /**
     * 校验阶段模板存在
     */
    StageDO validateStageExists(Long id);

    /**
     * 某个流程模板组下的阶段
     */
    List<StageDO> getStageListByGroup(Long workflowGroup);

    /**
     * 某一类项目流程（waterfall/waterfallplus/ipd）的模板组列表
     */
    List<StageDO> getStageListByProjectType(String projectType);

    /**
     * 某个模板组的占比合计
     */
    BigDecimal getTotalPercent(Long workflowGroup);

    /**
     * 按模板为项目生成阶段实例（写入 zt_project，type='stage'）
     *
     * @param project       项目编号
     * @param workflowGroup 流程模板组；传 null 时用项目自身的 workflowGroup，
     *                      再退化为按项目 model 找第一套模板
     * @return 生成的阶段编号
     */
    List<Long> generateStages(Long project, Long workflowGroup);

    /**
     * 项目下的阶段列表
     */
    List<StageRespVO> getProjectStages(Long project);

    /**
     * 删除项目的全部阶段实例（便于重新生成）
     */
    void deleteProjectStages(Long project);

}
