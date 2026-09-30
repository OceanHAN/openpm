package cn.iocoder.yudao.module.zentao.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zentao.controller.admin.product.vo.ProductPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.product.vo.ProductRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.product.vo.ProductSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.bug.BugDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.module.ModuleDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.story.StoryDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.bug.BugMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.module.ModuleMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.program.ProgramMapper;
import cn.iocoder.yudao.module.zentao.dal.mysql.story.StoryMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.bug.BugStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.story.StoryStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.execution.ExecutionTypeEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 产品 Service 实现
 *
 * 业务规则来源：禅道 {@code module/product/model.php}。
 */
@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    private static final String OBJECT_TYPE_PRODUCT = "product";

    /**
     * 产品状态：正常 / 结束。禅道 {@code $lang->product->statusList}
     */
    private static final String STATUS_NORMAL = "normal";
    private static final String STATUS_CLOSED = "closed";

    @Resource
    private ProductMapper productMapper;

    @Resource
    private StoryMapper storyMapper;

    @Resource
    private BugMapper bugMapper;

    @Resource
    private ProgramMapper programMapper;

    @Resource
    private ModuleMapper moduleMapper;

    @Resource
    private ActionService actionService;

    @Resource
    private AdminUserApi adminUserApi;

    // ==================== 写 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProduct(ProductSaveReqVO createReqVO) {
        // 禅道规则：产品名称唯一
        if (productMapper.selectByName(createReqVO.getName()) != null) {
            throw exception(PRODUCT_NAME_DUPLICATE, createReqVO.getName());
        }

        ProductDO product = BeanUtils.toBean(createReqVO, ProductDO.class);
        product.setStatus(STATUS_NORMAL);
        if (!StringUtils.hasText(product.getType())) {
            product.setType(STATUS_NORMAL);
        }
        if (!StringUtils.hasText(product.getAcl())) {
            product.setAcl("open");
        }
        if (product.getOrder() == null) {
            product.setOrder(0);
        }
        validateProgramAndLine(product.getProgram(), product.getLine());
        product.setVision("rnd");
        String operator = currentAccount();
        product.setCreatedBy(operator);
        product.setCreatedDate(LocalDateTime.now());
        productMapper.insert(product);

        actionService.recordAction(OBJECT_TYPE_PRODUCT, product.getId(), ActionTypeEnum.CREATED, null);
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(ProductSaveReqVO updateReqVO) {
        ProductDO oldProduct = validateProductExists(updateReqVO.getId());
        if (STATUS_CLOSED.equals(oldProduct.getStatus())) {
            throw exception(PRODUCT_CLOSED_CANNOT_UPDATE);
        }
        // 改名时要保证不与其他产品重名
        ProductDO sameName = productMapper.selectByName(updateReqVO.getName());
        if (sameName != null && !sameName.getId().equals(oldProduct.getId())) {
            throw exception(PRODUCT_NAME_DUPLICATE, updateReqVO.getName());
        }

        ProductDO updateObj = BeanUtils.toBean(updateReqVO, ProductDO.class);
        validateProgramAndLine(updateObj.getProgram(), updateObj.getLine());
        productMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_PRODUCT, oldProduct.getId(),
                ActionTypeEnum.EDITED, null, oldProduct, updateObj);
    }

    @Override
    public void closeProduct(Long id) {
        ProductDO product = validateProductExists(id);
        if (STATUS_CLOSED.equals(product.getStatus())) {
            throw exception(PRODUCT_ALREADY_CLOSED);
        }

        ProductDO updateObj = new ProductDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_CLOSED);
        updateObj.setClosedDate(LocalDateTime.now());
        productMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_PRODUCT, id,
                ActionTypeEnum.CLOSED, null, product, updateObj);
    }

    @Override
    public void activateProduct(Long id) {
        ProductDO product = validateProductExists(id);
        if (!STATUS_CLOSED.equals(product.getStatus())) {
            throw exception(PRODUCT_ALREADY_CLOSED);
        }

        ProductDO updateObj = new ProductDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_NORMAL);
        productMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_PRODUCT, id,
                ActionTypeEnum.ACTIVATED, null, product, updateObj);
    }

    @Override
    public void deleteProduct(Long id) {
        validateProductExists(id);
        // 禅道规则：产品下还有需求时不允许删除
        long storyCount = storyMapper.selectCount(
                new LambdaQueryWrapperX<StoryDO>().eq(StoryDO::getProduct, id));
        if (storyCount > 0) {
            throw exception(PRODUCT_HAS_STORIES, storyCount);
        }
        productMapper.deleteById(id);
        actionService.recordAction(OBJECT_TYPE_PRODUCT, id, ActionTypeEnum.DELETED, null);
    }

    @Override
    public void deleteProductList(List<Long> ids) {
        ids.forEach(this::deleteProduct);
    }

    // ==================== 读 ====================

    @Override
    public ProductDO getProduct(Long id) {
        return productMapper.selectById(id);
    }

    @Override
    public ProductDO validateProductExists(Long id) {
        if (id == null) {
            return null;
        }
        ProductDO product = productMapper.selectById(id);
        if (product == null) {
            throw exception(PRODUCT_NOT_EXISTS);
        }
        return product;
    }

    @Override
    public PageResult<ProductDO> getProductPage(ProductPageReqVO reqVO) {
        return productMapper.selectPage(reqVO);
    }

    @Override
    public List<ProductDO> getProductSimpleList() {
        return productMapper.selectSimpleList();
    }

    @Override
    public ProductRespVO.ProductStats getProductStats(Long productId) {
        ProductRespVO.ProductStats stats = new ProductRespVO.ProductStats();

        // 需求统计
        stats.setTotalStories(countStory(productId, null));
        stats.setActiveStories(countStory(productId, StoryStatusEnum.ACTIVE.getStatus()));
        stats.setClosedStories(countStory(productId, StoryStatusEnum.CLOSED.getStatus()));

        // 缺陷统计
        stats.setTotalBugs(countBug(productId, null));
        stats.setClosedBugs(countBug(productId, BugStatusEnum.CLOSED.getStatus()));
        // 「未解决」= 处于激活状态、还没被解决的缺陷，这是禅道缺陷列表的默认口径
        stats.setUnresolvedBugs(countBug(productId, BugStatusEnum.ACTIVE.getStatus()));
        return stats;
    }

    /**
     * 禅道里产品可以归属到项目集（{@code zt_product.program}），产品线则是
     * {@code zt_module} 里 {@code type='line'} 的节点。两者都必须是真实存在的对象，
     * 否则会出现「产品挂在一个不存在的项目集下」这种查不出来的脏数据。
     */
    private void validateProgramAndLine(Long program, Long line) {
        if (program != null && program > 0) {
            ProjectDO programDO = programMapper.selectById(program);
            if (programDO == null || !ExecutionTypeEnum.PROGRAM.getType().equals(programDO.getType())) {
                throw exception(PRODUCT_PROGRAM_NOT_EXISTS, program);
            }
        }
        if (line != null && line > 0) {
            ModuleDO lineModule = moduleMapper.selectById(line);
            if (lineModule == null || !"line".equals(lineModule.getType())) {
                throw exception(PRODUCT_LINE_NOT_EXISTS, line);
            }
        }
    }

    // ==================== 内部 ====================

    private Long countStory(Long productId, String status) {
        LambdaQueryWrapperX<StoryDO> wrapper = new LambdaQueryWrapperX<StoryDO>()
                .eq(StoryDO::getProduct, productId);
        if (status != null) {
            wrapper.eq(StoryDO::getStatus, status);
        }
        return storyMapper.selectCount(wrapper);
    }

    private Long countBug(Long productId, String status) {
        LambdaQueryWrapperX<BugDO> wrapper = new LambdaQueryWrapperX<BugDO>()
                .eq(BugDO::getProduct, productId);
        if (status != null) {
            wrapper.eq(BugDO::getStatus, status);
        }
        return bugMapper.selectCount(wrapper);
    }

    private String currentAccount() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            return "";
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : "";
    }

}
