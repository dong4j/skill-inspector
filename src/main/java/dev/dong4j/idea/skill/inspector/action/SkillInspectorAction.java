package dev.dong4j.idea.skill.inspector.action;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import dev.dong4j.idea.skill.inspector.detection.SkillFileDetector;
import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillProblem;
import dev.dong4j.idea.skill.inspector.model.SkillSeverity;
import dev.dong4j.idea.skill.inspector.parser.SkillModelBuilder;
import dev.dong4j.idea.skill.inspector.rules.RuleRunner;
import dev.dong4j.idea.skill.inspector.util.NotificationUtil;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;
import icons.SkillInspectorIcons;

/**
 * Skill 校验入口动作
 * <p> 用户右键 "Validate Skill" 时触发: 扫描当前项目里所有 {@code SKILL.md} 文件,
 * 运行 {@link RuleRunner} 收集问题, 汇总 Error / Warning 数量并通过通知反馈给用户.
 * <p> 关键设计:
 * <ul>
 *   <li>在 read action 内通过 {@link FilenameIndex} 收集所有 SKILL.md, 兼容未打开的文件.</li>
 *   <li>对已打开的文件触发 {@link DaemonCodeAnalyzer#restart(PsiFile)},
 *       让 Problems 面板自动更新, 不需要用户重新打开文件.</li>
 *   <li>扫描完毕后激活 Problems View, 把汇总结果以通知形式弹出, 并保留点击跳转到第一个有错文件的能力.</li>
 *   <li>整个动作在后台 {@link Task.Backgroundable} 内执行, 大项目下不会卡 UI.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.01.02
 * @since 1.0.0
 */
public class SkillInspectorAction extends AnAction {

    /**
     * 构造函数, 初始化 Action 的标题, 描述与图标
     */
    public SkillInspectorAction() {
        super(
            SkillInspectorBundle.message("action.validate.skill.title"),
            SkillInspectorBundle.message("action.validate.skill.description"),
            SkillInspectorIcons.SKILL_INSPECTOR_16
        );
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            NotificationUtil.showError(null, SkillInspectorBundle.message("error.no.project"));
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(
            project,
            SkillInspectorBundle.message("action.validate.skill.title"),
            true
        ) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ValidationSummary summary = ApplicationManager.getApplication()
                    .runReadAction((com.intellij.openapi.util.Computable<ValidationSummary>) () ->
                        scanProject(project, indicator));

                ApplicationManager.getApplication().invokeLater(() -> publishResult(project, summary));
            }
        });
    }

    /**
     * 扫描整个项目里所有 SKILL.md 并汇总规则结果
     */
    @NotNull
    private ValidationSummary scanProject(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
            "SKILL.md",
            GlobalSearchScope.projectScope(project)
        );

        if (files.isEmpty()) {
            return ValidationSummary.empty();
        }

        RuleRunner runner = new RuleRunner();
        int totalErrors = 0;
        int totalWarnings = 0;
        VirtualFile firstErrorFile = null;
        List<PsiFile> openedSkillFiles = new ArrayList<>();

        int index = 0;
        for (VirtualFile virtualFile : files) {
            indicator.checkCanceled();
            indicator.setFraction(files.isEmpty() ? 0.0 : (double) index++ / files.size());
            indicator.setText2(virtualFile.getPresentableUrl());

            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
            if (psiFile == null || !SkillFileDetector.isSkillFile(psiFile)) {
                continue;
            }

            openedSkillFiles.add(psiFile);

            SkillFile skillFile = SkillModelBuilder.build(psiFile);
            List<SkillProblem> problems = runner.run(skillFile);
            int fileErrors = 0;
            int fileWarnings = 0;
            for (SkillProblem problem : problems) {
                if (problem.severity() == SkillSeverity.ERROR) {
                    fileErrors++;
                } else {
                    fileWarnings++;
                }
            }
            if (fileErrors > 0 && firstErrorFile == null) {
                firstErrorFile = virtualFile;
            }
            totalErrors += fileErrors;
            totalWarnings += fileWarnings;
        }

        return new ValidationSummary(files.size(), totalErrors, totalWarnings, firstErrorFile, openedSkillFiles);
    }

    /**
     * 在 EDT 上发布扫描结果: 激活 Problems View + 触发已打开文件的 daemon 重启 + 弹通知
     */
    private void publishResult(@NotNull Project project, @NotNull ValidationSummary summary) {
        if (project.isDisposed()) {
            return;
        }

        if (summary.totalFiles == 0) {
            NotificationUtil.showInfo(project, SkillInspectorBundle.message("action.validate.skill.no.skills"));
            return;
        }

        // 触发已打开 SKILL.md 的 daemon 重启, Problems View 会自动展示这些文件的最新问题.
        DaemonCodeAnalyzer daemon = DaemonCodeAnalyzer.getInstance(project);
        for (PsiFile psiFile : summary.openedSkillFiles) {
            if (psiFile.isValid()) {
                daemon.restart(psiFile);
            }
        }

        ToolWindow problems = ToolWindowManager.getInstance(project).getToolWindow("Problems View");
        if (problems != null) {
            problems.show();
        }

        String message = SkillInspectorBundle.message(
            "action.validate.skill.completed",
            summary.totalFiles,
            summary.totalErrors,
            summary.totalWarnings
        );

        // 通知图标按汇总结果的最高严重度选择, 严格对应 IDE 内置 NotificationType:
        //   有 error -> ERROR (红色 X), 仅 warning -> WARNING (黄色 !), 全干净 -> INFORMATION (蓝色 i)
        if (summary.totalErrors > 0 && summary.firstErrorFile != null) {
            NotificationUtil.showError(project, message);
            // 自动把第一个有错的 SKILL.md 打到编辑器前台, 方便用户立刻处理.
            FileEditorManager.getInstance(project).openFile(summary.firstErrorFile, true);
        } else if (summary.totalErrors > 0) {
            NotificationUtil.showError(project, message);
        } else if (summary.totalWarnings > 0) {
            NotificationUtil.showWarning(project, message);
        } else {
            NotificationUtil.showInfo(project, message);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 仅在有 project 时启用; 当前文件不必是 SKILL.md, 因为 Action 会扫整个项目.
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    @NotNull
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * 扫描结果汇总
     * <p> 用普通类(非 record) 是为了在 read action 与 EDT 之间方便传递,
     * 同时保持字段可读名而不是泛型 tuple.
     */
    private static final class ValidationSummary {
        final int totalFiles;
        final int totalErrors;
        final int totalWarnings;
        @Nullable
        final VirtualFile firstErrorFile;
        final List<PsiFile> openedSkillFiles;

        ValidationSummary(int totalFiles,
                          int totalErrors,
                          int totalWarnings,
                          @Nullable VirtualFile firstErrorFile,
                          @NotNull List<PsiFile> openedSkillFiles) {
            this.totalFiles = totalFiles;
            this.totalErrors = totalErrors;
            this.totalWarnings = totalWarnings;
            this.firstErrorFile = firstErrorFile;
            this.openedSkillFiles = openedSkillFiles;
        }

        @NotNull
        static ValidationSummary empty() {
            return new ValidationSummary(0, 0, 0, null, List.of());
        }
    }
}
