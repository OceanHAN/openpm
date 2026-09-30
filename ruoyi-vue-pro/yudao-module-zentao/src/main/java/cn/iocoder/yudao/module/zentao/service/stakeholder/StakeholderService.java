package cn.iocoder.yudao.module.zentao.service.stakeholder;

import cn.iocoder.yudao.module.zentao.controller.admin.stakeholder.vo.StakeholderRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.stakeholder.vo.StakeholderSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.stakeholder.StakeholderDO;

import java.util.List;
import java.util.Map;

/**
 * 干系人 Service
 *
 * 对应禅道 {@code module/stakeholder/}。
 */
public interface StakeholderService {

    Long createStakeholder(StakeholderSaveReqVO createReqVO);

    /** 批量添加（禅道 batchCreate：一次给一批账号） */
    List<Long> batchCreate(String objectType, Long objectID, String from, List<String> users);

    void updateStakeholder(StakeholderSaveReqVO updateReqVO);

    /** 按记录编号删除 */
    void deleteStakeholder(Long id);

    /**
     * 按「对象 + 账号」删除（禅道 delete(userID) 就是这个口径）
     */
    void deleteByObjectAndUser(String objectType, Long objectID, String user);

    StakeholderDO getStakeholder(Long id);

    /**
     * 单个干系人（带姓名、类型/来源名称）—— 列表和详情要的口径一致，
     * 不然详情里 typeName/fromName 会是 null（踩过）
     */
    StakeholderRespVO getStakeholderVO(Long id);

    /**
     * 某个对象的干系人列表（带姓名、类型/来源名称），关键干系人排前面
     */
    List<StakeholderRespVO> getStakeholderList(String objectType, Long objectID);

    /**
     * 批量取多个对象的干系人（列表页回填「干系人数」用）
     */
    Map<Long, List<StakeholderDO>> getStakeholderMap(String objectType, List<Long> objectIds);

    /**
     * 我作为干系人参与的对象编号
     */
    List<Long> getObjectIdsByUser(String objectType, String user);

}
