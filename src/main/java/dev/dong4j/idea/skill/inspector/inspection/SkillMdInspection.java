package dev.dong4j.idea.skill.inspector.inspection;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;

import dev.dong4j.idea.skill.inspector.detection.SkillFileDetector;
import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillFixType;
import dev.dong4j.idea.skill.inspector.model.SkillProblem;
import dev.dong4j.idea.skill.inspector.model.SkillSeverity;
import dev.dong4j.idea.skill.inspector.parser.SkillModelBuilder;
import dev.dong4j.idea.skill.inspector.quickfix.SkillQuickFix;
import dev.dong4j.idea.skill.inspector.rules.RuleRunner;
import dev.dong4j.idea.skill.inspector.settings.SkillInspectorSettings;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;
import dev.dong4j.idea.skill.inspector.util.TextRangeUtil;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SKILL.md Inspection
 * <p> 这是 V1 的核心 IDE 接入点: 只在文件名为 {@code SKILL.md} 的文件上运行,
 * 将内部规则引擎输出的 {@link SkillProblem} 映射为 IntelliJ Problems 面板中的问题.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public class SkillMdInspection extends LocalInspectionTool {

    /** 默认规则执行器 */
    private final RuleRunner ruleRunner = new RuleRunner();

    /** 诊断日志: 用于排查 IDE 是否对同一 PsiFile 多次调用 checkFile (导致 Problems View 出现重复) */
    private static final Logger LOG = Logger.getInstance(SkillMdInspection.class);

    @Override
    @NotNull
    public String getGroupDisplayName() {
        return SkillInspectorBundle.message("inspection.group.name");
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return SkillInspectorBundle.message("inspection.skill.md.display.name");
    }

    @Override
    @NotNull
    public String getShortName() {
        return "SkillMdInspection";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    /**
     * 声明这是 whole-file inspection, 强制 IDE 只调度一次 {@link #checkFile} 跑整个文件,
     * 不要再走默认的 visitor 路径 (visitor 会按 PSI element 触发, 可能跟 checkFile 同时调度,
     * 导致同一个 problem 被写入 Problems View 两次).
     * <p>这是修掉"Problems View 所有 SKILL.md 警告精确重复 2 次"的根因.
     */
    @Override
    public boolean runForWholeFile() {
        return true;
    }

    @Override
    @Nullable
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file,
                                         @NotNull InspectionManager manager,
                                         boolean isOnTheFly) {
        if (!SkillInspectorSettings.getInstance().isSkillInspectionEnabled() || !SkillFileDetector.isSkillFile(file)) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        SkillFile skillFile = SkillModelBuilder.build(file);
        List<SkillProblem> problems = ruleRunner.run(skillFile);
        // 双重去重防御: RuleRunner 已按 ruleId+range 去重, 这里再用 ProblemDescriptor 维度 (ruleId+offsets) 去重一次,
        // 防止 IDE 在同一 PSI 上多次触发 daemon highlighting pass 时, Problems View 累积出多条完全相同的条目.
        Set<String> seen = new HashSet<>();
        List<ProblemDescriptor> descriptors = new ArrayList<>(problems.size());
        for (SkillProblem problem : problems) {
            String key = problem.ruleId() + "@" + problem.range().getStartOffset() + "-" + problem.range().getEndOffset();
            if (!seen.add(key)) {
                continue;
            }
            descriptors.add(toDescriptor(file, manager, isOnTheFly, problem));
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("SkillMdInspection.checkFile: file=" + file.getName()
                + " onTheFly=" + isOnTheFly
                + " rawProblems=" + problems.size()
                + " uniqueDescriptors=" + descriptors.size());
        }
        return descriptors.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }

    /**
     * 将内部问题模型转换为 IntelliJ ProblemDescriptor
     */
    @NotNull
    private ProblemDescriptor toDescriptor(@NotNull PsiFile file,
                                           @NotNull InspectionManager manager,
                                           boolean isOnTheFly,
                                           @NotNull SkillProblem problem) {
        return manager.createProblemDescriptor(
            file,
            TextRangeUtil.clamp(problem.range(), file.getTextLength()),
            problem.message(),
            toHighlightType(problem.severity()),
            isOnTheFly,
            toQuickFixes(problem.fixTypes())
        );
    }

    /**
     * 将内部严重程度映射到 IDE 高亮类型
     */
    @NotNull
    private ProblemHighlightType toHighlightType(@NotNull SkillSeverity severity) {
        return switch (severity) {
            case ERROR -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
            case WARNING -> ProblemHighlightType.WARNING;
            case WEAK_WARNING -> ProblemHighlightType.WEAK_WARNING;
        };
    }

    /**
     * 创建 Quick Fix 数组
     */
    @NotNull
    private LocalQuickFix[] toQuickFixes(@NotNull List<SkillFixType> fixTypes) {
        return fixTypes.stream()
            .map(SkillQuickFix::new)
            .toArray(LocalQuickFix[]::new);
    }

}
