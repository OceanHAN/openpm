package cn.iocoder.yudao.module.zentao.dal.mysql.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.product.vo.ProductPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 产品 Mapper
 */
@Mapper
public interface ProductMapper extends BaseMapperX<ProductDO> {

    /**
     * 取启用中的产品列表，供下拉选择使用
     */
    default List<ProductDO> selectSimpleList() {
        return selectList(new LambdaQueryWrapperX<ProductDO>()
                .eq(ProductDO::getStatus, "normal")
                .orderByAsc(ProductDO::getOrder)
                .orderByAsc(ProductDO::getId));
    }

    /**
     * 分页查询
     */
    default PageResult<ProductDO> selectPage(ProductPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductDO>()
                .likeIfPresent(ProductDO::getName, reqVO.getName())
                .likeIfPresent(ProductDO::getCode, reqVO.getCode())
                .eqIfPresent(ProductDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProductDO::getType, reqVO.getType())
                .eqIfPresent(ProductDO::getProgram, reqVO.getProgram())
                .eqIfPresent(ProductDO::getPO, reqVO.getPO())
                .orderByAsc(ProductDO::getOrder)
                .orderByDesc(ProductDO::getId));
    }

    /**
     * 某个项目集下的产品（禅道项目集详情页的「产品」清单）
     */
    default List<ProductDO> selectListByProgram(Long program) {
        return selectList(new LambdaQueryWrapperX<ProductDO>()
                .eq(ProductDO::getProgram, program)
                .orderByAsc(ProductDO::getOrder)
                .orderByAsc(ProductDO::getId));
    }

    /**
     * 按名称查重
     */
    default ProductDO selectByName(String name) {
        return selectOne(ProductDO::getName, name);
    }

}
