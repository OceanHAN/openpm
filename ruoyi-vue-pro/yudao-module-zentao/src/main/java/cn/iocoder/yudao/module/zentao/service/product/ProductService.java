package cn.iocoder.yudao.module.zentao.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.product.vo.ProductPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.product.vo.ProductRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.product.vo.ProductSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;

import java.util.List;

/**
 * 产品 Service 接口
 *
 * 产品是禅道的根对象：需求、计划、发布都挂在产品下，缺陷也以产品为必填归属。
 * 禅道产品的状态只有两态：normal(正常) / closed(结束)，没有复杂状态机。
 */
public interface ProductService {

    /**
     * 创建产品
     */
    Long createProduct(ProductSaveReqVO createReqVO);

    /**
     * 修改产品。已关闭的产品允许改名/改描述，但需要用 activate 重新启用状态
     */
    void updateProduct(ProductSaveReqVO updateReqVO);

    /**
     * 关闭产品：normal → closed
     */
    void closeProduct(Long id);

    /**
     * 激活产品：closed → normal
     */
    void activateProduct(Long id);

    /**
     * 删除产品。产品下还有需求时不允许删除
     */
    void deleteProduct(Long id);

    /**
     * 批量删除
     */
    void deleteProductList(List<Long> ids);

    /**
     * 获得产品
     */
    ProductDO getProduct(Long id);

    /**
     * 校验产品存在
     */
    ProductDO validateProductExists(Long id);

    /**
     * 获得产品分页
     */
    PageResult<ProductDO> getProductPage(ProductPageReqVO reqVO);

    /**
     * 获得启用中的产品列表，供下拉选择
     */
    List<ProductDO> getProductSimpleList();

    /**
     * 实时统计某个产品下挂的业务对象数量。
     *
     * 禅道把这些数字冗余存在 zt_product 的 20 多个计数器列里，靠业务代码维护一致性；
     * 本实现改为实时统计，避免漏改导致的永久漂移。
     *
     * @param productId 产品编号
     * @return 统计结果
     */
    ProductRespVO.ProductStats getProductStats(Long productId);

}
