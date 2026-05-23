package dev.dong4j.idea.skill.inspector.model;

/**
 * Skill 正文模型
 * <p> 表示 YAML frontmatter 之后的 Markdown 正文, 并保留它在原文件中的文本范围.
 * 范围信息用于后续正文质量规则和 Problems 定位.
 *
 * @param text        正文文本
 * @param startOffset 正文起始 offset
 * @param endOffset   正文结束 offset
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public record SkillBody(String text, int startOffset, int endOffset) {
}
