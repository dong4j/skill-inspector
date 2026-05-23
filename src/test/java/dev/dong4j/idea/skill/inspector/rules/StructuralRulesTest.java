package dev.dong4j.idea.skill.inspector.rules;

import dev.dong4j.idea.skill.inspector.model.SkillFixType;
import dev.dong4j.idea.skill.inspector.model.SkillProblem;
import dev.dong4j.idea.skill.inspector.model.SkillSeverity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.dong4j.idea.skill.inspector.test.SkillFileTestFactory.skillFile;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StructuralRules} 单元测试
 * <p> 验证 specification 中最确定的结构规则, 这些规则会直接影响 skill 是否可被识别.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
class StructuralRulesTest {

    private final StructuralRules rules = new StructuralRules();

    @Test
    void shouldReportMissingFrontMatterWithAddFix() {
        List<SkillProblem> problems = rules.check(skillFile("# Skill\n", "my-skill"));

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.ruleId()).isEqualTo("frontmatter.missing");
            assertThat(problem.severity()).isEqualTo(SkillSeverity.ERROR);
            assertThat(problem.fixTypes()).containsExactly(SkillFixType.ADD_FRONT_MATTER);
        });
    }

    @Test
    void shouldReportMissingRequiredFields() {
        String text = """
            ---
            allowed-tools: Read
            ---
            # Skill
            """;

        List<SkillProblem> problems = rules.check(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .containsExactly("frontmatter.name.missing", "frontmatter.description.missing");
    }

    @Test
    void shouldReportInvalidAndMismatchedName() {
        String text = """
            ---
            name: My Skill
            description: Use this skill when validating skill files.
            ---
            # Skill
            """;

        List<SkillProblem> problems = rules.check(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .contains("frontmatter.name.invalid", "frontmatter.name.mismatch");
        assertThat(problems)
            .filteredOn(problem -> problem.ruleId().equals("frontmatter.name.mismatch"))
            .singleElement()
            .satisfies(problem -> assertThat(problem.fixTypes()).containsExactly(SkillFixType.SYNC_NAME_WITH_DIRECTORY));
    }

    @Test
    void shouldReportNameLongerThanSpecificationLimit() {
        String longName = "a".repeat(65);
        String text = """
            ---
            name: %s
            description: Use this skill when validating skill files.
            ---
            # Skill
            """.formatted(longName);

        List<SkillProblem> problems = rules.check(skillFile(text, longName));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .contains("frontmatter.name.too-long");
    }

    @Test
    void shouldReportDescriptionLongerThanSpecificationLimit() {
        String longDescription = "Use this skill when validating agent skill files. " + "a".repeat(1_024);
        String text = """
            ---
            name: my-skill
            description: %s
            ---
            # Skill
            """.formatted(longDescription);

        List<SkillProblem> problems = rules.check(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .contains("frontmatter.description.too-long");
    }

    @Test
    void shouldReportDescriptionBlockScalarLongerThanSpecificationLimit() {
        String longDescription = "Use this skill when validating agent skill files. " + "a".repeat(1_024);
        String text = """
            ---
            name: my-skill
            description: >
              %s
            ---
            # Skill
            """.formatted(longDescription);

        List<SkillProblem> problems = rules.check(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .contains("frontmatter.description.too-long");
    }

    @Test
    void shouldReportInvalidCompatibilityLengthWhenProvided() {
        String text = """
            ---
            name: my-skill
            description: Use this skill when validating skill files.
            compatibility:
            ---
            # Skill
            """;

        List<SkillProblem> emptyCompatibilityProblems = rules.check(skillFile(text, "my-skill"));

        assertThat(emptyCompatibilityProblems)
            .extracting(SkillProblem::ruleId)
            .contains("frontmatter.compatibility.empty");

        String longCompatibility = "Requires Java and network access. " + "a".repeat(500);
        String longText = """
            ---
            name: my-skill
            description: Use this skill when validating skill files.
            compatibility: %s
            ---
            # Skill
            """.formatted(longCompatibility);

        List<SkillProblem> longCompatibilityProblems = rules.check(skillFile(longText, "my-skill"));

        assertThat(longCompatibilityProblems)
            .extracting(SkillProblem::ruleId)
            .contains("frontmatter.compatibility.too-long");
    }

    @Test
    void shouldAcceptValidStructure() {
        String text = """
            ---
            name: my-skill
            description: Use this skill when validating skill files.
            ---
            # My Skill
            """;

        List<SkillProblem> problems = rules.check(skillFile(text, "my-skill"));

        assertThat(problems).isEmpty();
    }
}
