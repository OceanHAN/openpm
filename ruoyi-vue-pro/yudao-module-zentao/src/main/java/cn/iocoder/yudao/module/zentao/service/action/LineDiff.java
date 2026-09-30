package cn.iocoder.yudao.module.zentao.service.action;

/**
 * 行级文本差异工具
 *
 * 禅道用 {@code common::diff()} 生成 HTML 片段（&lt;del&gt;/&lt;ins&gt;）。
 * 这里刻意**不生成 HTML**：日志内容会被前端直接渲染，存 HTML 会引入 XSS 风险。
 * 改为输出统一 diff 风格的纯文本，前端放在 &lt;pre&gt; 里按前缀着色即可：
 *
 * <pre>
 *   "  未改动的行"   （前缀两个空格）
 *   "- 被删除的行"   （前缀 - ）
 *   "+ 新增的行"     （前缀 + ）
 * </pre>
 */
public final class LineDiff {

    /**
     * 参与 diff 的最大行数。超过则不做逐行比较，避免大文本把日志表撑爆。
     */
    private static final int MAX_LINES = 800;

    private LineDiff() {}

    /**
     * 计算两个文本的行级差异
     *
     * @param oldText 旧文本
     * @param newText 新文本
     * @return 统一 diff 风格的纯文本；两文本相同或过大时返回空串
     */
    public static String diff(String oldText, String newText) {
        String oldSafe = oldText == null ? "" : oldText.replace("&nbsp;", "").trim();
        String newSafe = newText == null ? "" : newText.replace("&nbsp;", "").trim();
        if (oldSafe.equals(newSafe)) {
            return "";
        }

        String[] a = oldSafe.isEmpty() ? new String[0] : oldSafe.split("\n", -1);
        String[] b = newSafe.isEmpty() ? new String[0] : newSafe.split("\n", -1);
        if (a.length > MAX_LINES || b.length > MAX_LINES) {
            return "（文本过长，未生成逐行差异）";
        }

        // LCS 动态规划表
        int[][] lcs = new int[a.length + 1][b.length + 1];
        for (int i = a.length - 1; i >= 0; i--) {
            for (int j = b.length - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j])
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        StringBuilder sb = new StringBuilder();
        int i = 0, j = 0;
        while (i < a.length && j < b.length) {
            if (a[i].equals(b[j])) {
                sb.append("  ").append(a[i]).append('\n');
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                sb.append("- ").append(a[i]).append('\n');
                i++;
            } else {
                sb.append("+ ").append(b[j]).append('\n');
                j++;
            }
        }
        while (i < a.length) {
            sb.append("- ").append(a[i++]).append('\n');
        }
        while (j < b.length) {
            sb.append("+ ").append(b[j++]).append('\n');
        }
        return sb.toString();
    }

    /**
     * 该字段是否值得生成逐行差异。
     * 对应禅道 createChanges 里的长文本字段白名单。
     */
    public static boolean needDiff(String field) {
        if (field == null) {
            return false;
        }
        String f = field.toLowerCase();
        return ",name,title,desc,spec,steps,content,digest,verify,report,definition,analysis,"
                .concat("summary,prevention,resolution,outline,schedule,minutes,sql,interface,ui,")
                .concat("langs,performance,privileges,search,actions,deploy,bi,safe,other,")
                .contains("," + f + ",");
    }

}
