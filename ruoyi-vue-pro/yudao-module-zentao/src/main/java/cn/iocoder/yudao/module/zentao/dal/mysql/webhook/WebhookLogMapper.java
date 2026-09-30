package cn.iocoder.yudao.module.zentao.dal.mysql.webhook;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookLogPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.webhook.WebhookLogDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Webhook 发送日志 Mapper（禅道 {@code zt_log}，{@code objectType='webhook'}）。
 *
 * <p>表由 entry 模块建的（{@code 49-zt_entry.sql}），本模块**只插不改**；
 * 「按 webhook 编号取日志」对应禅道 {@code webhook::getLogList()}。
 */
@Mapper
public interface WebhookLogMapper extends BaseMapperX<WebhookLogDO> {

    default PageResult<WebhookLogDO> selectPage(WebhookLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<WebhookLogDO>()
                .eqIfPresent(WebhookLogDO::getObjectType, reqVO.getObjectType())
                .eqIfPresent(WebhookLogDO::getObjectID, reqVO.getObjectID())
                .eqIfPresent(WebhookLogDO::getAction, reqVO.getAction())
                .likeIfPresent(WebhookLogDO::getUrl, reqVO.getUrl())
                .betweenIfPresent(WebhookLogDO::getDate, reqVO.getDate())
                .orderByDesc(WebhookLogDO::getId));
    }

    /**
     * 取某个 webhook 的全部日志（测试与「最近一次发送结果」用），id 倒序 = 最新在前。
     *
     * <p>注意排序方向决定语义（坑位 #31）：这里是 {@code id DESC}，
     * 所以「列表第一个就是最新一条」，取最新请用 {@code get(0)}。
     */
    default List<WebhookLogDO> selectListByWebhook(Long webhookId) {
        LambdaQueryWrapperX<WebhookLogDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.eq(WebhookLogDO::getObjectType, WebhookLogDO.OBJECT_TYPE_WEBHOOK);
        wrapper.eq(WebhookLogDO::getObjectID, webhookId);
        wrapper.orderByDesc(WebhookLogDO::getId);
        return selectList(wrapper);
    }

    /**
     * 物理删除某个 webhook 的日志（**只给测试脚本的收尾清理用**）。
     *
     * <p>禅道自己不删 webhook 日志（由 {@code admin::deleteLog} 按保留天数统一清），
     * 所以这里用裸 SQL 而不是逻辑删：{@code zt_log} 根本没有 {@code deleted} 列。
     */
    @Delete("DELETE FROM zt_log WHERE objectType = 'webhook' AND objectID = #{webhookId}")
    int deleteByWebhookId(@Param("webhookId") Long webhookId);

}
