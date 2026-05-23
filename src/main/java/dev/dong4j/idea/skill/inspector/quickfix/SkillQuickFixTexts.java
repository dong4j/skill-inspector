package dev.dong4j.idea.skill.inspector.quickfix;

import org.jetbrains.annotations.NotNull;

/**
 * Skill Quick Fix 文本生成工具
 * <p> 将 Quick Fix 中可纯文本验证的部分从 IDE 写操作中拆出来, 方便单元测试覆盖
 * frontmatter 模板、字段插入换行和目录名 fallback 等关键边界.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public final class SkillQuickFixTexts {

    /** 目录名不可用时使用的保守 fallback, 避免生成空 name */
    public static final String FALLBACK_SKILL_NAME = "new-skill";

    /** description 占位文本只作为结构修复, 不尝试替用户生成真实描述 */
    public static final String DESCRIPTION_PLACEHOLDER = "TODO: Describe when to use this skill.";

    /** 私有构造函数, 防止工具类被实例化 */
    private SkillQuickFixTexts() {
    }

    /**
     * 规范化 Skill 名称
     *
     * @param skillDirectoryName SKILL.md 所在父目录名
     * @return 可写入 frontmatter 的 name 值
     */
    @NotNull
    public static String skillNameOrFallback(@NotNull String skillDirectoryName) {
        return skillDirectoryName.isBlank() ? FALLBACK_SKILL_NAME : skillDirectoryName;
    }

    /**
     * 创建完整 frontmatter 模板
     *
     * @param skillName      skill 名称
     * @param documentIsEmpty 当前文档是否为空
     * @return 可插入文件开头的 frontmatter 文本
     */
    @NotNull
    public static String frontMatterTemplate(@NotNull String skillName, boolean documentIsEmpty) {
        String separator = documentIsEmpty ? "" : "\n";
        return "---\n"
            + "name: " + skillName + "\n"
            + "description: " + DESCRIPTION_PLACEHOLDER + "\n"
            + "---\n"
            + separator;
    }

    /**
     * 创建追加到已有 frontmatter 内容区的字段文本
     * <p> 当 frontmatter 内容区为空时, 需要在字段后补换行, 保证闭合 {@code ---} 仍在下一行.
     *
     * @param hasExistingContent frontmatter 内容区是否已有字段
     * @param key                字段名
     * @param value              字段值
     * @return 待插入文本
     */
    @NotNull
    public static String fieldInsertion(boolean hasExistingContent, @NotNull String key, @NotNull String value) {
        String prefix = hasExistingContent ? "\n" : "";
        String suffix = hasExistingContent ? "" : "\n";
        return prefix + key + ": " + value + suffix;
    }
}
