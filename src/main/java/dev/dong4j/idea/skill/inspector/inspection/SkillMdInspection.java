package dev.dong4j.idea.skill.inspector.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementVisitor;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * IDE daemon 可能在同一份 Markdown 文件、同一份文本版本上并发启动多轮 LocalInspection 会话。
     * <p>这些会话来自 IDE 外层调度, 单次 {@link RuleRunner} 与单个 {@link ProblemsHolder}
     * 内部都无法看见彼此, 所以只能在 inspection 适配层做一个短窗口防抖。key 包含文件路径、
     * PSI modification stamp、ruleId、range 和 message, 文本一变就会自然失效, 不影响重新检查.
     */
    private static final Map<String, Long> RECENT_PROBLEM_KEYS = new ConcurrentHashMap<>();

    /** 重复 daemon 会话通常在毫秒级完成; 这个窗口只用于挡同一批 descriptor 的并发重复写入 */
    private static final long DUPLICATE_SUPPRESSION_WINDOW_MILLIS = 1_000L;

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

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                inspectFile(file, holder, isOnTheFly);
            }
        };
    }

    /**
     * 按文件粒度注册问题.
     * <p>Skill 规则天然是 whole-file 语义, 不依赖逐 PSI 节点遍历. 使用 visitor 的
     * {@link PsiElementVisitor#visitFile(PsiFile)} 作为唯一入口, 可以避开 {@code checkFile}
     * 与 daemon visitor 调度交叉时在 Problems View 中产生重复 descriptor 的风险.
     */
    private void inspectFile(@NotNull PsiFile file, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SkillInspectorSettings.getInstance().isSkillInspectionEnabled() || !SkillFileDetector.isSkillFile(file)) {
            return;
        }

        SkillFile skillFile = SkillModelBuilder.build(file);
        for (SkillProblem problem : ruleRunner.run(skillFile)) {
            if (isDuplicateDaemonProblem(file, problem)) {
                continue;
            }
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
    }

    /**
     * 判断当前 problem 是否是 IDE 外层重复 daemon 会话产生的同一条 descriptor.
     *
     * @param file    被检查的 SKILL.md
     * @param problem 规则层输出的问题
     * @return true 表示短时间内同一文件版本已注册过完全相同的问题, 本次应跳过
     */
    private static boolean isDuplicateDaemonProblem(@NotNull PsiFile file, @NotNull SkillProblem problem) {
        long now = System.currentTimeMillis();
        cleanupRecentProblemKeys(now);

        String key = duplicateKey(file, problem);
        Long previous = RECENT_PROBLEM_KEYS.putIfAbsent(key, now);
        if (previous == null) {
            return false;
        }
        if (now - previous <= DUPLICATE_SUPPRESSION_WINDOW_MILLIS) {
            return true;
        }
        RECENT_PROBLEM_KEYS.put(key, now);
        return false;
    }

    /**
     * 构造跨 inspection 会话稳定的 problem key.
     */
    @NotNull
    private static String duplicateKey(@NotNull PsiFile file, @NotNull SkillProblem problem) {
        VirtualFile virtualFile = file.getVirtualFile();
        String path = virtualFile == null ? file.getName() : virtualFile.getPath();
        long modificationStamp = file.getViewProvider().getModificationStamp();
        return path + "#" + modificationStamp
            + "#" + problem.ruleId()
            + "@" + problem.range().getStartOffset() + "-" + problem.range().getEndOffset()
            + "#" + problem.message();
    }

    /**
     * 控制静态防抖表大小, 避免长时间 IDE 会话中积累无用 key.
     */
    private static void cleanupRecentProblemKeys(long now) {
        RECENT_PROBLEM_KEYS.entrySet().removeIf(
            entry -> now - entry.getValue() > DUPLICATE_SUPPRESSION_WINDOW_MILLIS
        );
    }

    /**
     * 将内部严重程度映射到 IDE 高亮类型
     */
    @NotNull
    private ProblemHighlightType toHighlightType(@NotNull SkillSeverity severity) {
        return switch (severity) {
            case ERROR -> ProblemHighlightType.ERROR;
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
