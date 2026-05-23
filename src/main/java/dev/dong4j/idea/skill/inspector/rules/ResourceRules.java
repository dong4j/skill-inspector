package dev.dong4j.idea.skill.inspector.rules;

import com.intellij.openapi.util.TextRange;

import dev.dong4j.idea.skill.inspector.model.SkillBody;
import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillProblem;
import dev.dong4j.idea.skill.inspector.model.SkillReference;
import dev.dong4j.idea.skill.inspector.model.SkillSeverity;
import dev.dong4j.idea.skill.inspector.parser.MarkdownReferenceParser;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;
import dev.dong4j.idea.skill.inspector.util.TextRangeUtil;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Skill 资源规则
 * <p> 与 {@link ReferenceRules} 相反: {@code ReferenceRules} 从正文链接出发, 看目标是否存在;
 * 本规则从 {@code references/} / {@code scripts/} 两个推荐目录出发, 看是否被正文使用,
 * 用于发现"上传了但忘了在 SKILL.md 里引用 / 提及"的孤儿资源.
 * <p> 当前覆盖:
 * <ul>
 *   <li>{@code resource.unused-reference} —— {@code references/} 下的文件没被任何 Markdown 链接引用</li>
 *   <li>{@code script.missing-usage} —— {@code scripts/} 下的脚本既没被链接引用, 也没在正文文本中提到</li>
 * </ul>
 * <p> 设计取舍:
 * <ul>
 *   <li>所有问题统一上报在 SKILL.md 第一行: 真正的"孤儿"在文件系统层面, 没法标记到正文具体位置.</li>
 *   <li>对 {@code scripts/} 采用"链接 OR 文件名出现"的宽松判定, 允许 {@code 运行 foo.sh} 这种纯文本提及.</li>
 *   <li>不强制目录存在: 如果 {@code references/} / {@code scripts/} 不存在, 规则直接跳过, 不报错.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public class ResourceRules implements SkillRule {

    /** 推荐的引用资源目录名 */
    private static final String REFERENCES_DIR = "references";

    /** 推荐的脚本目录名 */
    private static final String SCRIPTS_DIR = "scripts";

    /** Skill 根目录解析器, 默认从 VirtualFile 读取, 测试可注入临时目录 */
    private final Function<SkillFile, Optional<Path>> skillDirectoryResolver;

    /** Markdown 引用提取器, 默认复用 Markdown PSI */
    private final Function<SkillFile, List<SkillReference>> referenceResolver;

    /**
     * 创建默认资源规则
     */
    public ResourceRules() {
        this(skillFile -> skillFile.skillDirectory() == null
                ? Optional.empty()
                : Optional.of(Path.of(skillFile.skillDirectory().getPath()).normalize()),
            MarkdownReferenceParser::parse);
    }

    /**
     * 创建可注入根目录解析器和引用提取器的资源规则
     * <p> 该构造函数主要用于单元测试, 避免为了测试文件系统逻辑而 mock IntelliJ {@code VirtualFile}.
     *
     * @param skillDirectoryResolver Skill 根目录解析器
     * @param referenceResolver      Markdown 引用提取器
     */
    ResourceRules(@NotNull Function<SkillFile, Optional<Path>> skillDirectoryResolver,
                  @NotNull Function<SkillFile, List<SkillReference>> referenceResolver) {
        this.skillDirectoryResolver = skillDirectoryResolver;
        this.referenceResolver = referenceResolver;
    }

    @Override
    @NotNull
    public List<SkillProblem> check(@NotNull SkillFile skillFile) {
        Optional<Path> resolvedSkillDirectory = skillDirectoryResolver.apply(skillFile);
        if (resolvedSkillDirectory.isEmpty()) {
            return List.of();
        }

        Path skillDirectory = resolvedSkillDirectory.get();
        List<SkillProblem> problems = new ArrayList<>();
        List<SkillReference> references = referenceResolver.apply(skillFile);
        Set<String> referencedPaths = collectReferencedPaths(skillDirectory, references);
        TextRange anchor = firstLineRange(skillFile);
        int textLength = skillFile.psiFile().getTextLength();

        checkUnusedReferences(problems, skillDirectory, referencedPaths, anchor, textLength);
        checkScriptsUsage(problems, skillDirectory, referencedPaths, skillFile.body(), anchor, textLength);

        return problems;
    }

    /**
     * 检查 {@code references/} 下未被引用的文件
     */
    private void checkUnusedReferences(@NotNull List<SkillProblem> problems,
                                       @NotNull Path skillDirectory,
                                       @NotNull Set<String> referencedPaths,
                                       @NotNull TextRange anchor,
                                       int textLength) {
        Path referencesDir = skillDirectory.resolve(REFERENCES_DIR);
        if (!Files.isDirectory(referencesDir)) {
            return;
        }
        for (String relative : listRelativeFiles(skillDirectory, referencesDir)) {
            if (!referencedPaths.contains(relative)) {
                problems.add(new SkillProblem(
                    "resource.unused-reference",
                    SkillSeverity.WARNING,
                    SkillInspectorBundle.message("inspection.resource.unused.reference", relative),
                    TextRangeUtil.clamp(anchor, textLength),
                    List.of()
                ));
            }
        }
    }

    /**
     * 检查 {@code scripts/} 下未在正文说明用法的脚本
     */
    private void checkScriptsUsage(@NotNull List<SkillProblem> problems,
                                   @NotNull Path skillDirectory,
                                   @NotNull Set<String> referencedPaths,
                                   @NotNull SkillBody body,
                                   @NotNull TextRange anchor,
                                   int textLength) {
        Path scriptsDir = skillDirectory.resolve(SCRIPTS_DIR);
        if (!Files.isDirectory(scriptsDir)) {
            return;
        }
        String bodyText = body.text();
        for (String relative : listRelativeFiles(skillDirectory, scriptsDir)) {
            String fileName = Path.of(relative).getFileName().toString();
            boolean linked = referencedPaths.contains(relative);
            // 文件名出现在正文中也视为"被提及", 允许 `运行 foo.sh` 这种纯文本说明.
            boolean mentioned = bodyText.contains(fileName);
            if (!linked && !mentioned) {
                problems.add(new SkillProblem(
                    "script.missing-usage",
                    SkillSeverity.WARNING,
                    SkillInspectorBundle.message("inspection.script.missing.usage", relative),
                    TextRangeUtil.clamp(anchor, textLength),
                    List.of()
                ));
            }
        }
    }

    /**
     * 把 Markdown 引用规范化为"相对于 skill 根目录"的 POSIX 风格路径集合
     * <p> 关键约束:
     * <ul>
     *   <li>跳过 URL / 锚点 / 邮箱 / 绝对路径, 这些不会落到本地文件系统</li>
     *   <li>去掉 fragment / query, 因为它们不影响文件存在性</li>
     *   <li>越界路径(normalize 后跳出 skill 目录) 由 ReferenceRules 报错, 这里只是过滤掉, 不重复报</li>
     * </ul>
     */
    @NotNull
    private Set<String> collectReferencedPaths(@NotNull Path skillDirectory,
                                               @NotNull List<SkillReference> references) {
        Set<String> referenced = new HashSet<>();
        for (SkillReference reference : references) {
            String target = stripFragment(reference.target());
            if (shouldSkip(target)) {
                continue;
            }
            try {
                Path resolved = skillDirectory.resolve(target).normalize();
                if (!resolved.startsWith(skillDirectory)) {
                    continue;
                }
                Path relative = skillDirectory.relativize(resolved);
                referenced.add(toPosix(relative));
            } catch (InvalidPathException ignored) {
                // 非法路径在 ReferenceRules 已报错, 这里忽略.
            }
        }
        return referenced;
    }

    /**
     * 列出指定目录下所有常规文件相对于 skill 根目录的 POSIX 风格路径
     */
    @NotNull
    private List<String> listRelativeFiles(@NotNull Path skillDirectory, @NotNull Path subDirectory) {
        List<String> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(subDirectory)) {
            stream.filter(Files::isRegularFile)
                .forEach(file -> result.add(toPosix(skillDirectory.relativize(file))));
        } catch (IOException ignored) {
            // 读取目录失败时静默跳过, 不阻塞其他规则.
        }
        result.sort(String::compareTo);
        return result;
    }

    /**
     * 选取 SKILL.md 第一行作为问题锚点
     * <p> 资源孤儿问题没有对应的正文片段, 选择第一行是为了让 Problems 面板能聚焦到 SKILL.md 顶部, 帮助作者快速定位文件.
     */
    @NotNull
    private TextRange firstLineRange(@NotNull SkillFile skillFile) {
        String text = skillFile.psiFile().getText();
        if (text == null || text.isEmpty()) {
            return TextRange.EMPTY_RANGE;
        }
        int newLineIndex = text.indexOf('\n');
        int end = newLineIndex < 0 ? text.length() : newLineIndex;
        return TextRange.create(0, Math.max(end, 1));
    }

    /**
     * 去掉 anchor 和 query, 只保留本地文件路径部分
     */
    @NotNull
    private String stripFragment(@NotNull String target) {
        int cut = target.length();
        int fragment = target.indexOf('#');
        int query = target.indexOf('?');
        if (fragment >= 0) {
            cut = Math.min(cut, fragment);
        }
        if (query >= 0) {
            cut = Math.min(cut, query);
        }
        return target.substring(0, cut);
    }

    /**
     * 跳过非本地文件引用
     */
    private boolean shouldSkip(@NotNull String target) {
        if (target.isBlank()) {
            return true;
        }
        String lower = target.toLowerCase(Locale.ROOT);
        return target.startsWith("#")
            || target.startsWith("/")
            || lower.startsWith("http://")
            || lower.startsWith("https://")
            || lower.startsWith("mailto:");
    }

    /**
     * 把 {@link Path} 转成 POSIX 风格字符串, 让 Windows / macOS / Linux 三端用统一表达
     */
    @NotNull
    private String toPosix(@NotNull Path path) {
        return path.toString().replace('\\', '/');
    }
}
