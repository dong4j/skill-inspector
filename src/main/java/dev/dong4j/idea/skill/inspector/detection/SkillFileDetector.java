package dev.dong4j.idea.skill.inspector.detection;

import com.intellij.psi.PsiFile;

import org.jetbrains.annotations.NotNull;

/**
 * Skill 文件检测器
 * <p> V1 只对文件名严格等于 {@code SKILL.md} 的物理顶层文件启用检查. 不扫描项目、不判断
 * Claude/Codex/Cursor 等具体 skill 根目录, 以保持 Inspection 入口足够轻量.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public final class SkillFileDetector {

    /** Agent Skill 入口文件名 */
    public static final String SKILL_FILE_NAME = "SKILL.md";

    /** 私有构造函数, 防止工具类被实例化 */
    private SkillFileDetector() {
    }

    /**
     * 判断当前 PSI 文件是否是 Skill 入口文件.
     * <p>Markdown fenced code block 会为 {@code ```markdown} 片段创建 injected PSI,
     * 这些片段在 IDE Problems View 中仍可能映射回宿主 {@code SKILL.md}. 如果只按文件名判断,
     * 代码块示例会被误当成独立 Skill 文件检查, 造成正文中的 frontmatter 误报和重复条目.
     * 因此这里要求 {@link PsiFile#getContext()} 为空, 只允许真实文件本身进入规则层.
     *
     * @param psiFile PSI 文件
     * @return 如果文件名为 {@code SKILL.md} 且不是 injected PSI 片段则返回 true
     */
    public static boolean isSkillFile(@NotNull PsiFile psiFile) {
        return SKILL_FILE_NAME.equals(psiFile.getName()) && psiFile.getContext() == null;
    }
}
