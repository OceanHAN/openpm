package cn.iocoder.yudao.module.zentao.service.bi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zentao.enums.ErrorCodeConstants.*;

/**
 * 只读 SQL 白名单。
 *
 * <p>数据视图要在 MySQL 上执行用户写的 SQL，所以必须先把「能写什么」卡死。规则（逐条都会报具体的错）：
 * <ol>
 *   <li><b>单语句</b>：不允许出现分号（也不允许注释符 {@code --} / {@code #} / {@code /*}，
 *       免得用注释把后半段藏起来）</li>
 *   <li><b>必须是查询</b>：以 {@code SELECT} 或 {@code WITH} 开头</li>
 *   <li><b>关键字黑名单</b>：insert/update/delete/drop/alter/create/truncate/replace/grant/revoke/
 *       call/set/use/into outfile/load_file/sleep/benchmark/… 一律拒绝（词边界匹配，
 *       所以 {@code settings} 里的 set 不会误伤）</li>
 *   <li><b>只允许禅道自己的表</b>：{@code FROM} / {@code JOIN} 后面的表名必须以 {@code zt_} 开头，
 *       不允许出现库名前缀（{@code a.b}）—— 这样它碰不到 {@code system_user} / {@code infra_file} 那些表</li>
 *   <li>执行侧再兜一层：结果集用 {@code SELECT * FROM (sql) t LIMIT n} 包起来，最多取 200 行</li>
 * </ol>
 *
 * <p>本实现是全项目**唯一**用字符串拼 SQL 的地方（MyBatis 的 {@code ${}}），
 * 所以规则写成这个类，测试里对每条规则都有断言。
 */
@Slf4j
@Component
public class SqlGuard {

    /** 不允许出现的「写/危险」关键字（词边界匹配） */
    private static final List<String> FORBIDDEN = List.of(
            "insert", "update", "delete", "drop", "alter", "create", "truncate", "replace",
            "grant", "revoke", "call", "execute", "prepare", "deallocate", "handler", "load_file",
            "outfile", "dumpfile", "sleep", "benchmark", "updatexml", "extractvalue",
            "set", "use", "lock", "unlock", "rename", "flush", "kill", "shutdown"
    );

    /** 系统库/系统表（即使不带库名前缀也不允许） */
    private static final List<String> FORBIDDEN_SCHEMAS = List.of(
            "information_schema", "performance_schema", "mysql", "sys"
    );

    private static final Pattern TABLE_PATTERN =
            Pattern.compile("(?i)\\b(?:from|join)\\s+([`\\w.\\-]+)");

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /** 校验并返回「去掉了结尾分号」的 SQL */
    public String validate(String sql) {
        if (!StringUtils.hasText(sql)) {
            throw exception(BI_SQL_INVALID, "SQL 不能为空");
        }
        String text = sql.trim();
        while (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        String lower = text.toLowerCase(Locale.ROOT);

        // ① 单语句 + 禁注释
        if (text.contains(";")) {
            throw exception(BI_SQL_INVALID, "只能写一条语句（SQL 里不能出现分号）");
        }
        if (text.contains("--") || text.contains("/*") || text.contains("#")) {
            throw exception(BI_SQL_INVALID, "SQL 里不能出现注释符");
        }
        // ② 必须是查询
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            throw exception(BI_SQL_INVALID, "只允许 SELECT / WITH 查询");
        }
        // ③ 关键字黑名单
        for (String word : FORBIDDEN) {
            if (containsWord(lower, word)) {
                throw exception(BI_SQL_INVALID, "SQL 里不允许出现关键字：" + word);
            }
        }
        for (String schema : FORBIDDEN_SCHEMAS) {
            // 用词边界匹配：`system_users` 里的 sys 不算（它会被表名白名单拦下，报更准的错）
            if (containsWord(lower, schema)) {
                throw exception(BI_SQL_INVALID, "SQL 里不允许出现系统库：" + schema);
            }
        }
        // ④ 表名白名单
        for (String table : extractTables(text)) {
            String name = table.replace("`", "");
            if (name.contains(".")) {
                throw exception(BI_SQL_FORBIDDEN_TABLE, name + "（不能带库名前缀）");
            }
            if (!name.toLowerCase(Locale.ROOT).startsWith("zt_")) {
                throw exception(BI_SQL_FORBIDDEN_TABLE, name);
            }
        }
        return text;
    }

    /** 字段名（维度/指标）必须是简单标识符 —— 它会以反引号拼进 SQL */
    public String validateIdentifier(String field) {
        if (!StringUtils.hasText(field) || !IDENTIFIER.matcher(field.trim()).matches()) {
            throw exception(BI_CHART_FIELD_INVALID, field);
        }
        return field.trim();
    }

    private boolean containsWord(String lowerSql, String word) {
        int index = 0;
        while ((index = lowerSql.indexOf(word, index)) >= 0) {
            boolean leftOk = index == 0 || !Character.isLetterOrDigit(lowerSql.charAt(index - 1))
                    && lowerSql.charAt(index - 1) != '_';
            int end = index + word.length();
            boolean rightOk = end >= lowerSql.length() || !Character.isLetterOrDigit(lowerSql.charAt(end))
                    && lowerSql.charAt(end) != '_';
            if (leftOk && rightOk) {
                return true;
            }
            index = end;
        }
        return false;
    }

    /** 取出 FROM / JOIN 后面的表名（子查询的括号会被过滤掉） */
    private List<String> extractTables(String sql) {
        List<String> tables = new ArrayList<>();
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!name.startsWith("(")) {
                tables.add(name);
            }
        }
        return tables;
    }

}
