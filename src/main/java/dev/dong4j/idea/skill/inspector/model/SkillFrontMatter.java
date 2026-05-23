package dev.dong4j.idea.skill.inspector.model;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Skill YAML frontmatter 模型
 * <p> 保留 frontmatter 的完整范围、内容范围、解析后的顶层键值和解析错误.
 * 解析失败时仍返回对象, 是为了让 Inspection 可以报告 YAML 错误, 而不是中断整个检查流程.
 *
 * @param startOffset        frontmatter 起始 offset, 包含开头 {@code ---}
 * @param endOffset          frontmatter 结束 offset, 包含闭合 {@code ---}
 * @param contentStartOffset YAML 内容起始 offset
 * @param contentEndOffset   YAML 内容结束 offset
 * @param values             顶层字段键值
 * @param entries            顶层字段条目及范围信息
 * @param metadata           Agent Skill 强类型前置元数据
 * @param parseError         解析错误, 没有错误时为空
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public record SkillFrontMatter(int startOffset,
                               int endOffset,
                               int contentStartOffset,
                               int contentEndOffset,
                               Map<String, String> values,
                               List<SkillYamlEntry> entries,
                               SkillMetadata metadata,
                               @Nullable String parseError) {
}
