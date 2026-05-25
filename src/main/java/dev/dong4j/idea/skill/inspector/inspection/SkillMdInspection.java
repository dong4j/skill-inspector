package dev.dong4j.idea.skill.inspector.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;

import dev.dong4j.idea.skill.inspector.detection.SkillFileDetector;
import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillProblem;
import dev.dong4j.idea.skill.inspector.parser.SkillModelBuilder;
import dev.dong4j.idea.skill.inspector.rules.RuleRunner;
import dev.dong4j.idea.skill.inspector.settings.SkillInspectorSettings;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;

import org.jetbrains.annotations.NotNull;

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

    /** descriptor 映射器 */
    private final SkillInspectionProblemMapper problemMapper = new SkillInspectionProblemMapper();

    /** 跨 daemon inspection 会话的短窗口幂等保护 */
    private static final SkillInspectionDuplicateGuard DUPLICATE_GUARD = new SkillInspectionDuplicateGuard(1_000L);

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
    public boolean isAvailableForFile(@NotNull PsiFile file) {
        return SkillFileDetector.isSkillFile(file);
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
     * <p>Skill 规则天然是 whole-file 语义, 不依赖逐 PSI 节点遍历. 这里仅负责调度:
     * 构建领域模型、执行规则、通过幂等保护后交给 mapper 注册 descriptor.
     */
    private void inspectFile(@NotNull PsiFile file, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SkillInspectorSettings.getInstance().isSkillInspectionEnabled() || !SkillFileDetector.isSkillFile(file)) {
            return;
        }

        SkillFile skillFile = SkillModelBuilder.build(file);
        for (SkillProblem problem : ruleRunner.run(skillFile)) {
            SkillProblemKey key = SkillProblemKey.from(file, problem);
            if (!DUPLICATE_GUARD.shouldRegister(key)) {
                continue;
            }
            problemMapper.register(file, holder, isOnTheFly, problem);
        }
    }

}
