package dev.dong4j.idea.skill.inspector.rules;

import com.intellij.openapi.util.TextRange;

import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillFixType;
import dev.dong4j.idea.skill.inspector.model.SkillProblem;
import dev.dong4j.idea.skill.inspector.model.SkillReference;
import dev.dong4j.idea.skill.inspector.model.SkillSeverity;
import dev.dong4j.idea.skill.inspector.parser.MarkdownReferenceParser;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;
import dev.dong4j.idea.skill.inspector.util.TextRangeUtil;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Markdown 引用规则
 * <p> 检查 {@code SKILL.md} 中的本地相对链接是否仍留在当前 skill 目录内,
 * 且目标文件是否存在. 这是 skill 迁移和协作编辑中最容易失效的确定性问题之一.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public class ReferenceRules implements SkillRule {

    /** Skill 根目录解析器, 默认从 VirtualFile 读取, 测试可注入临时目录 */
    private final Function<SkillFile, Optional<Path>> skillDirectoryResolver;

    /** Markdown 引用提取器, 默认复用 Markdown PSI */
    private final Function<SkillFile, List<SkillReference>> referenceResolver;

    /**
     * 创建默认引用规则
     */
    public ReferenceRules() {
        this(skillFile -> skillFile.skillDirectory() == null
            ? Optional.empty()
            : Optional.of(Path.of(skillFile.skillDirectory().getPath()).normalize()),
            MarkdownReferenceParser::parse);
    }

    /**
     * 创建可注入根目录解析器的引用规则
     * <p> 该构造函数主要用于单元测试, 避免为了测试文件系统逻辑而 mock IntelliJ {@code VirtualFile}.
     *
     * @param skillDirectoryResolver Skill 根目录解析器
     */
    ReferenceRules(@NotNull Function<SkillFile, Optional<Path>> skillDirectoryResolver) {
        this(skillDirectoryResolver, MarkdownReferenceParser::parse);
    }

    /**
     * 创建可注入根目录解析器和引用提取器的引用规则
     * <p> 生产代码使用 Markdown PSI 提取引用; 单元测试可直接注入引用列表, 将文件系统规则与 PSI 解析解耦。
     *
     * @param skillDirectoryResolver Skill 根目录解析器
     * @param referenceResolver      Markdown 引用提取器
     */
    ReferenceRules(@NotNull Function<SkillFile, Optional<Path>> skillDirectoryResolver,
                   @NotNull Function<SkillFile, List<SkillReference>> referenceResolver) {
        this.skillDirectoryResolver = skillDirectoryResolver;
        this.referenceResolver = referenceResolver;
    }

    @Override
    @NotNull
    public List<SkillProblem> check(@NotNull SkillFile skillFile) {
        List<SkillProblem> problems = new ArrayList<>();
        Optional<Path> resolvedSkillDirectory = skillDirectoryResolver.apply(skillFile);
        if (resolvedSkillDirectory.isEmpty()) {
            return problems;
        }

        int textLength = skillFile.psiFile().getTextLength();
        Path skillDirectory = resolvedSkillDirectory.get().normalize();
        for (SkillReference reference : referenceResolver.apply(skillFile)) {
            checkReference(problems, textLength, skillDirectory, reference);
        }
        return problems;
    }

    /**
     * 检查单个 Markdown 引用
     */
    private void checkReference(@NotNull List<SkillProblem> problems,
                                int textLength,
                                @NotNull Path skillDirectory,
                                @NotNull SkillReference reference) {
        String target = stripFragment(reference.target());
        if (shouldSkip(target)) {
            return;
        }

        Path resolved;
        try {
            resolved = skillDirectory.resolve(target).normalize();
        } catch (InvalidPathException ignored) {
            problems.add(problem(
                "reference.invalid-path",
                SkillSeverity.WARNING,
                SkillInspectorBundle.message("inspection.reference.invalid.path"),
                reference.targetRange(),
                textLength
            ));
            return;
        }

        if (!resolved.startsWith(skillDirectory)) {
            problems.add(problem(
                "reference.outside-skill",
                SkillSeverity.WARNING,
                SkillInspectorBundle.message("inspection.reference.outside.skill"),
                reference.targetRange(),
                textLength
            ));
            return;
        }

        if (!Files.exists(resolved)) {
            problems.add(new SkillProblem(
                "reference.missing-file",
                SkillSeverity.WARNING,
                SkillInspectorBundle.message("inspection.reference.missing.file", target),
                TextRangeUtil.clamp(reference.targetRange(), textLength),
                List.of(SkillFixType.CREATE_MISSING_REFERENCE)
            ));
            return;
        }

        if (hasCaseMismatch(skillDirectory, target)) {
            problems.add(problem(
                "reference.case-mismatch",
                SkillSeverity.WARNING,
                SkillInspectorBundle.message("inspection.reference.case.mismatch"),
                reference.targetRange(),
                textLength
            ));
        }
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
        String lowerTarget = target.toLowerCase(Locale.ROOT);
        return target.isBlank()
            || target.startsWith("#")
            || target.startsWith("/")
            || lowerTarget.startsWith("http://")
            || lowerTarget.startsWith("https://")
            || lowerTarget.startsWith("mailto:");
    }

    /**
     * 检查路径大小写是否与文件系统目录项一致
     * <p> macOS 默认大小写不敏感, {@link Files#exists(Path)} 可能在大小写错误时仍返回 true.
     * 因此逐级读取目录项进行精确比较, 捕获迁移到 Linux 后才暴露的问题.
     */
    private boolean hasCaseMismatch(@NotNull Path skillDirectory, @NotNull String target) {
        Path current = skillDirectory;
        Path relative;
        try {
            relative = Path.of(target);
        } catch (InvalidPathException ignored) {
            return false;
        }

        for (Path segment : relative) {
            String segmentName = segment.toString();
            if (segmentName.equals(".") || segmentName.equals("..")) {
                current = current.resolve(segmentName).normalize();
                continue;
            }
            if (!containsExactChild(current, segmentName)) {
                return true;
            }
            current = current.resolve(segmentName);
        }
        return false;
    }

    /**
     * 判断目录中是否存在同名同大小写的子项
     */
    private boolean containsExactChild(@NotNull Path directory, @NotNull String childName) {
        if (!Files.isDirectory(directory)) {
            return true;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                Path fileName = child.getFileName();
                if (fileName != null && fileName.toString().equals(childName)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            return true;
        }
        return false;
    }

    /**
     * 创建引用问题
     */
    @NotNull
    private SkillProblem problem(@NotNull String ruleId,
                                 @NotNull SkillSeverity severity,
                                 @NotNull String message,
                                 @NotNull TextRange range,
                                 int textLength) {
        return new SkillProblem(ruleId, severity, message, TextRangeUtil.clamp(range, textLength), List.of());
    }
}
