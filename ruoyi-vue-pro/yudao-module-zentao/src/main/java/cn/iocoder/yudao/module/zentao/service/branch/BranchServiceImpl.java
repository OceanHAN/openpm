package cn.iocoder.yudao.module.zentao.service.branch;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.branch.vo.BranchPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.branch.vo.BranchSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.branch.BranchDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.branch.BranchMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.branch.BranchStatusEnum;
import cn.iocoder.yudao.module.zentao.enums.product.ProductTypeEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import cn.iocoder.yudao.module.zentao.service.product.ProductService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 分支/平台 Service 实现
 *
 * <p>业务规则全部对齐禅道 {@code module/branch/model.php}，逐条对应关系写在每个方法上。
 */
@Slf4j
@Service
public class BranchServiceImpl implements BranchService {

    /**
     * 操作日志的对象类型
     */
    private static final String OBJECT_TYPE_BRANCH = "branch";

    @Resource
    private BranchMapper branchMapper;

    @Resource
    private ProductService productService;

    @Resource
    private ActionService actionService;

    // ==================== 写 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createBranch(BranchSaveReqVO createReqVO) {
        ProductDO product = validateProductSupportsBranch(createReqVO.getProduct());
        String branchName = ProductTypeEnum.branchNameOf(product.getType());
        // 禅道：同产品内名称唯一
        if (branchMapper.selectByName(product.getId(), createReqVO.getName(), null) != null) {
            throw exception(BRANCH_NAME_DUPLICATE, branchName, createReqVO.getName());
        }

        BranchDO branch = BeanUtils.toBean(createReqVO, BranchDO.class);
        branch.setId(null);
        branch.setStatus(BranchStatusEnum.ACTIVE.getStatus());
        // 禅道 create()：order = 当前最大值 + 1
        branch.setOrder(branchMapper.selectMaxOrder(product.getId()) + 1);
        branch.setDefaultFlag(0);
        branch.setCreatedDate(LocalDateTime.now());
        branch.setClosedDate(null);
        branchMapper.insert(branch);

        // 建的时候直接勾选「设为默认」也要支持
        if (Boolean.TRUE.equals(createReqVO.getSetDefault())) {
            setDefaultBranch(product.getId(), branch.getId());
        }

