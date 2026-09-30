package cn.iocoder.yudao.module.zentao.enums.doc;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 文档类型
 *
 * <p>对齐禅道 {@code $config->doc->docTypes = 'text,word,ppt,excel,url,article,attachment'}
 * 与实际落库的 {@code zt_doc.type} 取值。
 *
 * <p><b>chapter 不是文档</b>：它是章节树的中间节点，没有正文、不写 zt_doccontent。
 * 所以任何「文档列表」都要把它排除（{@link #isChapter}）。
 */
@Getter
@AllArgsConstructor
public enum DocTypeEnum {

    CHAPTER("chapter", "章节"),
    HTML("html", "富文本"),
    MARKDOWN("markdown", "Markdown"),
    TEXT("text", "纯文本"),
    URL("url", "链接"),
    WORD("word", "Word"),
    PPT("ppt", "PPT"),
    EXCEL("excel", "Excel"),
    ATTACHMENT("attachment", "附件");

    private final String type;
    private final String name;

    /**
     * 「不写正文、只作为树节点」的类型。禅道里叫 notArticleType。
     */
    public static boolean isChapter(String type) {
        return CHAPTER.type.equals(type);
    }

    /**
     * 需要正文的类型（url 的正文就是一个 URL，也算）
     */
    public static boolean needsContent(String type) {
        return !isChapter(type) && !"attachment".equals(type);
    }

    public static DocTypeEnum of(String type) {
        return Arrays.stream(values()).filter(item -> item.type.equals(type)).findFirst().orElse(null);
    }

    public static List<DocTypeEnum> list() {
        return Arrays.asList(values());
    }

}
