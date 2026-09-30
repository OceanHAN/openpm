package cn.iocoder.yudao.module.zentao.service.testcase;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CasePageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseReviewReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseSpecRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CaseStepVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testcase.CaseDO;

import java.util.List;

/**
 * 测试用例 Service 接口
 *
 * <h3>禅道语义（module/testcase）</h3>
 * <ul>
 *   <li>头部 {@code zt_case} + 版本快照 {@code zt_casespec} + 步骤 {@code zt_casestep}</li>
 *   <li><b>只有步骤变化才 version+1</b>，并把状态打回 {@code wait}（待评审）</li>
 *   <li>{@code storyVersion} 冻结关联时的需求版本，需求升版后进入「待确认」</li>
 * </ul>
 */
public interface CaseService {

    Long createCase(CaseSaveReqVO reqVO);

    void updateCase(CaseSaveReqVO reqVO);

    void deleteCase(Long id);

    CaseDO validateCaseExists(Long id);

    /**
     * 读用例详情。
     *
     * @param version 版本号；0 或 null 表示当前版本
     */
    CaseRespVO getCase(Long id, Integer version);

    /**
     * 某版本的步骤（带层级编号）
     */
    List<CaseStepVO> getStepList(Long id, Integer version);

    /**
     * 版本历史（最新在前）
     */
    List<CaseSpecRespVO> getSpecList(Long id);

    PageResult<CaseRespVO> getCasePage(CasePageReqVO reqVO);

    /**
     * 某需求关联的用例
     */
    List<CaseRespVO> getCaseListByStory(Long story);

    /**
     * 评审：只有 status=wait 才能评审
     */
    void reviewCase(CaseReviewReqVO reqVO);

    /**
     * 确认需求变更：把 storyVersion 追平到需求当前版本
     */
    void confirmStoryChange(Long id);

}
