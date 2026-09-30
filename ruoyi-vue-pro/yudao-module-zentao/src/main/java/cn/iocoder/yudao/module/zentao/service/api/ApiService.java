package cn.iocoder.yudao.module.zentao.service.api;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiDetailRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiLibRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiReleaseRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiReleaseSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiStructPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiStructRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.api.vo.ApiStructSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.api.ApiDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.api.ApiLibReleaseDO;

import java.util.List;

/**
 * 接口文档库（禅道 {@code module/api}）。
 *
 * <h3>这个模块的边界：库不在本模块的表里</h3>
 * 接口库 = {@code zt_doclib} 里 {@code type='api'} 的记录（禅道从来没有 {@code zt_apilib}），
 * 目录树 = {@code zt_module} 里 {@code type='api'}、{@code root=库编号} 的节点。
 * 本模块只负责 {@code zt_api}/{@code zt_apispec}/{@code zt_apistruct}/
 * {@code zt_apistruct_spec}/{@code zt_api_lib_release} 五张表，
 * 库的增删改沿用 doc 模块的 {@code zt_doclib}（本 Service 只读它，见 {@link #getLibList()}）。
 *
 * <h3>五条照抄的禅道规则</h3>
 * <ol>
 *   <li><b>两条版本链</b>：接口 {@code zt_api + zt_apispec(doc,version)}、
 *       结构 {@code zt_apistruct + zt_apistruct_spec(name,version)}。
 *       接口只在「有变更」时 version+1，结构则无条件 +1（禅道两处实现不同，见方法注释）。</li>
 *   <li><b>发布是快照</b>：{@link #publishLib} 把 modules/apis/structs 打成 {@code snap} JSON 冻结，
 *       读发布时用 snap 里的 version 回查 spec，不是引用当前值。</li>
 *   <li><b>唯一性不过滤已删除</b>：title 在 (lib,module)、path 在 (lib,module,method)，
 *       查询故意不带 {@code deleted}。</li>
 *   <li><b>乐观锁</b>：接口编辑带的 {@code editedDate} 与库里不一致就拒绝（别人已经改过）。</li>
 *   <li><b>删除保护</b>：接口被发布版本冻结、或被数据结构引用时拒绝删除
 *       （禅道本身没有这道检查，是本实现有意加的加固，见 README 的说明）。</li>
 * </ol>
 */
public interface ApiService {

    // ==================== 接口库（读 zt_doclib，不拥有它） ====================

    /**
     * 接口库列表（{@code zt_doclib} 里 {@code type='api'} 的记录，附接口/结构计数）。
     */
    List<ApiLibRespVO> getLibList();

    // ==================== 接口 ====================

    /**
     * 接口分页。{@code module} 会展开成「自己 + 全部子孙」；{@code releaseID} 走冻结快照回溯。
     */
    PageResult<ApiRespVO> getApiPage(ApiPageReqVO reqVO);

    /**
     * 接口详情，对齐禅道 {@code api::getByID($id, $version, $releaseID)}：
     * 不传 version/release 给当前值；传 version 给历史版本；传 releaseID 先从快照里取版本号。
     */
    ApiDetailRespVO getApiDetail(Long id, Integer version, Long releaseID);

    /** 新建接口：写 {@code zt_api} + {@code zt_apispec} 的 v1 */
    Long createApi(ApiSaveReqVO reqVO);

    /** 修改接口：真有变更才 version+1，并原地重写当前版本的 spec */
    void updateApi(ApiSaveReqVO reqVO);

    /** 删除接口（逻辑删除）；被发布版本冻结或被结构引用时拒绝 */
    void deleteApi(Long id);

    // ==================== 数据结构 ====================

    /**
     * 结构分页。{@code releaseID} 非空时按发布快照里的 {@code (id, version)} 回溯冻结版本。
     */
    PageResult<ApiStructRespVO> getStructPage(ApiStructPageReqVO reqVO);

    /** 结构详情；{@code version} 非空时取该结构名的历史版本（禅道按 name 关联版本） */
    ApiStructRespVO getStructDetail(Long id, Integer version);

    /** 新建结构：写 {@code zt_apistruct} + {@code zt_apistruct_spec} 的 v1 */
    Long createStruct(ApiStructSaveReqVO reqVO);

    // ==================== 发布版本 ====================

    /** 发布版本列表（禅道 {@code api::releases($libID)}） */
    List<ApiReleaseRespVO> getReleaseList(Long libID);

    /** 发布：把当前库的 modules/apis/structs 打成 snap JSON 冻结（禅道 {@code publishLib()}） */
    Long publishLib(ApiReleaseSaveReqVO reqVO);

    /** 删除发布版本（物理删除，禅道 {@code deleteRelease()}）；删掉之后被它冻结的接口才能删 */
    void deleteRelease(Long id);

    // ==================== 给其它模块/测试用的校验入口 ====================

    /** 校验接口存在，返回当前行 */
    ApiDO validateApiExists(Long id);

    /** 校验发布版本存在，返回该行 */
    ApiLibReleaseDO validateReleaseExists(Long id);

}
