package dev.dong4j.idea.skill.inspector.model;

import com.intellij.openapi.util.TextRange;

/**
 * frontmatter 中的单个 YAML 键值条目
 * <p> V1 只需要识别 specification 中的顶层字段, 因此先用轻量的单行 key-value 模型.
 * 后续如果引入完整 YAML PSI, 仍可保留该模型作为规则层的稳定输入.
 *
 * @param key        字段名
 * @param value      字段值, 已去除首尾空白和简单引号
 * @param keyRange   字段名文本范围
 * @param valueRange 字段值文本范围
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public record SkillYamlEntry(String key, String value, TextRange keyRange, TextRange valueRange) {
}