        actionService.recordAction(OBJECT_TYPE_BRANCH, branch.getId(), ActionTypeEnum.CREATED,
                "新建" + branchName + "：" + branch.getName());
        return branch.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBranch(BranchSaveReqVO updateReqVO) {
        BranchDO oldBranch = validateBranchExists(updateReqVO.getId());
        String branchName = branchNameOf(oldBranch.getProduct());
        // 禅道 update()：名称变了才查重，且要排除自己
        if (StringUtils.hasText(updateReqVO.getName())
                && !updateReqVO.getName().equals(oldBranch.getName())
                && branchMapper.selectByName(oldBranch.getProduct(), updateReqVO.getName(), oldBranch.getId()) != null) {
            throw exception(BRANCH_NAME_DUPLICATE, branchName, updateReqVO.getName());
        }

        BranchDO updateObj = new BranchDO();
        updateObj.setId(oldBranch.getId());
        updateObj.setName(updateReqVO.getName());
        updateObj.setDesc(updateReqVO.getDesc());
        branchMapper.updateById(updateObj);

        if (Boolean.TRUE.equals(updateReqVO.getSetDefault())) {
            setDefaultBranch(oldBranch.getProduct(), oldBranch.getId());
        }

        BranchDO newBranch = branchMapper.selectById(oldBranch.getId());
        actionService.recordActionWithChanges(OBJECT_TYPE_BRANCH, oldBranch.getId(),
                ActionTypeEnum.EDITED, null, oldBranch, newBranch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeBranch(Long id) {
        BranchDO branch = validateBranchExists(id);
        String branchName = branchNameOf(branch.getProduct());
        if (BranchStatusEnum.CLOSED.getStatus().equals(branch.getStatus())) {
            throw exception(BRANCH_ALREADY_CLOSED, branchName);
        }

        BranchDO updateObj = new BranchDO();
        updateObj.setId(id);
        updateObj.setStatus(BranchStatusEnum.CLOSED.getStatus());
        updateObj.setClosedDate(LocalDateTime.now());
        // 禅道 close() 顺带把 default 置 0：关闭的分支不能继续当默认
        updateObj.setDefaultFlag(0);
        branchMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_BRANCH, id,
                ActionTypeEnum.CLOSED, "关闭" + branchName, branch, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateBranch(Long id) {
        BranchDO branch = validateBranchExists(id);
        String branchName = branchNameOf(branch.getProduct());
        if (BranchStatusEnum.ACTIVE.getStatus().equals(branch.getStatus())) {
            throw exception(BRANCH_STATUS_ILLEGAL, branchName, "激活");
        }

        BranchDO updateObj = new BranchDO();
        updateObj.setId(id);
        updateObj.setStatus(BranchStatusEnum.ACTIVE.getStatus());
        updateObj.setClosedDate(null);
        branchMapper.updateById(updateObj);

        actionService.recordActionWithChanges(OBJECT_TYPE_BRANCH, id,
                ActionTypeEnum.ACTIVATED, "激活" + branchName, branch, updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultBranch(Long product, Long branchId) {
        validateProductSupportsBranch(product);
        // 禅道 setDefault()：先把该产品下所有分支的 default 清 0，再设置目标
        BranchDO clear = new BranchDO();
        clear.setDefaultFlag(0);
        branchMapper.update(clear, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BranchDO>()
                .eq(BranchDO::getProduct, product));

        // branchId = 0 表示默认分支是主干，此时不需要再设置任何行
        if (branchId == null || BranchDO.MAIN_BRANCH_ID.equals(branchId)) {
            return;
        }
        BranchDO branch = validateBranchExists(branchId);
        if (branch.getProduct() == null || !branch.getProduct().equals(product)) {
            throw exception(BRANCH_NOT_EXISTS, branchNameOf(product) + "（不属于该产品）");
        }
        BranchDO updateObj = new BranchDO();
        updateObj.setId(branchId);
        updateObj.setDefaultFlag(1);
        branchMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBranch(Long id) {
        BranchDO branch = validateBranchExists(id);
        String branchName = branchNameOf(branch.getProduct());
        // 禅道 checkBranchData()：分支下有数据就不能删
        String usedBy = detectUsedBy(id);
        if (StringUtils.hasText(usedBy)) {
            throw exception(BRANCH_HAS_DATA, branchName, usedBy);
        }
        branchMapper.deleteById(id);
        actionService.recordAction(OBJECT_TYPE_BRANCH, id, ActionTypeEnum.DELETED,
                "删除" + branchName + "：" + branch.getName());
    }

    @Override
    public void deleteBranchList(List<Long> ids) {
        ids.forEach(this::deleteBranch);
    }

    // ==================== 读 ====================

    @Override
    public BranchDO getBranch(Long id) {
        // id = 0 是虚拟主干，不入库，这里造一个临时对象返回
        if (id == null || BranchDO.MAIN_BRANCH_ID.equals(id)) {
            BranchDO main = new BranchDO();
            main.setId(BranchDO.MAIN_BRANCH_ID);
            main.setName(BranchDO.MAIN_BRANCH_NAME);
            main.setStatus(BranchStatusEnum.ACTIVE.getStatus());
            main.setDefaultFlag(0);
            main.setOrder(0);
            return main;
        }
        return validateBranchExists(id);
    }

    @Override
    public BranchDO validateBranchExists(Long id) {
        if (id == null) {
            throw exception(BRANCH_NOT_EXISTS, "分支");
        }
        if (BranchDO.MAIN_BRANCH_ID.equals(id)) {
            throw exception(BRANCH_MAIN_NOT_ALLOWED, "分支");
        }
        BranchDO branch = branchMapper.selectById(id);
        if (branch == null) {
            throw exception(BRANCH_NOT_EXISTS, "分支");
        }
        return branch;
    }

    @Override
    public PageResult<BranchDO> getBranchPage(BranchPageReqVO reqVO) {
        PageResult<BranchDO> pageResult = branchMapper.selectPage(reqVO);

        // 「主干」这一虚拟行要算进 total，但只能在第一页真正插进列表，
        // 否则第二页会重复出现一条主干，而且 total 会随页码变化。
        if (shouldCountMainBranch(reqVO)) {
            pageResult.setTotal(pageResult.getTotal() + 1);
            if (isFirstPage(reqVO)) {
                List<BranchDO> list = new ArrayList<>();
                list.add(buildMainBranch(reqVO.getProduct()));
                list.addAll(pageResult.getList());
                pageResult.setList(list);
            }
        }
        return pageResult;
    }

    @Override
    public List<BranchDO> getBranchListByProduct(Long product, String status) {
        List<BranchDO> list = new ArrayList<>(branchMapper.selectListByProduct(product, status));
        // 主干没有状态变更，只在「全部 / 激活」时出现
        if (!BranchStatusEnum.CLOSED.getStatus().equals(status)) {
            list.add(0, buildMainBranch(product));
        }
        return list;
    }

    @Override
    public Long countBranchByProduct(Long product) {
        return branchMapper.countByProduct(product);
    }

    @Override
    public boolean isBranchEnabled(Long product) {
        ProductDO productDO = productService.validateProductExists(product);
        return ProductTypeEnum.supportsBranch(productDO.getType());
    }

    @Override
    public String branchNameOf(Long product) {
        ProductDO productDO = productService.getProduct(product);
        return productDO == null
                ? "分支"
                : ProductTypeEnum.branchNameOf(productDO.getType());
    }

    // ==================== 内部 ====================

    /**
     * 产品必须存在，且类型为 branch / platform —— 对齐禅道 {@code showBranch()}
     */
    private ProductDO validateProductSupportsBranch(Long product) {
        if (product == null) {
            throw exception(BRANCH_PRODUCT_NOT_EXISTS, "(空)");
        }
        ProductDO productDO = productService.getProduct(product);
        if (productDO == null) {
            throw exception(BRANCH_PRODUCT_NOT_EXISTS, product);
        }
        if (!ProductTypeEnum.supportsBranch(productDO.getType())) {
            throw exception(BRANCH_PRODUCT_TYPE_UNSUPPORTED,
                    productDO.getName(),
                    ProductTypeEnum.NORMAL.getType().equals(productDO.getType()) ? "普通产品" : productDO.getType());
        }
        return productDO;
    }

    /**
     * 主干是否要计入本次分页结果。
     *
     * <p>前提：指定了某个产品（主干是产品内的概念）、状态过滤不是 closed、
     * 且名称关键词能匹配上「主干」。注意这里**与页码无关** —— total 在任何一页都要一致。
     */
    private boolean shouldCountMainBranch(BranchPageReqVO reqVO) {
        if (reqVO.getProduct() == null) {
            return false;
        }
        if (BranchStatusEnum.CLOSED.getStatus().equals(reqVO.getStatus())) {
            return false;
        }
        return !StringUtils.hasText(reqVO.getName()) || BranchDO.MAIN_BRANCH_NAME.contains(reqVO.getName());
    }

    private boolean isFirstPage(BranchPageReqVO reqVO) {
        return reqVO.getPageNo() == null || reqVO.getPageNo() == 1;
    }

    private BranchDO buildMainBranch(Long product) {
        BranchDO main = new BranchDO();
        main.setId(BranchDO.MAIN_BRANCH_ID);
        main.setProduct(product);
        main.setName(BranchDO.MAIN_BRANCH_NAME);
        main.setStatus(BranchStatusEnum.ACTIVE.getStatus());
        main.setDefaultFlag(0);
        main.setOrder(0);
        return main;
    }

    /**
     * 复刻禅道 {@code checkBranchData()}：分支下是否已经挂了数据
     *
     * <p>禅道检查 7 张表 + 项目关联表：
     * {@code module / story / productplan / bug / case / release / build} 和
     * {@code zt_projectproduct.branch}。本项目目前只有 {@code zt_story} / {@code zt_bug}
     * 两张表用到 branch，其余表建好后在这里追加检查即可。
     *
     * @return 命中数据的中文描述；没有数据返回 null
     */
    private String detectUsedBy(Long branchId) {
        if (branchMapper.countStoryByBranch(branchId) > 0) {
            return "需求";
        }
        if (branchMapper.countBugByBranch(branchId) > 0) {
            return "缺陷";
        }
        if (branchMapper.countModuleByBranch(branchId) > 0) {
            return "模块";
        }
        return null;
    }

}
