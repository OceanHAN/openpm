package cn.iocoder.yudao.module.zentao.dal.mysql.company;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.company.vo.CompanyPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.company.CompanyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 公司 Mapper。
 *
 * <p>注意：{@code LambdaQueryWrapperX} 只覆写了一部分方法，继承来的方法（如 {@code ne}、{@code orderByAsc}）
 * 会把表达式类型退回 {@code LambdaQueryWrapper}，所以这里一律「先建 wrapper、再逐条语句调用」（坑位 #1 的变体）。
 */
@Mapper
public interface CompanyMapper extends BaseMapperX<CompanyDO> {

    default PageResult<CompanyDO> selectPage(CompanyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CompanyDO>()
                .likeIfPresent(CompanyDO::getName, reqVO.getName())
                .eqIfPresent(CompanyDO::getGuest, reqVO.getGuest())
                .orderByAsc(CompanyDO::getId));
    }

    /** 禅道 {@code getFirst()}：按 id 升序取第一条（单公司部署时就是「本公司」） */
    default CompanyDO selectFirst() {
        LambdaQueryWrapperX<CompanyDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.orderByAsc(CompanyDO::getId);
        wrapper.last("LIMIT 1");
        List<CompanyDO> list = selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 禅道 {@code getOutsideCompanies()}：{@code id != 1} 且未删除，按 id 升序 */
    default List<CompanyDO> selectOutsideList() {
        LambdaQueryWrapperX<CompanyDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.ne(CompanyDO::getId, 1L);
        wrapper.orderByAsc(CompanyDO::getId);
        return selectList(wrapper);
    }

    /**
     * 按名称取 id，**包含已逻辑删除的记录**。
     *
     * <p>禅道 {@code batchCheck('name','unique', "id != x")} 生成的 SQL 没有 {@code deleted} 条件，
     * 所以「删掉的公司名还能不能再用」答案是**不能**（同 {@code entry} 的代号，见坑位 #52 那一段）。
     */
    @Select("SELECT id FROM zt_company WHERE name = #{name} LIMIT 1")
    Long selectIdByNameIgnoreDeleted(@Param("name") String name);

}
