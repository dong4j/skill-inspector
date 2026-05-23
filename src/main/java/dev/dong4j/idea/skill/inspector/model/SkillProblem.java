package dev.dong4j.idea.skill.inspector.model;

import com.intellij.openapi.util.TextRange;

import java.util.List;

/**
 * Skill 检查问题
 * <p> 规则层输出的统一问题模型, 最终由 Inspection 适配层映射为 IntelliJ 的
 * {@code ProblemDescriptor}. 该模型携带可选 Quick Fix 类型, 但不直接包含 IDE 写操作.
 *
 * @param ruleId     规则 ID, 例如 {@code frontmatter.missing}
 * @param severity   严重程度
 * @param message    展示给用户的问题描述
 * @param range      问题所在文本范围
 * @param fixTypes   可用 Quick Fix 类型列表
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public record SkillProblem(String ruleId,
                           SkillSeverity severity,
                           String message,
                           TextRange range,
                           List<SkillFixType> fixTypes) {
}
