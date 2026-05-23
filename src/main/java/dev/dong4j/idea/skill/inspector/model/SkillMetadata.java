package dev.dong4j.idea.skill.inspector.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Skill 前置元数据对象
 * <p> 对应 Agent Skills specification 中定义的 frontmatter 字段。规则层应通过该对象访问
 * {@code name}/{@code description}/{@code compatibility} 等字段, 避免在规则中散落字符串 key
 * 和重复的字段查找逻辑。
 *
 * @param name          必填 name 字段
 * @param description   必填 description 字段
 * @param license       可选 license 字段
 * @param compatibility 可选 compatibility 字段
 * @param metadata      可选 metadata 映射字段
 * @param allowedTools  可选 allowed-tools 字段
 * @param entries       全部顶层字段, 保留给扩展规则和未知字段检查
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public record SkillMetadata(@Nullable SkillYamlEntry name,
                            @Nullable SkillYamlEntry description,
                            @Nullable SkillYamlEntry license,
                            @Nullable SkillYamlEntry compatibility,
                            @Nullable SkillYamlEntry metadata,
                            @Nullable SkillYamlEntry allowedTools,
                            @NotNull Map<String, SkillYamlEntry> entries) {

    /** name 字段 */
    private static final String FIELD_NAME = "name";

    /** description 字段 */
    private static final String FIELD_DESCRIPTION = "description";

    /** license 字段 */
    private static final String FIELD_LICENSE = "license";

    /** compatibility 字段 */
    private static final String FIELD_COMPATIBILITY = "compatibility";

    /** metadata 字段 */
    private static final String FIELD_METADATA = "metadata";

    /** allowed-tools 字段 */
    private static final String FIELD_ALLOWED_TOOLS = "allowed-tools";

    /**
     * 从 YAML 顶层条目构建强类型 metadata 对象
     *
     * @param entries YAML 顶层条目
     * @return Skill metadata
     */
    @NotNull
    public static SkillMetadata from(@NotNull List<SkillYamlEntry> entries) {
        Map<String, SkillYamlEntry> entryMap = new LinkedHashMap<>();
        for (SkillYamlEntry entry : entries) {
            entryMap.put(entry.key(), entry);
        }
        return new SkillMetadata(
            entryMap.get(FIELD_NAME),
            entryMap.get(FIELD_DESCRIPTION),
            entryMap.get(FIELD_LICENSE),
            entryMap.get(FIELD_COMPATIBILITY),
            entryMap.get(FIELD_METADATA),
            entryMap.get(FIELD_ALLOWED_TOOLS),
            Map.copyOf(entryMap)
        );
    }

    /**
     * 判断可选字段是否已经声明
     *
     * @param key 字段名
     * @return 如果字段存在则返回 true
     */
    public boolean hasField(@NotNull String key) {
        return entries.containsKey(key);
    }

    /**
     * 按字段名读取原始 YAML 条目
     *
     * @param key 字段名
     * @return 字段条目, 不存在时返回 {@code null}
     */
    @Nullable
    public SkillYamlEntry entry(@NotNull String key) {
        return entries.get(key);
    }
}
