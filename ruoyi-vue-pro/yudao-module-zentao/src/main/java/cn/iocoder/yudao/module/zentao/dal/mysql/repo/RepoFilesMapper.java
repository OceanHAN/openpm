package cn.iocoder.yudao.module.zentao.dal.mysql.repo;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.dal.dataobject.repo.RepoFilesDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RepoFilesMapper extends BaseMapperX<RepoFilesDO> {

    default List<RepoFilesDO> selectListByRevision(Long repo, String revision) {
        return selectList(new LambdaQueryWrapperX<RepoFilesDO>()
                .eq(RepoFilesDO::getRepo, repo)
                .eq(RepoFilesDO::getRevision, revision)
                .orderByAsc(RepoFilesDO::getPath));
    }

    /** 重新同步某个 commit 时先清掉它的旧文件清单（本表没有 deleted 列，物理删） */
    @Delete("DELETE FROM zt_repofiles WHERE repo = #{repo} AND revision = #{revision}")
    int deleteByRevision(@Param("repo") Long repo, @Param("revision") String revision);

    /** 删代码库时清理它的文件清单 */
    @Delete("DELETE FROM zt_repofiles WHERE repo = #{repo}")
    int deleteByRepo(@Param("repo") Long repo);

}
