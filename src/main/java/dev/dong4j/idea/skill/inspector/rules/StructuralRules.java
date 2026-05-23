package dev.dong4j.idea.skill.inspector.rules;

import com.intellij.openapi.util.TextRange;

import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillFixType;
import dev.dong4j.idea.skill.inspector.model.SkillFrontMatter;
import dev.dong4j.idea.skill.inspector.model.SkillMetadata;
import dev.dong4j.idea.skill.inspector.model.SkillProblem;
import dev.dong4j.idea.skill.inspector.model.SkillSeverity;
import dev.dong4j.idea.skill.inspector.model.SkillYamlEntry;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;
import dev.dong4j.idea.skill.inspector.util.TextRangeUtil;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Skill 结构规则
 * <p> 覆盖 Agent Skills specification 中确定性最高的约束: frontmatter 必须存在,
 * {@code name}/{@code description} 必须存在, {@code name} 必须符合命名规范并匹配父目录名.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public class StructuralRules implements SkillRule {

    /** specification 规定 name 必须为 1-64 个字符 */
    private static final int MAX_NAME_LENGTH = 64;

    /** specification 规定 description 必须为 1-1024 个字符 */
    private static final int MAX_DESCRIPTION_LENGTH = 1024;

    /** specification 规定 compatibility 如果提供, 必须为 1-500 个字符 */
    private static final int MAX_COMPATIBILITY_LENGTH = 500;

    /** Agent Skill 名称格式: 小写字母、数字和连字符, 且不能以连字符开头或结尾 */
    private static final Pattern KEBAB_CASE = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    @Override
    @NotNull
    public List<SkillProblem> check(@NotNull SkillFile skillFile) {
        List<SkillProblem> problems = new ArrayList<>();
        int textLength = skillFile.psiFile().getTextLength();

        // 目录名规则独立于 frontmatter, 即便缺少 YAML 头部也应能提示父目录命名问题.
        checkDirectoryName(skillFile, problems, textLength);

        SkillFrontMatter frontMatter = skillFile.frontMatter();
        if (frontMatter == null) {
            problems.add(new SkillProblem(
                "frontmatter.missing",
                SkillSeverity.ERROR,
                SkillInspectorBundle.message("inspection.frontmatter.missing"),
                TextRangeUtil.fileStart(textLength),
                List.of(SkillFixType.ADD_FRONT_MATTER)
            ));
            return problems;
        }

        TextRange frontMatterRange = TextRangeUtil.clamp(TextRange.create(frontMatter.startOffset(), frontMatter.endOffset()), textLength);
        if (frontMatter.parseError() != null) {
            problems.add(new SkillProblem(
                "frontmatter.invalid-yaml",
                SkillSeverity.ERROR,
                SkillInspectorBundle.message("inspection.frontmatter.invalid", frontMatter.parseError()),
                frontMatterRange,
                List.of()
            ));
            return problems;
        }

        SkillMetadata metadata = frontMatter.metadata();
        checkName(skillFile, problems, textLength, metadata, frontMatterRange);
        checkDescription(problems, textLength, frontMatterRange, metadata);
        checkCompatibility(problems, textLength, metadata);
        return problems;
    }

    /**
     * 校验父目录名本身是否符合 kebab-case
     * <p> 与 {@code frontmatter.name.mismatch} 互补: 即便 frontmatter 缺失或父目录名与 name 一致,
     * 也要单独提示作者把目录名改成规范格式, 避免将来重命名时出现"按规范改了 name 反而对不上目录"的死循环.
     *
     * @param skillFile  Skill 上下文
     * @param problems   问题累积列表
     * @param textLength 文本长度, 用于在文件开头创建安全范围
     */
    private void checkDirectoryName(@NotNull SkillFile skillFile,
                                    @NotNull List<SkillProblem> problems,
                                    int textLength) {
        String dirName = skillFile.skillDirectoryName();
        if (dirName.isBlank()) {
            return;
        }
        if (!KEBAB_CASE.matcher(dirName).matches()) {
            problems.add(new SkillProblem(
                "skill.directory.name",
                SkillSeverity.ERROR,
                SkillInspectorBundle.message("inspection.skill.directory.name.invalid", dirName),
                TextRangeUtil.fileStart(textLength),
                List.of()
            ));
        }
    }

    /**
     * 校验 name 字段
     */
    private void checkName(@NotNull SkillFile skillFile,
                           @NotNull List<SkillProblem> problems,
                           int textLength,
                           @NotNull SkillMetadata metadata,
                           @NotNull TextRange frontMatterRange) {
        SkillYamlEntry nameEntry = metadata.name();
        if (nameEntry == null || nameEntry.value().isBlank()) {
            problems.add(new SkillProblem(
                "frontmatter.name.missing",
                SkillSeverity.ERROR,
                SkillInspectorBundle.message("inspection.frontmatter.name.missing"),
                frontMatterRange,
                List.of(SkillFixType.ADD_NAME)
            ));
            return;
        }

        TextRange nameRange = TextRangeUtil.clamp(nameEntry.valueRange(), textLength);
        if (nameEntry.value().length() > MAX_NAME_LENGTH) {
            problems.add(new SkillProblem(
                "frontmatter.name.too-long",
                SkillSeverity.ERROR,
                SkillInspectorBundle.message("inspection.frontmatter.name.too.long", MAX_NAME_LENGTH),
                nameRange,
                List.of()
            ));
        }

        if (!KEBAB_CASE.matcher(nameEntry.value()).matches()) {
            problems.add(new SkillProblem(
                "frontmatter.name.invalid",
                SkillSeverity.ERROR,
                SkillInspectorBundle.message("inspection.frontmatter.name.invalid"),
                nameRange,
                List.of(SkillFixType.CONVERT_NAME_TO_KEBAB)
            ));
        }

        if (!skillFile.skillDirectoryName().isBlank() && !nameEntry.value().equals(skillFile.skillDirectoryName())) {
            problems.add(new SkillProblem(
                "frontmatter.name.mismatch",
                SkillSeverity.ERROR,
                SkillInspectorBundle.message("inspection.frontmatter.name.mismatch", skillFile.skillDirectoryName()),
                nameRange,
                List.of(SkillFixType.SYNC_NAME_WITH_DIRECTORY)
            ));
        }
    }

    /**
     * 校验 description 字段
     */
    private void checkDescription(@NotNull List<SkillProblem> problems,
                                  int textLength,
                                  @NotNull TextRange frontMatterRange,
                                  @NotNull SkillMetadata metadata) {
        SkillYamlEntry descriptionEntry = metadata.description();
        if (descriptionEntry == null || descriptionEntry.value().isBlank()) {
            problems.add(new SkillProblem(
                "frontmatter.description.missing",
                SkillSeverity.ERROR,
                SkillInspectorBundle.message("inspection.frontmatter.description.missing"),
                frontMatterRange,
                List.of(SkillFixType.ADD_DESCRIPTION)
            ));
            return;
        }

        TextRange descriptionRange = TextRangeUtil.clamp(descriptionEntry.valueRange(), textLength);
        if (descriptionEntry.value().length() > MAX_DESCRIPTION_LENGTH) {
            problems.add(new SkillProblem(
                "frontmatter.description.too-long",
                SkillSeverity.ERROR,
                SkillInspectorBundle.message("inspection.frontmatter.description.too.long", MAX_DESCRIPTION_LENGTH),
                descriptionRange,
                List.of()
            ));
        }
    }

    /**
     * 校验 compatibility 字段
     */
    private void checkCompatibility(@NotNull List<SkillProblem> problems,
                                    int textLength,
                                    @NotNull SkillMetadata metadata) {
        SkillYamlEntry compatibilityEntry = metadata.compatibility();
        if (compatibilityEntry == null) {
            return;
        }

        TextRange compatibilityRange = TextRangeUtil.clamp(compatibilityEntry.valueRange(), textLength);
        if (compatibilityEntry.value().isBlank()) {
            problems.add(new SkillProblem(
                "frontmatter.compatibility.empty",
                SkillSeverity.ERROR,
                SkillInspectorBundle.message("inspection.frontmatter.compatibility.empty"),
                compatibilityRange,
                List.of()
            ));
            return;
        }

        if (compatibilityEntry.value().length() > MAX_COMPATIBILITY_LENGTH) {
            problems.add(new SkillProblem(
                "frontmatter.compatibility.too-long",
                SkillSeverity.ERROR,
                SkillInspectorBundle.message("inspection.frontmatter.compatibility.too.long", MAX_COMPATIBILITY_LENGTH),
                compatibilityRange,
                List.of()
            ));
        }
    }
}
