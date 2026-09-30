package cn.iocoder.yudao.module.zentao.dal.mysql.testcase;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.testcase.vo.CasePageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.testcase.CaseDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 测试用例 Mapper
 */
@Mapper
public interface CaseMapper extends BaseMapperX<CaseDO> {

    default PageResult<CaseDO> selectPage(CasePageReqVO reqVO, Collection<Long> moduleIds, Boolean needConfirm) {
        LambdaQueryWrapperX<CaseDO> wrapper = new LambdaQueryWrapperX<CaseDO>()
                .eqIfPresent(CaseDO::getProduct, reqVO.getProduct())
                .eqIfPresent(CaseDO::getBranch, reqVO.getBranch())
                .inIfPresent(CaseDO::getModule, moduleIds)
                .eqIfPresent(CaseDO::getStory, reqVO.getStory())
                .eqIfPresent(CaseDO::getType, reqVO.getType())
                .eqIfPresent(CaseDO::getStatus, reqVO.getStatus())
                .eqIfPresent(CaseDO::getPri, reqVO.getPri())
                .likeIfPresent(CaseDO::getTitle, reqVO.getTitle())
                .likeIfPresent(CaseDO::getKeywords, reqVO.getKeywords());
        // 用例的归属是**二选一**：product>0（产品用例）或 lib>0（用例库用例）。
        // 查用例库用例按 lib 过滤；查产品用例必须排除库用例（lib=0）——
        // 不写这一条，库里那批 product=0 的用例会混进产品用例列表（共用表要强制加过滤条件，坑位 #12）。
        if (reqVO.getLib() != null && reqVO.getLib() > 0) {
            wrapper.eq(CaseDO::getLib, reqVO.getLib());
        } else {
            wrapper.eq(CaseDO::getLib, 0L);
        }
        // stage 是逗号列表，只能 FIND_IN_SET
        if (reqVO.getStage() != null && !reqVO.getStage().isEmpty()) {
            wrapper.apply("FIND_IN_SET({0}, stage)", reqVO.getStage());
        }
        // 「待确认」= 关联需求已经升版（story.version > case.storyVersion）且需求还是激活态。
        // 这条判断需要 join zt_story，所以用 EXISTS 子查询写成 SQL 片段。
        if (Boolean.TRUE.equals(needConfirm)) {
            // 注意用三参数重载：LambdaQueryWrapperX 只覆写了 exists(condition, sql, values)，
            // 单参数的 exists(sql) 会退化成基类型，赋不回 LambdaQueryWrapperX。
            wrapper.exists(true, "SELECT 1 FROM zt_story s WHERE s.id = zt_case.story AND s.deleted = 0 "
                    + "AND s.status = 'active' AND s.version > zt_case.storyVersion");
        }
        // 「我的用例」：我创建的 / 我评审过的（reviewedBy 是逗号列表，用 FIND_IN_SET）
        wrapper.eqIfPresent(CaseDO::getOpenedBy, reqVO.getOpenedBy());
        if (org.springframework.util.StringUtils.hasText(reqVO.getReviewedBy())) {
            wrapper.apply("FIND_IN_SET({0}, reviewedBy)", reqVO.getReviewedBy());
        }
        wrapper.orderByDesc(CaseDO::getId);
        return selectPage(reqVO, wrapper);
    }

    default List<CaseDO> selectListByStory(Long story) {
        return selectList(new LambdaQueryWrapperX<CaseDO>()
                .eq(CaseDO::getStory, story)
                .orderByDesc(CaseDO::getId));
    }

    default List<CaseDO> selectListByProduct(Long product) {
        return selectList(new LambdaQueryWrapperX<CaseDO>()
                .eq(CaseDO::getProduct, product)
                .orderByDesc(CaseDO::getId));
    }

    /**
     * 「可以导入用例库」的产品用例：product 下的产品用例（lib=0），排除已经导入过这个库的
     * （禅道 {@code testcase/getCanImportCases} 就是按 {@code fromCaseID} 判重）
     */
    default PageResult<CaseDO> selectCanImportPage(Long product, String title, Collection<Long> excludedSourceIds,
                                                   PageParam pageParam) {
        LambdaQueryWrapper<CaseDO> wrapper = new LambdaQueryWrapperX<CaseDO>()
                .eq(CaseDO::getProduct, product)
                .eq(CaseDO::getLib, 0L)
                .likeIfPresent(CaseDO::getTitle, title);
        if (excludedSourceIds != null && !excludedSourceIds.isEmpty()) {
            // notIn 没有 X 版本（返回基类型），所以这里不接链式调用，直接对同一 wrapper 赋值
            wrapper.notIn(CaseDO::getId, excludedSourceIds);
        }
        wrapper.orderByDesc(CaseDO::getId);
        return selectPage(pageParam, wrapper);
    }

    /**
     * 某个用例库已导入的来源用例编号（用于「已导入」判重）
     */
    default List<CaseDO> selectListByLib(Long lib) {
        return selectList(new LambdaQueryWrapperX<CaseDO>()
                .eq(CaseDO::getLib, lib)
                .orderByDesc(CaseDO::getId));
    }

}
