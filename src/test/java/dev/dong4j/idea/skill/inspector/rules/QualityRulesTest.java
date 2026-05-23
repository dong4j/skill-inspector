package dev.dong4j.idea.skill.inspector.rules;

import dev.dong4j.idea.skill.inspector.model.SkillProblem;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.dong4j.idea.skill.inspector.test.SkillFileTestFactory.skillFile;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QualityRules} 单元测试
 * <p> 质量规则是建议性提示, 测试重点是避免误报有效正文, 并覆盖短描述、泛描述和正文触发说明缺失.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
class QualityRulesTest {

    private final QualityRules rules = new QualityRules();

    @Test
    void shouldReportShortAndGenericDescription() {
        String text = """
            ---
            name: helper-skill
            description: helper
            ---
            # Helper

            Use this skill when testing.
            """;

        List<SkillProblem> problems = rules.check(skillFile(text, "helper-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .contains("description.too-short", "description.too-generic");
    }

    @Test
    void shouldReportDescriptionWithoutUsageGuidance() {
        String text = """
            ---
            name: my-skill
            description: Validates agent skill files and checks their frontmatter.
            ---
            # My Skill

            Validation instructions.
            """;

        List<SkillProblem> problems = rules.check(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .contains("description.missing-usage");
    }

    @Test
    void shouldReportMissingBodyTitleAndTrigger() {
        String text = """
            ---
            name: my-skill
            description: Validate agent skill files.
            ---
            This body has no heading or trigger phrase.
            """;

        List<SkillProblem> problems = rules.check(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .contains("body.missing-title", "body.missing-trigger");
    }

    @Test
    void shouldAcceptUsefulDescriptionAndTrigger() {
        String text = """
            ---
            name: my-skill
            description: Use this skill when validating agent skill files.
            ---
            # My Skill

            Use this skill when reviewing a SKILL.md file.
            """;

        List<SkillProblem> problems = rules.check(skillFile(text, "my-skill"));

        assertThat(problems).isEmpty();
    }

    @Test
    void shouldAcceptTriggerDefinedInDescription() {
        String text = """
            ---
            name: brand-guidelines
            description: Applies Anthropic's official brand colors and typography to any sort of artifact that may benefit from having Anthropic's look-and-feel. Use it when brand colors or style guidelines, visual formatting, or company design standards apply.
            license: Complete terms in LICENSE.txt
            ---
            # Anthropic Brand Styling

            ## Overview
            Brand styling guidance.
            """;

        List<SkillProblem> problems = rules.check(skillFile(text, "brand-guidelines"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .doesNotContain("body.missing-title", "body.missing-trigger");
    }
}
