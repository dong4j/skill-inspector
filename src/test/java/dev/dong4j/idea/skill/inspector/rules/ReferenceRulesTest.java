package dev.dong4j.idea.skill.inspector.rules;

import com.intellij.openapi.util.TextRange;

import dev.dong4j.idea.skill.inspector.model.SkillProblem;
import dev.dong4j.idea.skill.inspector.model.SkillReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static dev.dong4j.idea.skill.inspector.test.SkillFileTestFactory.skillFile;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ReferenceRules} 单元测试
 * <p> 使用临时目录模拟真实 skill 目录, 覆盖缺失文件、越界路径、大小写错误和 URL 跳过逻辑.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
class ReferenceRulesTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldReportMissingReferenceFile() {
        String text = validSkillBody("See [missing](references/missing.md).");

        List<SkillProblem> problems = rules(reference("references/missing.md")).check(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .containsExactly("reference.missing-file");
    }

    @Test
    void shouldReportOutsideSkillReference() {
        String text = validSkillBody("See [outside](../outside.md).");

        List<SkillProblem> problems = rules(reference("../outside.md")).check(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .containsExactly("reference.outside-skill");
    }

    @Test
    void shouldAcceptExistingReferenceAndSkipUrl() throws IOException {
        Files.createDirectories(tempDir.resolve("references"));
        Files.writeString(tempDir.resolve("references/guide.md"), "# Guide\n");
        String text = validSkillBody("See [guide](references/guide.md) and [site](https://example.com).");

        List<SkillProblem> problems = rules(reference("references/guide.md"), reference("https://example.com")).check(skillFile(text, "my-skill"));

        assertThat(problems).isEmpty();
    }

    @Test
    void shouldReportCaseMismatchWhenFileExistsWithDifferentCase() throws IOException {
        Files.createDirectories(tempDir.resolve("references"));
        Files.writeString(tempDir.resolve("references/Guide.md"), "# Guide\n");
        String text = validSkillBody("See [guide](references/guide.md).");

        List<SkillProblem> problems = rules(reference("references/guide.md")).check(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .contains("reference.case-mismatch");
    }

    /**
     * 创建有效 Skill 文本, 让 ReferenceRules 测试只聚焦引用逻辑.
     */
    private String validSkillBody(String body) {
        return """
            ---
            name: my-skill
            description: Use this skill when validating markdown references.
            ---
            # My Skill

            Use this skill when reviewing references.
            %s
            """.formatted(body);
    }

    /**
     * 创建使用临时目录作为 skill 根目录的引用规则
     */
    private ReferenceRules rules(SkillReference... references) {
        return new ReferenceRules(skillFile -> Optional.of(tempDir), skillFile -> List.of(references));
    }

    /**
     * 创建规则层引用模型, 让 ReferenceRules 测试聚焦文件系统校验而不是 Markdown PSI 构造。
     */
    private SkillReference reference(String target) {
        return new SkillReference(target, TextRange.create(0, target.length()));
    }
}
