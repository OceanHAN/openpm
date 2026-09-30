package cn.iocoder.yudao.module.zentao.service.module;

import cn.iocoder.yudao.module.zentao.controller.admin.module.vo.ModuleOrderReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.module.vo.ModuleRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.module.vo.ModuleSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.module.ModuleDO;

import java.util.List;

/**
 * 模块树 Service 接口
 *
 * <h3>禅道语义（module/tree/model.php）</h3>
 * <ul>
 *   <li>{@code zt_module} 是一张通用树表，{@code (root, type, branch)} 定位一棵树</li>
 *   <li>path 是<b>逗号分隔且以逗号开头</b>：{@code ,5,6,}；grade 一级为 1</li>
 *   <li>新建模块 order = 同级 max + 10；同级不允许重名</li>
 *   <li>删除模块会连带删除子孙，并把挂在这些模块上的需求/任务/缺陷<b>改挂到父模块</b></li>
 *   <li>删除或移动后调用 {@code fixModulePath()} 重算整棵树的 path/grade</li>
 * </ul>
 */
public interface ModuleService {

    /**
     * 新建模块
     */
    Long createModule(ModuleSaveReqVO createReqVO);

    /**
     * 修改模块。改动 parent 时会递归重算子孙的 path/grade
     */
    void updateModule(ModuleSaveReqVO updateReqVO);

    /**
     * 批量更新排序
     */
    void updateOrder(ModuleOrderReqVO reqVO);

    /**
     * 删除模块（连带子孙），并把关联业务对象改挂到父模块
     */
    void deleteModule(Long id);

    /**
     * 获得模块
     */
    ModuleDO getModule(Long id);

    /**
     * 校验模块存在
     */
    ModuleDO validateModuleExists(Long id);

    /**
     * 取一棵树的全部模块（平铺，按 parent/order 排序）
     */
    List<ModuleDO> getModuleList(Long root, String type, Long branch);

    /**
     * 取一棵树（嵌套结构），供前端树形控件直接使用
     */
    List<ModuleRespVO> getModuleTree(Long root, String type, Long branch);

    /**
     * 按 parent 重算整棵树的 path 与 grade —— 对应禅道 {@code fixModulePath()}
     */
    void fixModulePath(Long root, String type);

    /**
     * 取某个模块自己 + 全部子孙的 id。
     *
     * <p>用途：禅道里按模块筛选需求/任务/缺陷时，选一个父模块会**连带查出所有子模块**的数据
     * （它把选中的模块展开成整棵子树的 id 列表再 IN 查询）。列表页的 module 过滤走的就是这里。
     *
     * @param moduleId 模块编号
     * @return 自己 + 子孙的 id 列表；moduleId 为空时返回空列表
     */
    List<Long> getSelfAndDescendantIds(Long moduleId);

}
