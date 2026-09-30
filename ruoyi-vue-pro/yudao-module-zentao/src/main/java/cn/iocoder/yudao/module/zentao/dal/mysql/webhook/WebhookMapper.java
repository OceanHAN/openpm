package cn.iocoder.yudao.module.zentao.dal.mysql.webhook;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zentao.controller.admin.webhook.vo.WebhookPageReqVO;
import cn.iocoder.yudao.module.zentao.dal.dataobject.webhook.WebhookDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Webhook Mapper（禅道 {@code webhookModel} 的 DAO 部分）。
 *
 * <p>命名纪律（坑位 #25/#47）：{@code WebhookDO}/{@code WebhookMapper}/{@code WebhookService}
 * 这些短名都**没有**和 yudao 框架模块重名（infra 的 file/notice 那些才是雷区），
 * 全仓 grep 过一遍后才用；控制器仍然一律带 {@code Zentao} 前缀。
 *
 * <p>写法纪律：{@code LambdaQueryWrapperX} 只覆写了一部分方法，继承来的（如 {@code orderByDesc}）
 * 会把表达式类型退回 {@code LambdaQueryWrapper}，所以一律「先建 wrapper、再逐条语句调用」。
 */
@Mapper
public interface WebhookMapper extends BaseMapperX<WebhookDO> {

    /**
     * 分页。禅道 {@code getList()} 只有 {@code deleted = 0} 一个硬条件
     * （产品/执行的过滤是在 buildData 里按「当前这次动作」做的，不是列表过滤 —— 见 WebhookService）。
     */
    default PageResult<WebhookDO> selectPage(WebhookPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<WebhookDO>()
                .likeIfPresent(WebhookDO::getName, reqVO.getName())
                .eqIfPresent(WebhookDO::getType, reqVO.getType())
                .eqIfPresent(WebhookDO::getUrl, reqVO.getUrl())
                .orderByDesc(WebhookDO::getId));
    }

    /**
     * 全部启用的 webhook，按 id 升序 —— 对应禅道 {@code send()} 里的
     * {@code $webhooks = $this->getList()}（它用 id 做键，逐个尝试，顺序稳定）。
     */
    default List<WebhookDO> selectEnabledList() {
        LambdaQueryWrapperX<WebhookDO> wrapper = new LambdaQueryWrapperX<>();
        wrapper.orderByAsc(WebhookDO::getId);
        return selectList(wrapper);
    }

}
