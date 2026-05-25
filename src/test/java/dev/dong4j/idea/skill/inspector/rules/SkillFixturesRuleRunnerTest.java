package dev.dong4j.idea.skill.inspector.rules;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.ProjectExtension;

import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;

import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillProblem;
import dev.dong4j.idea.skill.inspector.parser.FrontMatterParseResult;
import dev.dong4j.idea.skill.inspector.parser.FrontMatterParser;
import dev.dong4j.idea.skill.inspector.parser.MarkdownReferenceParser;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixture 级规则快照测试.
 * <p>每个 fixture 子目录下的 {@code SKILL.md} 都代表一个真实 skill 目录。
 * 本测试使用真实 Markdown/YAML PSI 解析 SKILL.md, 同时把 fixture 目录作为
 * {@link SkillFile#skillDirectory()} 注入, 从而在普通 {@code ./gradlew test} 中覆盖
 * Structural / Quality / Reference / Resource / Security 全规则链路是否仍然生效.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.25
 * @since 1.0.0
 */
class SkillFixturesRuleRunnerTest {

    /** fixture 根目录 */
    private static final Path FIXTURES_DIR = Path.of("src/test/resources/fixtures/skills");

    /** 预期 ruleId 清单 */
    private static final Path EXPECTED_RULES = FIXTURES_DIR.resolve("expected-rule-ids.properties");

    /** 测试用轻量项目, 用于创建 Markdown/YAML PSI */
    @RegisterExtension
    private static final ProjectExtension PROJECT = new ProjectExtension();

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void shouldMatchExpectedRuleIds(@NotNull String fixtureName,
                                    @NotNull Path skillFilePath,
                                    @NotNull List<String> expectedRuleIds) throws IOException {
        String text = Files.readString(skillFilePath);
        SkillFile skillFile = buildSkillFile(skillFilePath, text);
        RuleRunner ruleRunner = ruleRunnerFor(skillFilePath.getParent());

        List<String> actualRuleIds = ApplicationManager.getApplication().runReadAction((Computable<List<String>>) () ->
                                                                                           ruleRunner.run(skillFile).stream()
                                                                                               .map(SkillProblem::ruleId)
                                                                                               .sorted()
                                                                                               .toList()
                                                                                      );

        assertThat(actualRuleIds)
            .as("fixture %s", fixtureName)
            .containsExactlyElementsOf(expectedRuleIds.stream().sorted().toList());
    }

    /**
     * 从 expected-rule-ids.properties 和实际 fixture 目录生成参数.
     */
    @NotNull
    private static Stream<Arguments> fixtures() throws IOException {
        Properties expected = loadExpectedRules();
        List<Arguments> arguments;
        try (Stream<Path> stream = Files.list(FIXTURES_DIR)) {
            arguments = stream
                .filter(Files::isDirectory)
                .filter(directory -> Files.isRegularFile(directory.resolve("SKILL.md")))
                .sorted()
                .map(directory -> toArguments(directory, expected))
                .toList();
        }

        assertThat(arguments)
            .as("fixture directories")
            .isNotEmpty();
        assertThat(expected.stringPropertyNames())
            .as("expected-rule-ids.properties should match fixture directories exactly")
            .containsExactlyInAnyOrderElementsOf(arguments.stream()
                                                     .map(argument -> (String) argument.get()[0])
                                                     .toList());
        return arguments.stream();
    }

    /**
     * 构造单个 fixture 参数.
     */
    @NotNull
    private static Arguments toArguments(@NotNull Path fixtureDirectory, @NotNull Properties expected) {
        String fixtureName = fixtureDirectory.getFileName().toString();
        String rawRuleIds = Objects.requireNonNull(expected.getProperty(fixtureName),
                                                   () -> "Missing expected rule IDs for fixture: " + fixtureName);
        List<String> ruleIds = rawRuleIds.isBlank()
                               ? List.of()
                               : Arrays.stream(rawRuleIds.split(","))
                                   .map(String::trim)
                                   .filter(ruleId -> !ruleId.isEmpty())
                                   .toList();
        return Arguments.of(fixtureName, fixtureDirectory.resolve("SKILL.md"), ruleIds);
    }

    /**
     * 读取预期 ruleId 配置.
     */
    @NotNull
    private static Properties loadExpectedRules() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(EXPECTED_RULES)) {
            properties.load(input);
        }
        return properties;
    }

    /**
     * 使用真实 Markdown PSI 解析文本, 并注入真实 fixture 目录路径给文件系统相关规则.
     */
    @NotNull
    private SkillFile buildSkillFile(@NotNull Path skillFilePath, @NotNull String text) {
        Project project = PROJECT.getProject();
        return ApplicationManager.getApplication().runReadAction((Computable<SkillFile>) () -> {
            PsiFile psiFile = PsiFileFactory.getInstance(project)
                .createFileFromText("SKILL.md", MarkdownLanguage.INSTANCE, text);
            FrontMatterParseResult parseResult = FrontMatterParser.parse(psiFile);
            Path skillDirectoryPath = skillFilePath.getParent();
            String skillDirectoryName = skillDirectoryPath.getFileName().toString();

            return new SkillFile(
                psiFile,
                skillDirectoryName,
                null,
                parseResult.frontMatter(),
                parseResult.body()
            );
        });
    }

    /**
     * 为当前 fixture 目录创建规则执行器.
     * <p>这里显式注入 skill 根目录, 避免为了文件系统规则 mock IntelliJ VirtualFile.
     */
    @NotNull
    private RuleRunner ruleRunnerFor(@NotNull Path skillDirectoryPath) {
        Path normalizedDirectory = skillDirectoryPath.toAbsolutePath().normalize();
        return new RuleRunner(List.of(
            new StructuralRules(),
            new QualityRules(),
            new ReferenceRules(skillFile -> java.util.Optional.of(normalizedDirectory)),
            new ResourceRules(
                skillFile -> java.util.Optional.of(normalizedDirectory),
                MarkdownReferenceParser::parse
            ),
            new SecurityRules()
                                     ));
    }
}
