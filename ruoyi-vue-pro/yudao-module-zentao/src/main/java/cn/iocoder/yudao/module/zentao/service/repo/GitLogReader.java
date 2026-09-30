package cn.iocoder.yudao.module.zentao.service.repo;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 读本地 git 仓库的提交日志（禅道 {@code module/repo} 里 git 驱动那一部分）。
 *
 * <h3>为什么用「跑 git 命令」而不是 JGit</h3>
 * 禅道就是这么做的（在服务器上执行 {@code git log} 再解析输出），不引额外依赖、
 * 行为和用户本机看到的完全一致；代价是宿主机要装 git（部署说明里写明这条依赖）。
 *
 * <h3>两个安全点</h3>
 * <ol>
 *   <li>用 {@link ProcessBuilder} 的**参数数组**形式，不拼 shell 字符串 ——
 *       路径里有空格/分号/反引号也不会被解释；</li>
 *   <li>只跑只读命令（{@code log} / {@code rev-parse}），不做任何写操作。</li>
 * </ol>
 *
 * <h3>输出格式（踩过坑才定下来的）</h3>
 * {@code --pretty} 与 {@code --name-status -z} 拼在一起时，文件清单是**跟在记录分隔符之后**的，
 * 直接按分隔符切会把「上一条的文件」算到下一条头上。所以格式串写成
 * {@code <RS>%H<US>%an<US>%aI<US>%B<US>} —— 分隔符放在**最前面**、末尾再补一个字段分隔符，
 * 这样每条记录就是「4 个元信息字段 + 一段 NUL 分隔的文件清单」，切一刀就能拿全。
 * 用可见的 {@code <RS>}/{@code <US>} 而不是控制字符，是为了让提交说明里出现控制字符时也不会误切。
 */
@Slf4j
public class GitLogReader {

    private static final String RECORD_SEP = "<RS>";
    private static final String FIELD_SEP = "<US>";

    /**
     * 提交说明里的「对象关联」规则（禅道 {@code config/repo.php} 的默认值）：
     * {@code Story #1,2} / {@code Task #3} / {@code Bug #4,5} —— 模块名 + 可选空格 + {@code #} + 逗号分隔编号。
     */
    private static final Pattern STORY_REGEX = Pattern.compile("Story *(#\\d+(?:,\\d+)*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TASK_REGEX = Pattern.compile("Task *(#\\d+(?:,\\d+)*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUG_REGEX = Pattern.compile("Bug *(#\\d+(?:,\\d+)*)", Pattern.CASE_INSENSITIVE);

    /** 一次读取的提交 */
    @Data
    public static class Commit {
        private String revision;
        private String committer;
        private LocalDateTime time;
        private String comment;
        private List<ChangedFile> files = new ArrayList<>();
    }

    /** 提交里被改动的文件 */
    @Data
    public static class ChangedFile {
        private String action;
        private String path;
        private String oldPath;
    }

    /** 路径是否是个 git 仓库 */
    public static boolean isGitRepo(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        Path dir = Paths.get(path);
        return Files.isDirectory(dir) && Files.isDirectory(dir.resolve(".git"));
    }

    /**
     * 读提交日志（新 → 旧）。
     *
     * @param path      本地仓库路径
     * @param maxCount  最多读多少条（防止第一次同步把几万条全拉进来）
     * @param stopAtSha 遇到这个 sha 就停（增量同步用，它本身已经入库了）；可为空
     */
    public static List<Commit> read(String path, int maxCount, String stopAtSha) {
        List<String> command = new ArrayList<>(List.of("git", "-C", path, "log",
                "--pretty=format:" + RECORD_SEP + "%H" + FIELD_SEP + "%an" + FIELD_SEP + "%aI" + FIELD_SEP + "%B"
                        + FIELD_SEP,
                "--name-status", "-z", "--max-count=" + maxCount));
        if (stopAtSha != null && !stopAtSha.isBlank()) {
            command.add(stopAtSha + "..HEAD");
        }
        return parse(exec(command), stopAtSha);
    }

    /** 当前 HEAD 的 sha（同步后记录「同步到哪儿了」） */
    public static String headRevision(String path) {
        return exec(List.of("git", "-C", path, "rev-parse", "HEAD")).trim();
    }

    private static String exec(List<String> command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("git 命令执行失败（exit=" + exit + "）：" + output);
            }
            return output.toString();
        } catch (IOException e) {
            throw new IllegalStateException("执行 git 失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("执行 git 被中断", e);
        }
    }

    /** 解析 {@code git log} 的输出（格式见类注释） */
    static List<Commit> parse(String raw, String stopAtSha) {
        List<Commit> commits = new ArrayList<>();
        for (String record : raw.split(RECORD_SEP)) {
            if (record.isBlank()) {
                continue;
            }
            String[] parts = record.split(FIELD_SEP, 5);
            if (parts.length < 4) {
                continue;
            }
            Commit commit = new Commit();
            commit.setRevision(parts[0].trim());
            commit.setCommitter(parts[1].trim());
            commit.setTime(parseTime(parts[2].trim()));
            commit.setComment(parts[3].trim());
            if (parts.length == 5) {
                parseFiles(commit, parts[4]);
            }
            if (stopAtSha != null && stopAtSha.equals(commit.getRevision())) {
                break; // 增量同步：到上次同步的位置就收工
            }
            commits.add(commit);
        }
        return commits;
    }

    /** 文件清单是 NUL 分隔的 token 流：A\0path\0M\0path2\0R100\0old\0new\0 */
    private static void parseFiles(Commit commit, String tokens) {
        List<String> list = new ArrayList<>();
        for (String token : tokens.split("\0")) {
            if (!token.isEmpty()) {
                list.add(token);
            }
        }
        if (!list.isEmpty()) {
            list.set(0, list.get(0).trim()); // 第一段混着换行
        }
        for (int i = 0; i < list.size(); ) {
            String status = list.get(i++);
            if (status.isEmpty() || i >= list.size()) {
                break;
            }
            String action = status.substring(0, 1);
            if ("R".equals(action) || "C".equals(action)) {
                if (i + 1 >= list.size()) {
                    break;
                }
                String oldPath = list.get(i++);
                String newPath = list.get(i++);
                commit.getFiles().add(file("R", newPath, oldPath));
            } else {
                commit.getFiles().add(file(action, list.get(i++), ""));
            }
        }
    }

    private static ChangedFile file(String action, String path, String oldPath) {
        ChangedFile file = new ChangedFile();
        file.setAction(action);
        file.setPath(path);
        file.setOldPath(oldPath);
        return file;
    }

    private static LocalDateTime parseTime(String iso) {
        try {
            return OffsetDateTime.parse(iso).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 提交说明 → 关联对象 ====================

    /**
     * 解析提交说明里的关联对象（禅道 {@code repo::parseComment}）。
     *
     * @return 形如 {@code story:1} / {@code task:3} / {@code bug:7} 的键集合
     */
    public static Set<String> parseLinkedKeys(String comment) {
        Set<String> keys = new LinkedHashSet<>();
        collect(comment, STORY_REGEX, "story", keys);
        collect(comment, TASK_REGEX, "task", keys);
        collect(comment, BUG_REGEX, "bug", keys);
        return keys;
    }

    private static void collect(String comment, Pattern pattern, String type, Set<String> keys) {
        if (comment == null) {
            return;
        }
        Matcher matcher = pattern.matcher(comment);
        while (matcher.find()) {
            for (String id : matcher.group(1).replace("#", "").split(",")) {
                if (!id.isBlank()) {
                    keys.add(type + ":" + id.trim());
                }
            }
        }
    }

}
