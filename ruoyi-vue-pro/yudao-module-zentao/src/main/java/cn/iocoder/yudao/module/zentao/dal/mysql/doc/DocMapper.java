package cn.iocoder.yudao.module.zentao.dal.mysql.doc;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.doc.vo.DocPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.doc.DocDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * 文档 Mapper
 */
@Mapper
public interface DocMapper extends BaseMapperX<DocDO> {

    /**
     * 分页查询。
     *
     * <p>{@code excludeChapter} 是给「文档列表」用的：章节（type=chapter）不是文档，
     * 混进列表会让用户看到一堆点不开的空行。
     * 反过来「章节树」接口只需要 chapter，用 {@link #selectChapterList}。
     */
    default PageResult<DocDO> selectPage(DocPageReqVO reqVO, Collection<Long> libIds) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DocDO>()
                .inIfPresent(DocDO::getLib, libIds)
                .eqIfPresent(DocDO::getLib, reqVO.getLib())
                .eqIfPresent(DocDO::getProduct, reqVO.getProduct())
                .eqIfPresent(DocDO::getProject, reqVO.getProject())
                .eqIfPresent(DocDO::getExecution, reqVO.getExecution())
                .eqIfPresent(DocDO::getModule, reqVO.getModule())
                .eqIfPresent(DocDO::getParent, reqVO.getParent())
                .eqIfPresent(DocDO::getStatus, reqVO.getStatus())
                .eqIfPresent(DocDO::getType, reqVO.getType())
                .likeIfPresent(DocDO::getTitle, reqVO.getTitle())
                .likeIfPresent(DocDO::getKeywords, reqVO.getKeywords())
                .neIfPresent(DocDO::getType, Boolean.TRUE.equals(reqVO.getExcludeChapter()) ? "chapter" : null)
                .orderByDesc(DocDO::getId));
    }

    /** 我的文档：创建人 / 指派给 / 最后修改人 任一中即算（先建 wrapper，避免链尾退化成基类型） */
    private static void applyMember(LambdaQueryWrapperX<DocDO> wrapper, String member) {
        if (org.springframework.util.StringUtils.hasText(member)) {
            wrapper.and(w -> w.eq(DocDO::getAddedBy, member)
                    .or().eq(DocDO::getAssignedTo, member)
                    .or().eq(DocDO::getEditedBy, member));
        }
    }

    /** 「我的文档」分页：带 member 条件的入口（与 selectPage 分开，避免影响库内浏览的既有行为） */
    default PageResult<DocDO> selectMyPage(DocPageReqVO reqVO) {
        LambdaQueryWrapperX<DocDO> wrapper = new LambdaQueryWrapperX<>();
        // 章节（type=chapter）是目录节点，不是给人看的文档，默认从「我的文档」里剔掉；
        // 但如果调用方明确要 chapter，就不能再叠一个 type<>chapter（否则恒空）
        if (org.springframework.util.StringUtils.hasText(reqVO.getType())) {
            wrapper.eq(DocDO::getType, reqVO.getType());
        } else {
            wrapper.ne(DocDO::getType, "chapter");
        }
        wrapper.likeIfPresent(DocDO::getTitle, reqVO.getTitle());
        applyMember(wrapper, reqVO.getMember());
        wrapper.orderByDesc(DocDO::getId);
        return selectPage(reqVO, wrapper);
    }

    /**
     * 某库下的全部章节（按 path 排序即树的前序）
     */
    default List<DocDO> selectChapterList(Long lib) {
        return selectList(new LambdaQueryWrapperX<DocDO>()
                .eq(DocDO::getLib, lib)
                .eq(DocDO::getType, "chapter")
                .orderByAsc(DocDO::getPath));
    }

    /**
     * 某库下的全部文档（不含章节），用于「库内按章节聚合」的场景
     */
    default List<DocDO> selectListByLib(Long lib) {
        return selectList(new LambdaQueryWrapperX<DocDO>()
                .eq(DocDO::getLib, lib)
                .ne(DocDO::getType, "chapter")
                .orderByAsc(DocDO::getOrder)
                .orderByDesc(DocDO::getId));
    }

    /**
     * 自己 + 全部子孙章节。
     *
     * <p>{@code zt_doc.path} 是逗号包起来且**包含自己**的（,1,2,），
     * 所以子孙就是「path 以 自己的 path 为前缀」的行 —— 不需要递归。
     * 注意别写成 {@code path + id + ','}：path 里已经含自己了（README 第 14 条坑）。
     */
    default List<DocDO> selectSelfAndDescendants(Long doc) {
        DocDO self = selectById(doc);
        if (self == null) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<DocDO>()
                .likeRight(DocDO::getPath, self.getPath())
                .orderByAsc(DocDO::getPath));
    }

    /**
     * 某库下是否已存在同名章节/文档（禅道允许同名，这里只做提示用的判重）
     */
    default DocDO selectByLibAndParentAndTitle(Long lib, Long parent, String title) {
        return selectOne(new LambdaQueryWrapperX<DocDO>()
                .eq(DocDO::getLib, lib)
                .eq(DocDO::getParent, parent)
                .eq(DocDO::getTitle, title));
    }

    /**
     * 浏览计数 +1。
     *
     * <p>和附件的 downloads 一样：并发浏览时「查出来 +1 再写回」会互相覆盖，
     * 交给数据库自增才正确。禅道是 {@code set('views = views + 1')}。
     */
    @Update("UPDATE zt_doc SET views = views + 1 WHERE id = #{id} AND deleted = 0")
    int increaseViews(@Param("id") Long id);

    /**
     * 移动章节/文档：只改父、path、grade、lib、module 这几个结构字段。
     *
     * <p>用原生 UPDATE 而不是 updateById 是有意的：{@code path} 必须能被写成新值，
     * 而 updateById 跳过 null（不能把 parent 置成 0/清空）。这里的值都不是 null，
     * 但把「树结构调整」集中成一条 SQL 更好审。
     */
    @Update("UPDATE zt_doc SET lib = #{lib}, parent = #{parent}, path = #{path}, grade = #{grade}, "
            + "module = #{module}, update_time = NOW() WHERE id = #{id} AND deleted = 0")
    int updateStructure(@Param("id") Long id, @Param("lib") Long lib, @Param("parent") Long parent,
                        @Param("path") String path, @Param("grade") Integer grade,
                        @Param("module") Long module);

    /**
     * 子树整体改挂：path 前缀替换。
     *
     * <p>用 SQL 的 REPLACE 一次改完整棵子树 —— 逐行 update 也行，但树深了就是 N 次往返。
     */
    @Update("UPDATE zt_doc SET path = CONCAT(#{newPrefix}, SUBSTRING(path, #{cutLen} + 1)), "
            + "lib = #{lib}, update_time = NOW() "
            + "WHERE path LIKE CONCAT(#{oldPrefix}, '%') AND deleted = 0 AND id <> #{selfId}")
    int moveSubtreePath(@Param("oldPrefix") String oldPrefix, @Param("newPrefix") String newPrefix,
                        @Param("cutLen") int cutLen, @Param("lib") Long lib, @Param("selfId") Long selfId);

    /**
     * 重算子树的 grade（父层级变了，所有子孙都要跟着变）
     */
    @Update("UPDATE zt_doc SET grade = grade + #{delta}, update_time = NOW() "
            + "WHERE path LIKE CONCAT(#{prefix}, '%') AND deleted = 0 AND id <> #{selfId}")
    int shiftSubtreeGrade(@Param("prefix") String prefix, @Param("delta") int delta,
                          @Param("selfId") Long selfId);

}
