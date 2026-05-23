package dev.dong4j.idea.skill.inspector.detection;

import com.intellij.psi.PsiFile;

import org.jetbrains.annotations.NotNull;

/**
 * Skill 文件检测器
 * <p> V1 只对文件名严格等于 {@code SKILL.md} 的文件启用检查. 不扫描项目、不判断
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
     * 判断当前 PSI 文件是否是 Skill 入口文件
     *
     * @param psiFile PSI 文件
     * @return 如果文件名为 {@code SKILL.md} 则返回 true
     */
    public static boolean isSkillFile(@NotNull PsiFile psiFile) {
        return SKILL_FILE_NAME.equals(psiFile.getName());
    }
}
