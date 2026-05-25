package dev.dong4j.idea.skill.inspector.inspection;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

import dev.dong4j.idea.skill.inspector.model.SkillProblem;

import org.jetbrains.annotations.NotNull;

/**
 * Inspection 问题的稳定标识.
 * <p>用于把规则层输出提升为"文件版本级"问题 key。IDE daemon 可能对同一个文件版本
 * 启动多轮 inspection 会话, 但同一文件、同一 modification stamp、同一规则和同一文本范围
 * 的问题在 Problems View 中只应出现一次.
 *
 * @param filePath          文件路径, VirtualFile 不可用时退回到文件名
 * @param modificationStamp PSI 文本版本标记
 * @param ruleId            Skill 规则 ID
 * @param startOffset       问题起始 offset
 * @param endOffset         问题结束 offset
 * @param message           展示给用户的问题文案
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.25
 * @since 1.0.0
 */
public record SkillProblemKey(@NotNull String filePath,
                              long modificationStamp,
                              @NotNull String ruleId,
                              int startOffset,
                              int endOffset,
                              @NotNull String message) {

    /**
     * 从 IDE PSI 与规则问题构造稳定 key.
     *
     * @param file    当前 SKILL.md PSI 文件
     * @param problem 规则层输出的问题
     * @return 可跨 daemon inspection 会话复用的问题 key
     */
    @NotNull
    public static SkillProblemKey from(@NotNull PsiFile file, @NotNull SkillProblem problem) {
        VirtualFile virtualFile = file.getVirtualFile();
        String path = virtualFile == null ? file.getName() : virtualFile.getPath();
        long modificationStamp = file.getViewProvider().getModificationStamp();
        return new SkillProblemKey(
            path,
            modificationStamp,
            problem.ruleId(),
            problem.range().getStartOffset(),
            problem.range().getEndOffset(),
            problem.message()
        );
    }
}
