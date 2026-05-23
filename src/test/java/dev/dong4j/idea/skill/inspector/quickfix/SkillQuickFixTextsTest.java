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

    @Test
    void shouldConvertCommonSeparatorsToKebab() {
        assertThat(SkillQuickFixTexts.toKebabCaseName("My Skill")).isEqualTo("my-skill");
        assertThat(SkillQuickFixTexts.toKebabCaseName("my_skill")).isEqualTo("my-skill");
        assertThat(SkillQuickFixTexts.toKebabCaseName("My.Skill")).isEqualTo("my-skill");
        assertThat(SkillQuickFixTexts.toKebabCaseName("path/to/skill")).isEqualTo("path-to-skill");
    }

    @Test
    void shouldCollapseConsecutiveSeparatorsAndTrimEdges() {
        assertThat(SkillQuickFixTexts.toKebabCaseName("---weird   name---")).isEqualTo("weird-name");
        assertThat(SkillQuickFixTexts.toKebabCaseName("__my__skill__")).isEqualTo("my-skill");
    }

    @Test
    void shouldDropNonAsciiCharactersWithoutTransliteration() {
        assertThat(SkillQuickFixTexts.toKebabCaseName("中文 mixed")).isEqualTo("mixed");
        assertThat(SkillQuickFixTexts.toKebabCaseName("emoji 🎉 skill")).isEqualTo("emoji-skill");
    }

    @Test
    void shouldFallbackWhenInputIsBlankOrAllInvalid() {
        assertThat(SkillQuickFixTexts.toKebabCaseName(null)).isEqualTo("new-skill");
        assertThat(SkillQuickFixTexts.toKebabCaseName("")).isEqualTo("new-skill");
        assertThat(SkillQuickFixTexts.toKebabCaseName("   ")).isEqualTo("new-skill");
        assertThat(SkillQuickFixTexts.toKebabCaseName("!!!")).isEqualTo("new-skill");
        // 全连字符的输入在 trim 后也应回退到 fallback, 而不是返回空串.
        assertThat(SkillQuickFixTexts.toKebabCaseName("----")).isEqualTo("new-skill");
    }

    @Test
    void shouldTruncateToSpecificationLengthAndTrimDanglingHyphen() {
        // 超过 64 字符时应截断, 且不能在截断处留下结尾连字符.
        String value = "a".repeat(60) + " spaces here";
        String result = SkillQuickFixTexts.toKebabCaseName(value);
        assertThat(result).hasSizeLessThanOrEqualTo(64).doesNotEndWith("-");
    }

    @Test
    void shouldRemoveHyphenLandingAtTruncationBoundary() {
        // 让截断点正好落在替换出来的连字符上, 验证最后一次 trimHyphens 不漏处理.
        String value = "a".repeat(63) + " bar";
        String result = SkillQuickFixTexts.toKebabCaseName(value);
        assertThat(result).isEqualTo("a".repeat(63)).doesNotEndWith("-");
    }

    @Test
    void shouldStripFragmentAndQueryFromReferenceTarget() {
        assertThat(SkillQuickFixTexts.stripFragmentAndQuery("references/guide.md")).isEqualTo("references/guide.md");
        assertThat(SkillQuickFixTexts.stripFragmentAndQuery("references/guide.md#section")).isEqualTo("references/guide.md");
        assertThat(SkillQuickFixTexts.stripFragmentAndQuery("references/guide.md?ts=1")).isEqualTo("references/guide.md");
        assertThat(SkillQuickFixTexts.stripFragmentAndQuery("references/guide.md?ts=1#section")).isEqualTo("references/guide.md");
        assertThat(SkillQuickFixTexts.stripFragmentAndQuery("#anchor-only")).isEmpty();
    }
}
