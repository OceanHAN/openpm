package cn.iocoder.yudao.module.zentao.service.program;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.program.vo.ProgramPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.program.vo.ProgramRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.program.vo.ProgramSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;

import java.util.List;
import java.util.Map;

/**
 * 项目集 Service
 *
 * 对应禅道 {@code module/program/}。项目集没有自己的表：它就是
 * {@code zt_project} 里 {@code type='program'} 的行，项目靠 {@code parent} 指向它。
 */
public interface ProgramService {

    Long createProgram(ProgramSaveReqVO createReqVO);

    void updateProgram(ProgramSaveReqVO updateReqVO);

    void deleteProgram(Long id);

    void deleteProgramList(List<Long> ids);

    // ==================== 状态流转（禅道 program 的 start/suspend/activate/close） ====================

    void startProgram(Long id);

    void suspendProgram(Long id);

    void activateProgram(Long id);

    void closeProgram(Long id, String reason);

    // ==================== 读 ====================

    ProjectDO getProgram(Long id);

    /**
     * 校验存在且确实是项目集。项目接口拿项目集 id 来查、项目集接口拿项目/执行 id 来查，
     * 都应该在这里被拦住
     */
    ProjectDO validateProgramExists(Long id);

    PageResult<ProjectDO> getProgramPage(ProgramPageReqVO reqVO);

    List<ProjectDO> getProgramList();

    List<ProjectDO> getProgramSimpleList();

    List<ProjectDO> getProgramListByParent(Long parent);

    /**
     * 项目集统计：下级项目集数 / 项目数 / 产品数
     */
    ProgramRespVO fillStats(ProjectDO program);

    /** 批量回填统计（列表用） */
    List<ProgramRespVO> fillStatsList(List<ProjectDO> programs);

    /**
     * 项目集下的项目（禅道项目集详情页的「项目」清单）
     */
    List<ProjectDO> getProjectList(Long programId);

    /**
     * 项目集下的产品（zt_product.program）
     */
    List<ProductDO> getProductList(Long programId);

    /**
     * 批量取项目集名称，用于给项目/产品列表回填「所属项目集」
     */
    Map<Long, String> getProgramNameMap(List<Long> ids);

}
