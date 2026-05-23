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
    SYNC_NAME_WITH_DIRECTORY
}
