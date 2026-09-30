package cn.iocoder.yudao.module.zentao.service.module;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zentao.controller.admin.module.vo.ModuleOrderReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.module.vo.ModuleRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.module.vo.ModuleSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.module.ModuleDO;
import cn.iocoder.yudao.module.zentao.dal.mysql.module.ModuleMapper;
import cn.iocoder.yudao.module.zentao.enums.action.ActionTypeEnum;
import cn.iocoder.yudao.module.zentao.enums.module.ModuleTypeEnum;
import cn.iocoder.yudao.module.zentao.service.action.ActionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 模块树 Service 实现
 *
 * <p>逐条对齐禅道 {@code module/tree/model.php}：createModule / update / updateOrder /
 * remove / fixModulePath。与禅道的差异都写在方法注释里。
 */
@Slf4j
@Service
public class ModuleServiceImpl implements ModuleService {

    private static final String OBJECT_TYPE_MODULE = "module";

    /**
     * 禅道新建模块时排序的步长（不是 1，是 10，便于中间插入）
     */
    private static final int ORDER_STEP = 10;

    @Resource
    private ModuleMapper moduleMapper;

    @Resource
    private ActionService actionService;

    // ==================== 写 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createModule(ModuleSaveReqVO createReqVO) {
        validateType(createReqVO.getType());
        if (createReqVO.getRoot() == null || createReqVO.getRoot() <= 0) {
            // 只有产品线（line）允许 root = 0
            if (!ModuleTypeEnum.LINE.getType().equals(createReqVO.getType())) {
                throw exception(MODULE_ROOT_REQUIRED);
            }
        }
        String name = trimName(createReqVO.getName());
        Long branch = createReqVO.getBranch() == null ? 0L : createReqVO.getBranch();
        Long parentId = createReqVO.getParent() == null ? 0L : createReqVO.getParent();

        ModuleDO parent = null;
        if (parentId > 0) {
            parent = moduleMapper.selectById(parentId);
            validateParent(parent, createReqVO.getRoot(), createReqVO.getType(), branch);
        }

        // 同级重名（禅道 checkUnique）
        if (moduleMapper.selectBySiblingName(createReqVO.getRoot(), createReqVO.getType(), branch, parentId, name, null) != null) {
            throw exception(MODULE_NAME_DUPLICATE, name);
        }

        ModuleDO module = BeanUtils.toBean(createReqVO, ModuleDO.class);
        module.setId(null);
        module.setName(name);
        module.setBranch(branch);
        module.setParent(parentId);
        // 禅道：order 不传时取同级 max + 10
        Integer order = createReqVO.getOrder();
        if (order == null) {
            order = moduleMapper.selectMaxOrder(createReqVO.getRoot(), createReqVO.getType(), branch, parentId) + ORDER_STEP;
        }
        module.setOrder(order);
        module.setFrom(0);
        moduleMapper.insert(module);

        // 插入后再算 path/grade：path 里要用到自己的 id
        fixModulePath(createReqVO.getRoot(), createReqVO.getType());

