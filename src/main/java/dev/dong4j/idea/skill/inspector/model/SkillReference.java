package dev.dong4j.idea.skill.inspector.model;

import com.intellij.openapi.util.TextRange;

/**
 * Markdown 本地引用模型
 * <p> 表示 {@code SKILL.md} 正文中的一个 Markdown 链接目标. V1 只关心本地相对路径,
 * 但仍保留原始 target, 便于规则层跳过 URL、锚点和 mailto 等非文件引用.
 *
 * @param target      Markdown 链接原始目标
 * @param targetRange 链接目标在文件中的文本范围
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public record SkillReference(String target, TextRange targetRange) {
}
