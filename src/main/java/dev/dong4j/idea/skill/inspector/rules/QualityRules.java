package dev.dong4j.idea.skill.inspector.rules;

import com.intellij.openapi.util.TextRange;

import dev.dong4j.idea.skill.inspector.model.SkillBody;
import dev.dong4j.idea.skill.inspector.model.SkillFile;
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
import java.util.Locale;
import java.util.Set;

/**
 * Skill 内容质量规则
 * <p> 质量规则不阻塞 skill 被识别, 只给出 authoring 过程中的改进建议.
 * 这些规则默认使用 Warning 或 Weak Warning, 避免对用户正文写作造成强干扰.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public class QualityRules implements SkillRule {

    /** description 低于该长度通常无法帮助 Agent 做发现 */
    private static final int MIN_DESCRIPTION_LENGTH = 20;

    /** 主 SKILL.md 超过该长度时建议拆分到 references/ */
    private static final int MAX_BODY_LENGTH = 12_000;

    /** 过泛描述词 */
    private static final Set<String> GENERIC_DESCRIPTIONS = Set.of("helper", "tool", "skill", "assistant", "utility");

    @Override
    @NotNull
    public List<SkillProblem> check(@NotNull SkillFile skillFile) {
        List<SkillProblem> problems = new ArrayList<>();
        SkillFrontMatter frontMatter = skillFile.frontMatter();
        int textLength = skillFile.psiFile().getTextLength();

        if (frontMatter != null && frontMatter.parseError() == null) {
            checkDescriptionQuality(problems, textLength, frontMatter.metadata());
        }
        checkBodyQuality(problems, textLength, frontMatter, skillFile.body());
        return problems;
    }

    /**
     * 检查 description 是否过短或过泛
     */
    private void checkDescriptionQuality(@NotNull List<SkillProblem> problems,
                                         int textLength,
                                         @NotNull SkillMetadata metadata) {
        SkillYamlEntry descriptionEntry = metadata.description();
        if (descriptionEntry == null || descriptionEntry.value().isBlank()) {
            return;
        }

        TextRange range = TextRangeUtil.clamp(descriptionEntry.valueRange(), textLength);
        String description = descriptionEntry.value().trim();
        if (description.length() < MIN_DESCRIPTION_LENGTH) {
            problems.add(new SkillProblem(
                "description.too-short",
                SkillSeverity.WARNING,
                SkillInspectorBundle.message("inspection.description.too.short"),
                range,
                List.of()
            ));
        }

        if (GENERIC_DESCRIPTIONS.contains(description.toLowerCase(Locale.ROOT))) {
            problems.add(new SkillProblem(
                "description.too-generic",
                SkillSeverity.WARNING,
                SkillInspectorBundle.message("inspection.description.too.generic"),
                range,
                List.of()
            ));
        }

        if (!containsUsageTrigger(description)) {
            problems.add(new SkillProblem(
                "description.missing-usage",
                SkillSeverity.WARNING,
                SkillInspectorBundle.message("inspection.description.missing.usage"),
                range,
                List.of()
            ));
        }
    }

    /**
     * 检查 Markdown 正文质量
     */
    private void checkBodyQuality(@NotNull List<SkillProblem> problems,
                                  int textLength,
                                  SkillFrontMatter frontMatter,
                                  @NotNull SkillBody body) {
        String bodyText = body.text();
        TextRange bodyStart = TextRangeUtil.clamp(TextRange.create(body.startOffset(), Math.min(body.startOffset() + 1, body.endOffset())), textLength);

        if (!bodyText.stripLeading().startsWith("# ")) {
            problems.add(new SkillProblem(
                "body.missing-title",
                SkillSeverity.WEAK_WARNING,
                SkillInspectorBundle.message("inspection.body.missing.title"),
                bodyStart,
                List.of()
            ));
        }

        if (!hasTriggerText(frontMatter, bodyText)) {
            problems.add(new SkillProblem(
                "body.missing-trigger",
                SkillSeverity.WARNING,
                SkillInspectorBundle.message("inspection.body.missing.trigger"),
                bodyStart,
                List.of()
            ));
        }

        if (bodyText.length() > MAX_BODY_LENGTH) {
            problems.add(new SkillProblem(
                "body.too-long",
                SkillSeverity.WARNING,
                SkillInspectorBundle.message("inspection.body.too.long", MAX_BODY_LENGTH),
                bodyStart,
                List.of()
            ));
        }
    }

    /**
     * 判断 skill 是否已经说明使用时机
     * <p> 官方 Agent Skill 通常把触发条件写在 frontmatter description 中, 例如
     * {@code Use it when ...}. 因此此规则不能只看正文, 否则会对官方示例产生误报.
     */
    private boolean hasTriggerText(SkillFrontMatter frontMatter, @NotNull String bodyText) {
        StringBuilder searchableText = new StringBuilder(bodyText);
        if (frontMatter != null && frontMatter.parseError() == null) {
            SkillYamlEntry descriptionEntry = frontMatter.metadata().description();
            if (descriptionEntry != null) {
                searchableText.append('\n').append(descriptionEntry.value());
            }
        }

        String lowerText = searchableText.toString().toLowerCase(Locale.ROOT);
        return containsUsageTrigger(lowerText) || searchableText.toString().contains("使用") || searchableText.toString().contains("适用");
    }

    /**
     * 判断描述中是否包含明确的使用时机说明
     */
    private boolean containsUsageTrigger(@NotNull String text) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        return lowerText.contains("use this skill when")
            || lowerText.contains("use it when")
            || lowerText.contains("use when")
            || lowerText.contains("when to use")
            || lowerText.contains("适用")
            || lowerText.contains("使用");
    }
}
