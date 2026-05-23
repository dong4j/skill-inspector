package dev.dong4j.idea.skill.inspector.rules;

import dev.dong4j.idea.skill.inspector.model.SkillProblem;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.dong4j.idea.skill.inspector.test.SkillFileTestFactory.skillFile;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RuleRunner} 单元测试
 * <p> 验证默认规则集合已接入关键规则, 避免后续重构时遗漏某一类检查.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
class RuleRunnerTest {

    @Test
    void shouldRunDefaultRuleSet() {
        String text = """
            ---
            name: Wrong Name
            description: helper
            allowed-tools: Bash
            ---
            Body without heading.
            """;

        List<SkillProblem> problems = new RuleRunner().run(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .contains(
                "frontmatter.name.invalid",
                "frontmatter.name.mismatch",
                "description.too-short",
                "description.too-generic",
                "body.missing-title",
                "body.missing-trigger",
                "security.allowed-tools-bash"
            );
    }

    @Test
    void shouldAcceptOfficialBrandGuidelinesShape() {
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

        List<SkillProblem> problems = new RuleRunner().run(skillFile(text, "brand-guidelines"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .doesNotContain(
                "frontmatter.missing",
                "frontmatter.name.missing",
                "frontmatter.description.missing",
                "body.missing-title",
                "body.missing-trigger"
            );
    }

    @Test
    void shouldReportSpecificationViolationsThroughDefaultRuleSet() {
        String longDescription = "Use this skill when validating agent skill files. " + "a".repeat(6_000);
        String text = """
            ---
            name: my-skill
            description: %s
            ---
            # My Skill
            """.formatted(longDescription);

        List<SkillProblem> problems = new RuleRunner().run(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .contains("frontmatter.description.too-long");
    }
}
