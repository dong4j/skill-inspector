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
 * {@link ResourceRules} 单元测试
 * <p> 用临时目录模拟 skill 根目录, 覆盖以下场景:
 * <ul>
 *   <li>{@code references/} 下未被引用的文件 → {@code resource.unused-reference}</li>
 *   <li>{@code scripts/} 下既无链接也无文本提及 → {@code script.missing-usage}</li>
 *   <li>{@code references/} 不存在 → 静默跳过</li>
 *   <li>引用走的是 fragment / 绝对路径 / URL 时不应算作"被引用"</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
class ResourceRulesTest {

    @TempDir
    private Path tempDir;

    @Test
    void shouldReportUnusedReferenceFile() throws IOException {
        Files.createDirectories(tempDir.resolve("references"));
        Files.writeString(tempDir.resolve("references/used.md"), "# Used\n");
        Files.writeString(tempDir.resolve("references/orphan.md"), "# Orphan\n");

        String text = validSkillBody("Reads from [used](references/used.md).");
        List<SkillProblem> problems = rules(reference("references/used.md"))
            .check(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId, SkillProblem::message)
            .containsExactly(tuple("resource.unused-reference", "references/orphan.md"));
    }

    @Test
    void shouldNotReportWhenAllReferencesUsed() throws IOException {
        Files.createDirectories(tempDir.resolve("references"));
        Files.writeString(tempDir.resolve("references/guide.md"), "# Guide\n");

        String text = validSkillBody("See [guide](references/guide.md).");
        List<SkillProblem> problems = rules(reference("references/guide.md"))
            .check(skillFile(text, "my-skill"));

        assertThat(problems).isEmpty();
    }

    @Test
    void shouldReportScriptMissingUsage() throws IOException {
        Files.createDirectories(tempDir.resolve("scripts"));
        Files.writeString(tempDir.resolve("scripts/lonely.sh"), "#!/bin/bash\n");
        Files.writeString(tempDir.resolve("scripts/known.sh"), "#!/bin/bash\n");

        // 正文里提到 known.sh 文件名, 视为已说明用法; lonely.sh 完全没出现, 应报告.
        String text = validSkillBody("Run known.sh after editing.");
        List<SkillProblem> problems = rules().check(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId, SkillProblem::message)
            .containsExactly(tuple("script.missing-usage", "scripts/lonely.sh"));
    }

    @Test
    void shouldTreatScriptAsUsedWhenLinkedFromBody() throws IOException {
        Files.createDirectories(tempDir.resolve("scripts"));
        Files.writeString(tempDir.resolve("scripts/run.sh"), "#!/bin/bash\n");

        String text = validSkillBody("Click [run](scripts/run.sh) to start.");
        List<SkillProblem> problems = rules(reference("scripts/run.sh"))
            .check(skillFile(text, "my-skill"));

        assertThat(problems).isEmpty();
    }

    @Test
    void shouldSkipWhenReferencesAndScriptsDirectoriesMissing() {
        String text = validSkillBody("No resources at all.");

        List<SkillProblem> problems = rules().check(skillFile(text, "my-skill"));

        assertThat(problems).isEmpty();
    }

    @Test
    void shouldIgnoreUrlAndAnchorReferencesWhenLookingForUsage() throws IOException {
        Files.createDirectories(tempDir.resolve("references"));
        Files.writeString(tempDir.resolve("references/guide.md"), "# Guide\n");

        // URL / 锚点 / 邮箱 不能视为"引用了 references/guide.md", 因此 guide.md 仍应报为未引用.
        String text = validSkillBody("See [home](#intro) or [site](https://example.com).");
        List<SkillProblem> problems = rules(
            reference("https://example.com"),
            reference("#intro"),
            reference("mailto:dong4j@gmail.com")
        ).check(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .containsExactly("resource.unused-reference");
    }

    @Test
    void shouldDetectUnusedFilesInNestedReferences() throws IOException {
        Files.createDirectories(tempDir.resolve("references/sub"));
        Files.writeString(tempDir.resolve("references/sub/orphan.md"), "# nested orphan\n");

        String text = validSkillBody("Body without any link.");
        List<SkillProblem> problems = rules().check(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId, SkillProblem::message)
            .containsExactly(tuple("resource.unused-reference", "references/sub/orphan.md"));
    }

    /**
     * 创建有效 Skill 文本, 避免与 description.missing 等结构规则冲突
     */
    private String validSkillBody(String body) {
        return """
            ---
            name: my-skill
            description: Use this skill when validating resource usage.
            ---
            # My Skill

            Use this skill when reviewing resources.
            %s
            """.formatted(body);
    }

    /**
     * 创建以临时目录作为 skill 根目录的资源规则
     */
    private ResourceRules rules(SkillReference... references) {
        return new ResourceRules(
            skillFile -> Optional.of(tempDir),
            skillFile -> List.of(references)
        );
    }

    /**
     * 创建规则层引用模型, 让 ResourceRules 测试聚焦"目录扫描 + 集合差集"逻辑
     */
    private SkillReference reference(String target) {
        return new SkillReference(target, TextRange.create(0, target.length()));
    }

    /**
     * 把 ruleId + message 关键字段拼成 tuple, 简化断言写法
     */
    private static org.assertj.core.groups.Tuple tuple(String ruleId, String messageSubstring) {
        return new org.assertj.core.groups.Tuple(ruleId, messageWith(ruleId, messageSubstring));
    }

    /**
     * 把 message 模板拼成期望值, 与 bundle 文案保持一致
     */
    private static String messageWith(String ruleId, String fileRelativePath) {
        return switch (ruleId) {
            case "resource.unused-reference" ->
                "Resource file " + fileRelativePath + " is not referenced from SKILL.md";
            case "script.missing-usage" ->
                "Script " + fileRelativePath + " is not mentioned in SKILL.md body";
            default -> throw new IllegalArgumentException("Unknown ruleId: " + ruleId);
        };
    }
}
