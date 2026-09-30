package cn.iocoder.yudao.module.zentao.enums.doc;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 文档状态
 *
 * <p>禅道 {@code $lang->doc->statusList}：{@code normal=已发布}、{@code draft=草稿}。
 *
 * <p>草稿不是「换个状态」这么简单：它决定正文写在哪里 ——
 * 草稿期间正文存在 {@code zt_doc.draft}，同时 {@code zt_doccontent} 里有一行
 * {@code version=0}；发布时这一行被升成 version=1（见 DocServiceImpl.releaseDoc）。
 */
@Getter
@AllArgsConstructor
public enum DocStatusEnum {

    NORMAL("normal", "已发布"),
    DRAFT("draft", "草稿");

    private final String status;
    private final String name;

    public static DocStatusEnum of(String status) {
        return Arrays.stream(values()).filter(item -> item.status.equals(status)).findFirst().orElse(null);
    }

}
