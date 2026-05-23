package dev.dong4j.idea.skill.inspector.parser;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;

import dev.dong4j.idea.skill.inspector.model.SkillFrontMatter;

import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.intellij.testFramework.ProjectExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FrontMatterParser} 单元测试
 * <p> 覆盖 frontmatter 解析的核心边界, 确保规则层拿到的字段值、正文范围和错误状态稳定.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
class FrontMatterParserTest {

    /** 测试用轻量项目, 用于创建 Markdown/YAML PSI */
    @RegisterExtension
    private static final ProjectExtension PROJECT = new ProjectExtension();

    @Test
    void shouldParseValidFrontMatterAndBody() {
        String text = """
            ---
            name: my-skill
            description: Use this skill when reviewing agent skill files.
            allowed-tools: Read Edit
            ---
            # My Skill

            Use this skill when...
            """;

        FrontMatterParseResult result = parse(text);

        assertThat(result.frontMatter()).isNotNull();
        SkillFrontMatter frontMatter = result.frontMatter();
        assertThat(frontMatter.parseError()).isNull();
        assertThat(frontMatter.values())
            .containsEntry("name", "my-skill")
            .containsEntry("description", "Use this skill when reviewing agent skill files.")
            .containsEntry("allowed-tools", "Read Edit");
        assertThat(frontMatter.metadata().name().value()).isEqualTo("my-skill");
        assertThat(frontMatter.metadata().description().value()).isEqualTo("Use this skill when reviewing agent skill files.");
        assertThat(frontMatter.metadata().allowedTools().value()).isEqualTo("Read Edit");
        assertThat(result.body().text()).startsWith("# My Skill");
        assertThat(result.body().startOffset()).isGreaterThanOrEqualTo(frontMatter.endOffset());
    }

    @Test
    void shouldReturnMissingFrontMatterWhenFileDoesNotStartWithDelimiter() {
        String text = "# My Skill\n\nUse this skill when...";

        FrontMatterParseResult result = parse(text);

        assertThat(result.frontMatter()).isNull();
        assertThat(result.body().text()).isEqualTo(text);
        assertThat(result.body().startOffset()).isZero();
    }

    @Test
    void shouldReportMissingClosingDelimiter() {
        String text = """
            ---
            name: broken-skill
            description: Missing closing delimiter
            """;

        FrontMatterParseResult result = parse(text);

        assertThat(result.frontMatter()).isNotNull();
        assertThat(result.frontMatter().parseError()).isEqualTo("Missing closing frontmatter delimiter");
        assertThat(result.body().text()).isEmpty();
    }

    @Test
    void shouldKeepYamlPsiParsedEntriesWhenValueIsIncomplete() {
        String text = """
            ---
            name: broken-skill
            description: [unterminated
            ---
            # Broken
            """;

        FrontMatterParseResult result = parse(text);

        assertThat(result.frontMatter()).isNotNull();
        assertThat(result.frontMatter().values()).containsEntry("name", "broken-skill");
    }

    @Test
    void shouldStripSimpleQuotesFromValues() {
        String text = """
            ---
            name: "quoted-skill"
            description: 'Use this skill when values are quoted.'
            ---
            # Quoted
            """;

        FrontMatterParseResult result = parse(text);

        assertThat(result.frontMatter()).isNotNull();
        assertThat(result.frontMatter().values())
            .containsEntry("name", "quoted-skill")
            .containsEntry("description", "Use this skill when values are quoted.");
    }

    @Test
    void shouldParseFrontMatterAfterUtf8Bom() {
        String text = "\uFEFF---\n"
            + "name: brand-guidelines\n"
            + "description: Use it when brand colors apply.\n"
            + "---\n"
            + "# Anthropic Brand Styling\n";

        FrontMatterParseResult result = parse(text);

        assertThat(result.frontMatter()).isNotNull();
        assertThat(result.frontMatter().startOffset()).isEqualTo(1);
        assertThat(result.frontMatter().values())
            .containsEntry("name", "brand-guidelines")
            .containsEntry("description", "Use it when brand colors apply.");
        assertThat(result.body().text()).startsWith("# Anthropic Brand Styling");
    }

    @Test
    void shouldAcceptNestedMetadataMappingWithoutParseError() {
        String text = """
            ---
            name: pdf-processing
            description: Extract PDF text, fill forms, merge files. Use when handling PDFs.
            metadata:
              author: example-org
              version: "1.0"
            ---
            # PDF Processing
            """;

        FrontMatterParseResult result = parse(text);

        assertThat(result.frontMatter()).isNotNull();
        assertThat(result.frontMatter().parseError()).isNull();
        assertThat(result.frontMatter().values())
            .containsEntry("name", "pdf-processing")
            .containsEntry("description", "Extract PDF text, fill forms, merge files. Use when handling PDFs.");
    }

    @Test
    void shouldParseDescriptionBlockScalarValue() {
        String text = """
            ---
            name: long-description
            description: >
              Extract PDF text, fill forms, and merge documents.
              Use when working with PDFs or document automation.
            ---
            # Long Description
            """;

        FrontMatterParseResult result = parse(text);

        assertThat(result.frontMatter()).isNotNull();
        assertThat(result.frontMatter().parseError()).isNull();
        assertThat(result.frontMatter().values().get("description"))
            .isEqualTo("Extract PDF text, fill forms, and merge documents. Use when working with PDFs or document automation.");
    }

    /**
     * 使用 Markdown 插件 PSI 创建测试文件, 覆盖生产路径解析逻辑。
     */
    private FrontMatterParseResult parse(String text) {
        Project project = PROJECT.getProject();
        return ApplicationManager.getApplication().runReadAction((com.intellij.openapi.util.Computable<FrontMatterParseResult>) () ->
            FrontMatterParser.parse(markdownFile(project, text))
        );
    }

    /**
     * 创建 Markdown PSI 测试文件。
     */
    private PsiFile markdownFile(Project project, String text) {
        return PsiFileFactory.getInstance(project).createFileFromText("SKILL.md", MarkdownLanguage.INSTANCE, text);
    }
}
