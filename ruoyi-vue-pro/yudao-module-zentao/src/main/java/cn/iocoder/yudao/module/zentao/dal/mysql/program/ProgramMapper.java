package cn.iocoder.yudao.module.zentao.dal.mysql.program;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.program.vo.ProgramPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.zentao.enums.execution.ExecutionTypeEnum;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 项目集 Mapper
 *
 * 项目集没有自己的表 —— 它就是 {@code zt_project} 里 {@code type='program'} 的行
 * （禅道 {@code define('TABLE_PROGRAM', '`zt_project`')}）。
 * 所以这里复用 {@link ProjectDO}，并在**每一个查询**上写死 {@code type='program'}：
 * 少写一处，项目或执行就会被当成项目集读出来。
 */
@Mapper
public interface ProgramMapper extends BaseMapperX<ProjectDO> {

    /** 项目集的类型条件，见 {@link ExecutionTypeEnum#PROGRAM} */
    String TYPE_PROGRAM = "program";

    default PageResult<ProjectDO> selectPage(ProgramPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProjectDO>()
                .eq(ProjectDO::getType, TYPE_PROGRAM)
                .likeIfPresent(ProjectDO::getName, reqVO.getName())
                .likeIfPresent(ProjectDO::getCode, reqVO.getCode())
                .eqIfPresent(ProjectDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProjectDO::getParent, reqVO.getParent())
                .eqIfPresent(ProjectDO::getPM, reqVO.getPM())
                .orderByAsc(ProjectDO::getOrder)
                .orderByDesc(ProjectDO::getId));
    }

    /** 全部项目集（下拉/树用），按 order 排 */
    default List<ProjectDO> selectList() {
        return selectList(new LambdaQueryWrapperX<ProjectDO>()
                .eq(ProjectDO::getType, TYPE_PROGRAM)
                .orderByAsc(ProjectDO::getOrder)
                .orderByAsc(ProjectDO::getId));
    }

    /** 未关闭的项目集，供下拉选择 */
    default List<ProjectDO> selectSimpleList() {
        return selectList(new LambdaQueryWrapperX<ProjectDO>()
                .eq(ProjectDO::getType, TYPE_PROGRAM)
                .ne(ProjectDO::getStatus, "closed")
                .orderByAsc(ProjectDO::getOrder)
                .orderByAsc(ProjectDO::getId));
    }

    /** 下级项目集 */
    default List<ProjectDO> selectListByParent(Long parent) {
        return selectList(new LambdaQueryWrapperX<ProjectDO>()
                .eq(ProjectDO::getType, TYPE_PROGRAM)
                .eq(ProjectDO::getParent, parent)
                .orderByAsc(ProjectDO::getOrder)
                .orderByAsc(ProjectDO::getId));
    }

    /**
     * 同级同名查重。禅道的唯一性范围是 {@code type='program' AND parent=当前父项目集}，
     * 也就是说不同项目集下可以重名（module/program/model.php#create 的 unique 规则）。
     */
    default ProjectDO selectByNameAndParent(String name, Long parent) {
        return selectOne(new LambdaQueryWrapperX<ProjectDO>()
                .eq(ProjectDO::getType, TYPE_PROGRAM)
                .eq(ProjectDO::getName, name)
                .eq(ProjectDO::getParent, parent == null ? 0L : parent)
                .last("LIMIT 1"));
    }

    /**
     * 子树：path 里含 {@code ,id,} 的项目集/项目。
     * 项目集的 path 是逗号格式（,9001,9002,），所以子孙就是 path 里含自己那段的行。
     */
    default List<ProjectDO> selectDescendants(Long programId) {
        return selectList(new LambdaQueryWrapperX<ProjectDO>()
                .in(ProjectDO::getType, TYPE_PROGRAM, ExecutionTypeEnum.PROJECT.getType())
                .like(ProjectDO::getPath, "," + programId + ",")
                .ne(ProjectDO::getId, programId)
                .orderByAsc(ProjectDO::getGrade)
                .orderByAsc(ProjectDO::getId));
    }

    /** 批量取项目集（列表回填「所属项目集」名称用） */
    default List<ProjectDO> selectListByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectBatchIds(ids);
    }

}
