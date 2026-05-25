package dev.dong4j.idea.skill.inspector.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiFile;

import dev.dong4j.idea.skill.inspector.model.SkillFixType;
import dev.dong4j.idea.skill.inspector.model.SkillProblem;
import dev.dong4j.idea.skill.inspector.model.SkillSeverity;
import dev.dong4j.idea.skill.inspector.quickfix.SkillQuickFix;
import dev.dong4j.idea.skill.inspector.util.TextRangeUtil;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 将 Skill 规则问题映射为 IntelliJ Inspection descriptor.
 * <p>规则层只表达业务问题, 不依赖 IntelliJ Problems View 的高亮类型、Quick Fix 数组
 * 和 TextRange 安全裁剪。本类集中完成适配, 让 {@link SkillMdInspection} 保持为薄入口.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.25
 * @since 1.0.0
 */
final class SkillInspectionProblemMapper {

    /**
     * 将单个 Skill problem 注册到当前 ProblemsHolder.
     *
     * @param file       被检查的 SKILL.md
     * @param holder     当前 inspection 会话的 holder
     * @param isOnTheFly 是否来自 on-the-fly daemon 检查
     * @param problem    规则层问题
     */
    void register(@NotNull PsiFile file,
                  @NotNull ProblemsHolder holder,
                  boolean isOnTheFly,
                  @NotNull SkillProblem problem) {
        holder.registerProblem(
            holder.getManager().createProblemDescriptor(
                file,
                TextRangeUtil.clamp(problem.range(), file.getTextLength()),
                problem.message(),
                toHighlightType(problem.severity()),
                isOnTheFly,
                toQuickFixes(problem.fixTypes())
            )
        );
    }

    /**
     * 将内部严重程度映射到 IDE 高亮类型.
     */
    @NotNull
    private static ProblemHighlightType toHighlightType(@NotNull SkillSeverity severity) {
        return switch (severity) {
            case ERROR -> ProblemHighlightType.ERROR;
            case WARNING -> ProblemHighlightType.WARNING;
            case WEAK_WARNING -> ProblemHighlightType.WEAK_WARNING;
        };
    }

    /**
     * 创建 Quick Fix 数组.
     */
    @NotNull
    private static LocalQuickFix[] toQuickFixes(@NotNull List<SkillFixType> fixTypes) {
        return fixTypes.stream()
            .map(SkillQuickFix::new)
            .toArray(LocalQuickFix[]::new);
    }
}
