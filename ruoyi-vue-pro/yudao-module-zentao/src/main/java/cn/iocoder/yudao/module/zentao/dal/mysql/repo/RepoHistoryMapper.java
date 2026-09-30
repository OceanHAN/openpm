package cn.iocoder.yudao.module.zentao.dal.mysql.repo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.repo.vo.RepoCommitPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.repo.RepoHistoryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RepoHistoryMapper extends BaseMapperX<RepoHistoryDO> {

    default PageResult<RepoHistoryDO> selectPage(RepoCommitPageReqVO reqVO) {
        LambdaQueryWrapperX<RepoHistoryDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.eqIfPresent(RepoHistoryDO::getRepo, reqVO.getRepo());
        wrapper.likeIfPresent(RepoHistoryDO::getCommitter, reqVO.getCommitter());
        wrapper.likeIfPresent(RepoHistoryDO::getComment, reqVO.getKeywords());
        wrapper.orderByDesc(RepoHistoryDO::getCommit);
        return selectPage(reqVO, wrapper);
    }

    default RepoHistoryDO selectByRevision(Long repo, String revision) {
        return selectOne(new LambdaQueryWrapperX<RepoHistoryDO>()
                .eq(RepoHistoryDO::getRepo, repo)
                .eq(RepoHistoryDO::getRevision, revision)
                .last("LIMIT 1"));
    }

    /** 某个代码库已同步的最大 commit 序号（同步时接着往下编号） */
    @Select("SELECT COALESCE(MAX(commit), 0) FROM zt_repohistory WHERE repo = #{repo}")
    Integer selectMaxCommit(@Param("repo") Long repo);

    /**
     * 某个对象（需求/任务/缺陷）关联的提交：走通用关系表。
     * 与禅道 {@code repo::getCommitsByObject} 同一口径（AType='revision' + relation='commit'）。
     */
    @Select("<script>SELECT h.* FROM zt_repohistory h JOIN zt_relation r ON r.AID = h.id "
            + "WHERE r.AType = 'revision' AND r.relation = 'commit' "
            + "AND r.BType = #{objectType} AND r.BID = #{objectID} "
            + "<if test='repo != null'> AND h.repo = #{repo}</if> "
            + "ORDER BY h.commit DESC</script>")
    List<RepoHistoryDO> selectListByObject(@Param("objectType") String objectType,
                                          @Param("objectID") Long objectID,
                                          @Param("repo") Long repo);

}
