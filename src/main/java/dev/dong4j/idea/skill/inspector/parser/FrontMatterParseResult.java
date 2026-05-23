package dev.dong4j.idea.skill.inspector.parser;

import dev.dong4j.idea.skill.inspector.model.SkillBody;
import dev.dong4j.idea.skill.inspector.model.SkillFrontMatter;

import org.jetbrains.annotations.Nullable;

/**
 * frontmatter 解析结果
 * <p> 同时返回 YAML frontmatter 和 Markdown 正文, 因为正文起始位置由 frontmatter
 * 的闭合分隔符决定, 两者应在同一次文本扫描中计算.
 *
 * @param frontMatter YAML frontmatter, 缺失时为空
 * @param body        Markdown 正文
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public record FrontMatterParseResult(@Nullable SkillFrontMatter frontMatter, SkillBody body) {
}
