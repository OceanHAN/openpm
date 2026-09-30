package cn.iocoder.yudao.module.zentao.dal.mysql.entry;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.entry.vo.EntryPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.entry.EntryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 应用接入 Mapper。
 */
@Mapper
public interface EntryMapper extends BaseMapperX<EntryDO> {

    /** 禅道 {@code entry::getList()} 里的 {@code andWhere('code')->ne('gitfox')}：内置代码库接入不露出 */
    String INTERNAL_CODE_GITFOX = "gitfox";

    default PageResult<EntryDO> selectPage(EntryPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EntryDO>()
                .likeIfPresent(EntryDO::getName, reqVO.getName())
                .eqIfPresent(EntryDO::getCode, reqVO.getCode())
                .eqIfPresent(EntryDO::getAccount, reqVO.getAccount())
                .eqIfPresent(EntryDO::getFreePasswd, reqVO.getFreePasswd())
                .betweenIfPresent(EntryDO::getCreatedDate, reqVO.getCreatedDate())
                .and(true, inner -> inner.ne(EntryDO::getCode, INTERNAL_CODE_GITFOX))
                .orderByDesc(EntryDO::getId));
    }

    default EntryDO selectByCode(String code) {
        return selectOne(EntryDO::getCode, code);
    }

    default EntryDO selectByName(String name) {
        return selectOne(EntryDO::getName, name);
    }

    /**
     * 取某代号对应的 id，**包含已逻辑删除的记录**。
     *
     * <p>为什么必须绕开逻辑删除：禅道 {@code check('code', 'unique')} 生成的 SQL 是
     * {@code SELECT COUNT(1) FROM zt_entry WHERE code = 'x'}（见 {@code lib/base/dao/dao.class.php}
     * 的 unique 分支）—— **没有 deleted 条件**。所以「删掉一个应用后，同代号还能不能再用」
     * 在禅道里的答案是**不能**。用 MyBatis-Plus 的普通查询会带上 {@code deleted = 0}，
     * 语义就变了。自定义 SQL 不走逻辑删除，正好用来还原这个行为。
     */
    @Select("SELECT id FROM zt_entry WHERE code = #{code} LIMIT 1")
    Long selectIdByCodeIgnoreDeleted(@Param("code") String code);

    /**
     * 回写最近一次调用的请求时间戳（禅道 {@code entry::updateCalledTime}）。
     *
     * <p>它是防重放的关键：下一次带时间戳的请求必须 {@code time > calledTime}。
     *
     * <p><b>为什么用裸 SQL 而不是 {@code update(entity, wrapper)}</b>：
     * <ol>
     *   <li>这个接口是 {@code @PermitAll}（调用方还没有登录态），MyBatis-Plus 的
     *       {@code updateFill} 拿不到登录用户，但**填充字段 {@code updater} 依然会进 SET 子句**，
     *       于是写进去一个 NULL，撞上 {@code updater varchar(64) NOT NULL} 报
     *       {@code Column 'updater' cannot be null}（本轮踩到并修掉的真错）。</li>
     *   <li>禅道这句本来就是「只更新 calledTime 一列」，裸 SQL 更贴原意，也不会顺手改掉审计列。</li>
     * </ol>
     */
    @Update("UPDATE zt_entry SET calledTime = #{time} WHERE code = #{code}")
    int updateCalledTime(@Param("code") String code, @Param("time") Integer time);

}