        actionService.recordAction(OBJECT_TYPE_MODULE, module.getId(), ActionTypeEnum.CREATED,
                "创建" + ModuleTypeEnum.nameOf(createReqVO.getType()) + "：" + name);
        return module.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModule(ModuleSaveReqVO updateReqVO) {
        ModuleDO oldModule = validateModuleExists(updateReqVO.getId());
        validateType(updateReqVO.getType());
        String name = trimName(updateReqVO.getName());

        Long branch = updateReqVO.getBranch() == null ? oldModule.getBranch() : updateReqVO.getBranch();
        Long newParentId = updateReqVO.getParent() == null ? oldModule.getParent() : updateReqVO.getParent();
        Long root = updateReqVO.getRoot() == null ? oldModule.getRoot() : updateReqVO.getRoot();

        if (!root.equals(oldModule.getRoot()) || !updateReqVO.getType().equals(oldModule.getType())) {
            // 换树（root/type）会让原 path 全部失效，而且业务对象的归属会错乱，
            // 禅道会弹「该操作比较危险」的确认。这里直接拒绝，避免出现跨树的脏数据。
            throw exception(MODULE_PARENT_INVALID, "不允许修改模块所属根对象或树类型");
        }

        if (!newParentId.equals(oldModule.getParent())) {
            // 移动：新父级必须合法，且不能移到自己或自己的子孙下（否则树成环）
            if (newParentId > 0) {
                ModuleDO newParent = moduleMapper.selectById(newParentId);
                validateParent(newParent, root, updateReqVO.getType(), branch);
            }
            if (newParentId.equals(oldModule.getId())) {
                throw exception(MODULE_MOVE_TO_DESCENDANT);
            }
            // 同理：oldModule.getPath() 已经含自己的 id，新父级 path 以它开头即为自己的子孙
            String selfPrefix = oldModule.getPath();
            if (newParentId > 0) {
                ModuleDO newParent = moduleMapper.selectById(newParentId);
                if (newParent.getPath() != null && newParent.getPath().startsWith(selfPrefix)) {
                    throw exception(MODULE_MOVE_TO_DESCENDANT);
                }
            }
        }

        if (moduleMapper.selectBySiblingName(root, updateReqVO.getType(), branch, newParentId, name, oldModule.getId()) != null) {
            throw exception(MODULE_NAME_DUPLICATE, name);
        }

        ModuleDO updateObj = new ModuleDO();
        updateObj.setId(oldModule.getId());
        updateObj.setName(name);
        updateObj.setParent(newParentId);
        updateObj.setBranch(branch);
        updateObj.setShortName(updateReqVO.getShortName());
        updateObj.setOwner(updateReqVO.getOwner());
        updateObj.setOrder(updateReqVO.getOrder());
        moduleMapper.updateById(updateObj);

        // 移动过就要重算整棵树的 path/grade
        if (!newParentId.equals(oldModule.getParent())) {
            fixModulePath(root, updateReqVO.getType());
        }

        ModuleDO newModule = moduleMapper.selectById(oldModule.getId());
        actionService.recordActionWithChanges(OBJECT_TYPE_MODULE, oldModule.getId(),
                ActionTypeEnum.EDITED, null, oldModule, newModule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(ModuleOrderReqVO reqVO) {
        for (ModuleOrderReqVO.Item item : reqVO.getItems()) {
            ModuleDO module = validateModuleExists(item.getId());
            ModuleDO updateObj = new ModuleDO();
            updateObj.setId(module.getId());
            updateObj.setOrder(item.getOrder());
            moduleMapper.updateById(updateObj);
        }
        actionService.recordAction(OBJECT_TYPE_MODULE, reqVO.getItems().get(0).getId(),
                ActionTypeEnum.EDITED, "调整模块排序（共 " + reqVO.getItems().size() + " 项）");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModule(Long id) {
        ModuleDO module = validateModuleExists(id);
        // 注意：模块自己的 path 里**已经包含自己的 id**（root 级模块 path 形如 ",9,"），
        // 所以子孙的前缀就是 module.getPath() 本身，不能再拼一次 id，
        // 否则 ",9," + "9," = ",9,9," 永远匹配不到任何子孙。
        String pathPrefix = module.getPath();
        // 子孙 + 自己
        List<ModuleDO> selfAndDescendants = moduleMapper.selectSelfAndDescendants(id, pathPrefix);
        List<Long> ids = selfAndDescendants.stream().map(ModuleDO::getId).toList();

        // 关键：先把业务对象改挂到父模块，再删树节点（复刻禅道 remove()）
        // 顺序不能反 —— 反了就会有一批对象挂在已删除的模块上，列表页按模块筛选时全部消失。
        int moved = 0;
        moved += moduleMapper.moveStoriesToParent(id, pathPrefix, module.getParent());
        moved += moduleMapper.moveTasksToParent(id, pathPrefix, module.getParent());
        moved += moduleMapper.moveBugsToParent(id, pathPrefix, module.getParent());

        for (Long moduleId : ids) {
            moduleMapper.deleteById(moduleId);
        }
        fixModulePath(module.getRoot(), module.getType());

        actionService.recordAction(OBJECT_TYPE_MODULE, id, ActionTypeEnum.DELETED,
                "删除" + ModuleTypeEnum.nameOf(module.getType()) + "：" + module.getName()
                        + "（含 " + (ids.size() - 1) + " 个子模块，"
                        + (moved > 0 ? moved + " 条数据已改挂到上级模块" : "无关联数据") + "）");
    }

    // ==================== 读 ====================

    @Override
    public ModuleDO getModule(Long id) {
        return validateModuleExists(id);
    }

    @Override
    public ModuleDO validateModuleExists(Long id) {
        ModuleDO module = id == null ? null : moduleMapper.selectById(id);
        if (module == null) {
            throw exception(MODULE_NOT_EXISTS, id);
        }
        return module;
    }

    @Override
    public List<ModuleDO> getModuleList(Long root, String type, Long branch) {
        return moduleMapper.selectList(root, type, branch);
    }

    @Override
    public List<ModuleRespVO> getModuleTree(Long root, String type, Long branch) {
        List<ModuleDO> list = getModuleList(root, type, branch);
        return buildTree(list, 0L);
    }

    @Override
    public List<Long> getSelfAndDescendantIds(Long moduleId) {
        if (moduleId == null || moduleId <= 0) {
            return List.of();
        }
        ModuleDO module = moduleMapper.selectById(moduleId);
        if (module == null) {
            return List.of();
        }
        return moduleMapper.selectSelfAndDescendants(moduleId, module.getPath())
                .stream().map(ModuleDO::getId).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fixModulePath(Long root, String type) {
        List<ModuleDO> all = moduleMapper.selectAllForFix(root, type);
        if (all.isEmpty()) {
            return;
        }
        // parentId -> children（保持 order 顺序）
        Map<Long, List<ModuleDO>> childrenMap = new LinkedHashMap<>();
        for (ModuleDO module : all) {
            childrenMap.computeIfAbsent(module.getParent(), k -> new ArrayList<>()).add(module);
        }
        childrenMap.values().forEach(children -> children.sort((a, b) -> Integer.compare(
                a.getOrder() == null ? 0 : a.getOrder(),
                b.getOrder() == null ? 0 : b.getOrder())));

        // 从一级模块（parent = 0）开始逐层下推，与禅道 fixModulePath 的迭代思路一致
        List<ModuleDO> roots = childrenMap.getOrDefault(0L, List.of());
        for (ModuleDO module : roots) {
            applyPath(module, ModuleDO.ROOT_PATH, ModuleDO.ROOT_GRADE, childrenMap);
        }
    }

    /**
     * 递归写入 path/grade。只有值真的变了才执行 UPDATE，避免整棵树无谓写库
     */
    private void applyPath(ModuleDO module, String parentPath, int parentGrade, Map<Long, List<ModuleDO>> childrenMap) {
        String path = parentPath + module.getId() + ",";
        int grade = parentGrade + 1;
        if (!path.equals(module.getPath()) || module.getGrade() == null || module.getGrade() != grade) {
            ModuleDO updateObj = new ModuleDO();
            updateObj.setId(module.getId());
            updateObj.setPath(path);
            updateObj.setGrade(grade);
            moduleMapper.updateById(updateObj);
            module.setPath(path);
            module.setGrade(grade);
        }
        for (ModuleDO child : childrenMap.getOrDefault(module.getId(), List.of())) {
            applyPath(child, path, grade, childrenMap);
        }
    }

    // ==================== 内部 ====================

    private void validateType(String type) {
        if (!ModuleTypeEnum.isValid(type)) {
            throw exception(MODULE_TYPE_INVALID, type);
        }
    }

    private String trimName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (!StringUtils.hasText(trimmed)) {
            throw exception(MODULE_NAME_BLANK);
        }
        return trimmed;
    }

    /**
     * 上级模块必须：存在、同一棵树（root + type）、同一分支。
     *
     * <p>禅道只校验 root 与 type；分支它是在 {@code fixModulePath()} 里
     * 用父模块的 branch 覆盖子模块。这里改成显式拒绝跨分支挂载 —— 语义更清楚，
     * 也避免「需求模块挂到了另一个平台的树下」这种脏数据。
     */
    private void validateParent(ModuleDO parent, Long root, String type, Long branch) {
        if (parent == null) {
            throw exception(MODULE_PARENT_INVALID, "上级模块不存在");
        }
        if (!parent.getRoot().equals(root) || !parent.getType().equals(type)) {
            throw exception(MODULE_PARENT_INVALID, "上级模块与当前模块不在同一棵树");
        }
        if (ModuleTypeEnum.isBranchAware(type) && parent.getBranch() != null && !parent.getBranch().equals(branch)) {
            throw exception(MODULE_PARENT_INVALID, "上级模块与当前模块不在同一分支");
        }
    }

    /**
     * 把平铺列表拼成树。只看 parent 关系，不依赖 path ——
     * 万一历史数据的 path 有脏值，树形展示也不会崩。
     */
    private List<ModuleRespVO> buildTree(List<ModuleDO> list, Long parentId) {
        List<ModuleRespVO> result = new ArrayList<>();
        for (ModuleDO module : list) {
            if (!parentId.equals(module.getParent())) {
                continue;
            }
            ModuleRespVO vo = BeanUtils.toBean(module, ModuleRespVO.class);
            vo.setTypeName(ModuleTypeEnum.nameOf(module.getType()));
            List<ModuleRespVO> children = buildTree(list, module.getId());
            vo.setChildren(children);
            vo.setChildCount(children.size());
            result.add(vo);
        }
        return result;
    }

}
