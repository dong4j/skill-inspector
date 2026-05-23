package dev.dong4j.idea.skill.inspector.quickfix;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Skill Quick Fix 文本生成工具
 * <p> 将 Quick Fix 中可纯文本验证的部分从 IDE 写操作中拆出来, 方便单元测试覆盖
 * frontmatter 模板、字段插入换行、目录名 fallback、kebab-case 规范化和链接路径清洗等关键边界.
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

    /** specification 规定 name 字段最大长度 */
    private static final int MAX_NAME_LENGTH = 64;

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

    /**
     * 将任意输入规范化为合规的 Agent Skill name
     * <p> 转换规则按以下顺序应用, 与 {@code StructuralRules} 中的 kebab-case 正则
     * {@code ^[a-z0-9]+(?:-[a-z0-9]+)*$} 保持一致:
     * <ol>
     *   <li>小写化、去除首尾空白</li>
     *   <li>把空白、下划线、空格、点号、斜杠等"软分隔符"统一转成连字符</li>
     *   <li>丢弃其余非法字符 (含 ASCII 范围外字符), 不做音译</li>
     *   <li>合并连续连字符、去掉首尾连字符</li>
     *   <li>截断到 specification 规定的 64 字符上限, 截断后再次去尾部连字符</li>
     *   <li>若结果为空则回退到 {@link #FALLBACK_SKILL_NAME}, 保证 Quick Fix 不会生成非法值</li>
     * </ol>
     *
     * @param value 原始 name 值
     * @return 合法的 kebab-case name
     */
    @NotNull
    public static String toKebabCaseName(String value) {
        if (value == null) {
            return FALLBACK_SKILL_NAME;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return FALLBACK_SKILL_NAME;
        }

        StringBuilder builder = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (isKebabAlphanumeric(c)) {
                builder.append(c);
            } else if (isSoftSeparator(c)) {
                builder.append('-');
            }
        }

        String collapsed = builder.toString().replaceAll("-+", "-");
        String trimmed = trimHyphens(collapsed);
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimHyphens(trimmed.substring(0, MAX_NAME_LENGTH));
        }
        return trimmed.isEmpty() ? FALLBACK_SKILL_NAME : trimmed;
    }

    /**
     * 清理 Markdown 链接目标中的 anchor 和 query, 与 ReferenceRules 行为对齐
     * <p> Quick Fix 在创建缺失引用文件时, 需要把链接路径还原为真实文件路径, 否则会把
     * {@code references/x.md#section} 当成磁盘上的文件名.
     *
     * @param target 原始链接目标
     * @return 去除锚点和查询串后的路径
     */
    @NotNull
    public static String stripFragmentAndQuery(@NotNull String target) {
        int cut = target.length();
        int fragment = target.indexOf('#');
        int query = target.indexOf('?');
        if (fragment >= 0) {
            cut = Math.min(cut, fragment);
        }
        if (query >= 0) {
            cut = Math.min(cut, query);
        }
        return target.substring(0, cut).trim();
    }

    /**
     * 判断字符是否属于 kebab-case 合法字符集
     */
    private static boolean isKebabAlphanumeric(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    /**
     * 判断字符是否应被视为软分隔符并替换为连字符
     * <p> 这里只接受常见的命名分隔符. 中文、emoji 等其他 Unicode 字符直接丢弃,
     * 避免擅自做音译给出错误的 skill 名称.
     */
    private static boolean isSoftSeparator(char c) {
        return Character.isWhitespace(c)
            || c == '_' || c == '-'
            || c == '.' || c == ','
            || c == '/' || c == '\\';
    }

    /**
     * 去除字符串首尾连字符
     */
    @NotNull
    private static String trimHyphens(@NotNull String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }
}
