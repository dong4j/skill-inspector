package dev.dong4j.idea.skill.inspector.model;

/**
 * Skill Quick Fix 类型
 * <p> 规则层只返回可执行修复的语义类型, 具体的 IntelliJ {@code LocalQuickFix}
 * 对象由 Inspection 适配层创建. 这样可以保持规则层足够轻量, 也避免在核心规则里夹杂 IDE 写操作细节.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public enum SkillFixType {
    /** 创建完整 frontmatter 模板 */
    ADD_FRONT_MATTER,

    /** 在已有 frontmatter 中补充 name 字段 */
    ADD_NAME,

    /** 在已有 frontmatter 中补充 description 字段 */
    ADD_DESCRIPTION,

    /** 将 frontmatter 中的 name 同步为父目录名 */
    SYNC_NAME_WITH_DIRECTORY,

    /**
     * 将不合规 name 自动转换为 kebab-case
     * <p> 仅作用于 frontmatter.name 字段; 转换规则由 {@code SkillQuickFixTexts.toKebabCaseName} 实现.
     */
    CONVERT_NAME_TO_KEBAB,

    /**
     * 创建缺失的引用文件
     * <p> 当 Markdown 相对链接指向的文件不存在时, 在 skill 目录内创建一个空文件,
     * 并按需创建父目录. 不写入任何内容, 由作者后续手动补充, 保证修复保守.
     */
    CREATE_MISSING_REFERENCE
}
