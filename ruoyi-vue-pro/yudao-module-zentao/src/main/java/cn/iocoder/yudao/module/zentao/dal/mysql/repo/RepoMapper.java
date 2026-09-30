package cn.iocoder.yudao.module.zentao.dal.mysql.repo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.repo.vo.RepoPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.repo.RepoDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RepoMapper extends BaseMapperX<RepoDO> {

    default PageResult<RepoDO> selectPage(RepoPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RepoDO>()
                .likeIfPresent(RepoDO::getName, reqVO.getName())
                .eqIfPresent(RepoDO::getStatus, reqVO.getStatus())
                .eqIfPresent(RepoDO::getScmType, reqVO.getScmType())
                .likeIfPresent(RepoDO::getProduct, reqVO.getProduct())
                .orderByDesc(RepoDO::getId));
    }

    default RepoDO selectByName(String name) {
        return selectOne(new LambdaQueryWrapperX<RepoDO>()
                .eq(RepoDO::getName, name)
                .last("LIMIT 1"));
    }

    default List<RepoDO> selectSimpleList() {
        return selectList(new LambdaQueryWrapperX<RepoDO>()
                .eq(RepoDO::getStatus, "active")
                .orderByAsc(RepoDO::getId));
    }

    /** 某产品关联的代码库（product 是逗号列表，只能 FIND_IN_SET） */
    default List<RepoDO> selectListByProduct(Long product) {
        return selectList(new LambdaQueryWrapperX<RepoDO>()
                .apply("FIND_IN_SET({0}, product)", product)
                .orderByAsc(RepoDO::getId));
    }

}
