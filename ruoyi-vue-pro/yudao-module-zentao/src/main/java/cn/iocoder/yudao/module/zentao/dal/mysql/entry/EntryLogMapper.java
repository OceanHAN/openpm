package cn.iocoder.yudao.module.zentao.dal.mysql.entry;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntryLogPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.entry.EntryLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用接入调用日志 Mapper，对应禅道的通用日志表 zt_log。
 */
@Mapper
public interface EntryLogMapper extends BaseMapperX<EntryLogDO> {

    default PageResult<EntryLogDO> selectPage(EntryLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EntryLogDO>()
                .eqIfPresent(EntryLogDO::getObjectType, reqVO.getObjectType())
                .eqIfPresent(EntryLogDO::getObjectID, reqVO.getObjectID())
                .likeIfPresent(EntryLogDO::getUrl, reqVO.getUrl())
                .betweenIfPresent(EntryLogDO::getDate, reqVO.getDate())
                .orderByDesc(EntryLogDO::getId));
    }

}
