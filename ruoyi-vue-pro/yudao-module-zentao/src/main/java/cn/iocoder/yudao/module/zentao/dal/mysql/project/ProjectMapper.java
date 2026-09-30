package cn.iocoder.yudao.module.zentao.dal.mysql.project;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.execution.vo.ExecutionPageReqVO;
import cn.iocoder.yudao.module.zentao.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.zentao.enums.execution.ExecutionTypeEnum;
import cn.iocoder.yudao.module.zentao.dal.dataobject.project.ProjectDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 项目 Mapper
 */
@Mapper
public interface ProjectMapper extends BaseMapperX<ProjectDO> {

    default PageResult<ProjectDO> selectPage(ProjectPageReqVO reqVO) {
        // 注意：FIND_IN_SET 这类条件用 apply 拼，不能用 LambdaQueryWrapperX 的链式（apply 不在 X 的覆写清单里，
        // 写在链尾会让整条表达式退化成基类型）——所以先建 wrapper，再逐条加条件
        LambdaQueryWrapperX<ProjectDO> wrapper = new LambdaQueryWrapperX<>();
        // 执行与项目共用本表，项目列表必须排除执行
        wrapper.eq(ProjectDO::getType, ExecutionTypeEnum.PROJECT.getType());
        wrapper.likeIfPresent(ProjectDO::getName, reqVO.getName());
        wrapper.likeIfPresent(ProjectDO::getCode, reqVO.getCode());
        wrapper.eqIfPresent(ProjectDO::getStatus, reqVO.getStatus());
        wrapper.eqIfPresent(ProjectDO::getModel, reqVO.getModel());
        wrapper.eqIfPresent(ProjectDO::getParent, reqVO.getParent());
        wrapper.eqIfPresent(ProjectDO::getPM, reqVO.getPM());
        applyMember(wrapper, reqVO.getMember());
        wrapper.orderByAsc(ProjectDO::getOrder);
        wrapper.orderByDesc(ProjectDO::getId);
        return selectPage(reqVO, wrapper);
    }

    /**
     * 「我参与的项目/执行」：四个负责人字段 + 团队成员（逗号列表）任一中即算。
     *
     * <p>团队是 {@code zt_project.team} 的逗号列表，所以用 {@code FIND_IN_SET}；
     * 条件整体用 {@code apply} 拼（成员账号来自登录上下文，不是用户随便传的字符串）。
     */
    private static void applyMember(LambdaQueryWrapperX<ProjectDO> wrapper, String member) {
        if (org.springframework.util.StringUtils.hasText(member)) {
            wrapper.apply("(PM = {0} OR QD = {0} OR RD = {0} OR PO = {0} OR FIND_IN_SET({0}, team))", member);
        }
    }

    /**
     * 取未关闭的项目，供下拉选择
     */
    default List<ProjectDO> selectSimpleList() {
        return selectList(new LambdaQueryWrapperX<ProjectDO>()
                .eq(ProjectDO::getType, ExecutionTypeEnum.PROJECT.getType())
                .ne(ProjectDO::getStatus, "closed")
                .orderByAsc(ProjectDO::getOrder)
                .orderByAsc(ProjectDO::getId));
    }

    /**
     * 按名称查重（只在项目范围内查重，执行可以重名）
     */
    default ProjectDO selectByName(String name) {
        return selectOne(new LambdaQueryWrapperX<ProjectDO>()
                .eq(ProjectDO::getName, name)
                .eq(ProjectDO::getType, ExecutionTypeEnum.PROJECT.getType())
                .last("LIMIT 1"));
    }


    // ==================== 执行（与项目共用本表，按 type 过滤） ====================

    /**
     * 执行分页。必须带上 type IN ('sprint','stage','kanban')，
     * 否则会把项目也当成执行查出来。
     */
    default PageResult<ProjectDO> selectExecutionPage(ExecutionPageReqVO reqVO) {
        LambdaQueryWrapperX<ProjectDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.in(ProjectDO::getType, ExecutionTypeEnum.EXECUTION_TYPES);
        wrapper.likeIfPresent(ProjectDO::getName, reqVO.getName());
        wrapper.eqIfPresent(ProjectDO::getStatus, reqVO.getStatus());
        wrapper.eqIfPresent(ProjectDO::getType, reqVO.getType());
        wrapper.eqIfPresent(ProjectDO::getProject, reqVO.getProject());
        wrapper.eqIfPresent(ProjectDO::getPM, reqVO.getPM());
        applyMember(wrapper, reqVO.getMember());
        wrapper.orderByAsc(ProjectDO::getOrder);
        wrapper.orderByDesc(ProjectDO::getId);
        return selectPage(reqVO, wrapper);
    }

    /**
     * 某个项目下的全部执行
     */
    default List<ProjectDO> selectExecutionListByProject(Long project) {
        return selectList(new LambdaQueryWrapperX<ProjectDO>()
                .eq(ProjectDO::getProject, project)
                .in(ProjectDO::getType, ExecutionTypeEnum.EXECUTION_TYPES)
                .orderByAsc(ProjectDO::getOrder)
                .orderByDesc(ProjectDO::getId));
    }

    /**
     * 统计某个项目下的执行数量（禅道 project 的 executionCount 由此而来）
     */
    default Long countExecutionByProject(Long project) {
        return selectCount(new LambdaQueryWrapperX<ProjectDO>()
                .eq(ProjectDO::getProject, project)
                .in(ProjectDO::getType, ExecutionTypeEnum.EXECUTION_TYPES));
    }

    /**
     * 某个项目集下的项目（项目靠 parent 指向所属项目集）
     */
    default List<ProjectDO> selectListByParent(Long parent) {
        return selectList(new LambdaQueryWrapperX<ProjectDO>()
                .eq(ProjectDO::getParent, parent)
                .eq(ProjectDO::getType, ExecutionTypeEnum.PROJECT.getType())
                .orderByAsc(ProjectDO::getId));
    }

    /**
     * 子树：path 里含 {@code ,id,} 的项目集/项目（移动项目集时要重算它们的 path/grade）
     */
    default List<ProjectDO> selectDescendants(Long id) {
        return selectList(new LambdaQueryWrapperX<ProjectDO>()
                .in(ProjectDO::getType, ExecutionTypeEnum.PROGRAM.getType(), ExecutionTypeEnum.PROJECT.getType())
                .like(ProjectDO::getPath, "," + id + ",")
                .ne(ProjectDO::getId, id)
                .orderByAsc(ProjectDO::getGrade)
                .orderByAsc(ProjectDO::getId));
    }

}
