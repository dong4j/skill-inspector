package dev.dong4j.idea.skill.inspector.quickfix;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SkillQuickFixTexts} 单元测试
 * <p> Quick Fix 的 IDE 写操作需要 fixture 测试, 但文本生成逻辑可以用普通单元测试快速覆盖,
 * 尤其是 frontmatter 空内容插入字段时的换行边界.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
class SkillQuickFixTextsTest {

    @Test
    void shouldCreateFrontMatterTemplateForEmptyDocument() {
        String template = SkillQuickFixTexts.frontMatterTemplate("my-skill", true);

        assertThat(template).isEqualTo("""
            ---
            name: my-skill
            description: TODO: Describe when to use this skill.
            ---
            """);
    }

    @Test
    void shouldAddBlankLineBetweenFrontMatterAndExistingDocument() {
        String template = SkillQuickFixTexts.frontMatterTemplate("my-skill", false);

        assertThat(template).endsWith("---\n\n");
    }

    @Test
    void shouldCreateFieldInsertionForEmptyFrontMatterContent() {
        String insertion = SkillQuickFixTexts.fieldInsertion(false, "name", "my-skill");

        assertThat(insertion).isEqualTo("name: my-skill\n");
    }

    @Test
    void shouldCreateFieldInsertionAfterExistingField() {
        String insertion = SkillQuickFixTexts.fieldInsertion(true, "description", "Use this skill when testing.");

        assertThat(insertion).isEqualTo("\ndescription: Use this skill when testing.");
    }

    @Test
    void shouldFallbackWhenDirectoryNameIsBlank() {
        assertThat(SkillQuickFixTexts.skillNameOrFallback("")).isEqualTo("new-skill");
        assertThat(SkillQuickFixTexts.skillNameOrFallback("my-skill")).isEqualTo("my-skill");
    }
}
