package cn.iocoder.yudao.module.zentao.service.doc;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocLibRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocLibSaveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocContentRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocMoveReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocRespVO;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocSaveReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.doc.DocDO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.doc.DocLibDO;

import java.util.List;

/**
 * 文档 Service 接口
 *
 * <h3>禅道语义（module/doc）</h3>
 * <ul>
 *   <li>三层结构：文档库 {@code zt_doclib} → 文档 {@code zt_doc} → 版本内容 {@code zt_doccontent}</li>
 *   <li>{@code type=chapter} 是章节（树节点），不是文档</li>
 *   <li>版本链：编辑内容追加 {@code version+1}；{@code version=0} 是草稿位</li>
 *   <li>浏览计数 {@code views} 自增</li>
 * </ul>
 */
public interface DocService {

    // ==================== 文档库 ====================

    Long createLib(DocLibSaveReqVO reqVO);

    void updateLib(DocLibSaveReqVO reqVO);

    void deleteLib(Long id);

    DocLibDO getLib(Long id);

    DocLibDO validateLibExists(Long id);

    /**
     * 某个对象（产品/项目/执行）下的文档库
     */
    List<DocLibDO> getLibList(String type, Long objectID);

    /**
     * 自定义空间（parent）下的库；parent=0 时返回全部空间
     */
    List<DocLibDO> getLibListByParent(Long parent);

    /**
     * 文档库列表（带库内文档数），供页面左侧列表用
     */
    List<DocLibRespVO> getLibRespList(String type, Long objectID, Long parent);

    // ==================== 文档 ====================

    Long createDoc(DocSaveReqVO reqVO);

    void updateDoc(DocSaveReqVO reqVO);

    void deleteDoc(Long id);

    /**
     * 读文档详情。
     *
     * @param version 版本号；0 或 null 表示当前版本
     */
    DocRespVO getDoc(Long id, Integer version);

    DocDO validateDocExists(Long id);

    PageResult<DocDO> getDocPage(DocPageReqVO reqVO);

    /** 「我的文档」：创建人 / 指派给 / 最后修改人命中我（只查文档，不含章节） */
    PageResult<DocDO> getMyDocPage(DocPageReqVO reqVO);

    /**
     * 某库的章节树（只含 type=chapter 的行）
     */
    List<DocRespVO> getChapterTree(Long lib);

    /**
     * 把 DO 列表批量转成 VO（补齐 libName / parentTitle 这类展示字段）。
     *
     * <p>单独开一个方法是因为「列表里要显示库名和所属章节名」这件事只有服务层做得到
     * （手里才有 mapper）。逐行查库名/章节名就是 N+1，所以在这里批量取。
     */
    List<DocRespVO> getDocRespList(List<DocDO> list);

    /**
     * 版本历史（最新在前，草稿 version=0 在最后）
     */
    List<DocContentRespVO> getContentList(Long id);

    /**
     * 浏览：views + 1，并返回详情
     */
    DocRespVO viewDoc(Long id);

    /**
     * 移动文档/章节（可跨库）
     */
    void moveDoc(DocMoveReqVO reqVO);

    /**
     * 把草稿发布成正式版本（version 0 → 1）
     */
    void publishDoc(Long id);

}
