package dev.dong4j.idea.skill.inspector.action;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.FileContentUtil;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
 * 校验"当前 SKILL.md 文件"的动作.
 * <p>与项目级 {@link SkillInspectorAction} 的差异:
 * <ul>
 *   <li>本 Action 只校验 {@link AnActionEvent} DataContext 里的当前文件 (要求文件名为 {@code SKILL.md}),
 *       不扫整个项目;</li>
 *   <li>专门用于"编辑器内浮动按钮"场景 — 按钮贴在某个 SKILL.md 编辑器上, 语义自然是
 *       "校验这个文件"; 浮动按钮调用时会用
 *       {@code SimpleDataContext} 显式注入按钮对应的 editor/file/project,
 *       避免按钮挂在 RootPane.layeredPane 时拿到"当前 focus 编辑器"的 file 这一陷阱;</li>
 *   <li>校验完毕后触发 {@link DaemonCodeAnalyzer#restart} 让 Problems View 立即刷新,
 *       并以通知形式弹出"X errors, Y warnings"汇总.</li>
 * </ul>
 *
 * <p>跟项目级扫描 ({@link SkillInspectorAction}) 共享同一份 {@link RuleRunner} 与领域模型,
 * 不重复实现规则.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.25
 * @since 1.0.0
 */
public class ValidateCurrentSkillFileAction extends AnAction {

    public ValidateCurrentSkillFileAction() {
        super(
            SkillInspectorBundle.message("action.validate.current.skill.title"),
            SkillInspectorBundle.message("action.validate.current.skill.description"),
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
        VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (vf == null) {
            NotificationUtil.showError(project, SkillInspectorBundle.message("error.no.file"));
            return;
        }
        if (!SkillFileDetector.SKILL_FILE_NAME.equals(vf.getName())) {
            NotificationUtil.showWarning(project, SkillInspectorBundle.message("error.not.skill.file"));
            return;
        }

        // 关键: 在 EDT 上把 in-memory document 同步到 PSI; 否则 BGT 里拿到的 PsiFile 可能是 stale 的,
        // 导致 Action 校验结果与 Inspection / Problems View 不一致 (常见 IDE 坑).
        Document document = FileDocumentManager.getInstance().getDocument(vf);
        if (document != null) {
            PsiDocumentManager.getInstance(project).commitDocument(document);
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(
            project,
            SkillInspectorBundle.message("action.validate.current.skill.title"),
            true
        ) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                FileValidation result = ApplicationManager.getApplication()
                    .runReadAction((Computable<FileValidation>) () -> validateFile(project, vf));
                ApplicationManager.getApplication().invokeLater(() -> publishResult(project, vf, result));
            }
        });
    }

    /**
     * 在 read action 内构建领域模型 + 跑规则.
     * <p>优先用 {@code PsiDocumentManager.getPsiFile(document)} 而非 {@code PsiManager.findFile(vf)}:
     * 前者从 in-memory document 派生 PsiFile, 保证跟编辑器实际内容同步 (前提是上游已 commit document);
     * 后者基于 VFS, 可能 stale.
     */
    @NotNull
    private FileValidation validateFile(@NotNull Project project, @NotNull VirtualFile vf) {
        Document document = FileDocumentManager.getInstance().getDocument(vf);
        PsiFile psiFile = document != null
            ? PsiDocumentManager.getInstance(project).getPsiFile(document)
            : PsiManager.getInstance(project).findFile(vf);
        if (psiFile == null || !SkillFileDetector.isSkillFile(psiFile)) {
            return FileValidation.invalid();
        }
        SkillFile skillFile = SkillModelBuilder.build(psiFile);
        List<SkillProblem> problems = new RuleRunner().run(skillFile);
        return new FileValidation(true, psiFile, List.copyOf(problems));
    }

    /**
     * 在 EDT 上发布结果: 触发 daemon 重启 (Problems View 自动刷新) + 激活 Problems View + 弹通知.
     * <p>通知文案设计:
     * <ul>
     *   <li>文件标签用"父目录名/SKILL.md", 否则多个 skill 都叫 SKILL.md, 用户看不出校验的是哪个;</li>
     *   <li>有问题时列出 ruleId 列表, 便于跟 Problems View 逐条比对, 暴露任何
     *       "Action 跑出来的 problem 集合 vs Inspection 跑出来的不一致"问题.</li>
     * </ul>
     */
    private void publishResult(@NotNull Project project,
                               @NotNull VirtualFile vf,
                               @NotNull FileValidation result) {
        if (project.isDisposed() || !result.valid) {
            return;
        }
        // 关键: 强制 Markdown PSI 完整重解析, 再 restart daemon.
        // 仅 restart 不够: 如果文件在 Markdown frontmatter 节点懒构建之前已经被 daemon 跑过一次,
        // 旧的 ProblemDescriptor 会被缓存在 Problems View. PSI 节点结构变化但 document 没变,
        // restart 不会让 daemon 重新拿到"新 PSI"的视角. reparseFiles 才能彻底刷新.
        FileContentUtil.reparseFiles(project, Collections.singletonList(vf), true);
        if (result.psiFile != null && result.psiFile.isValid()) {
            DaemonCodeAnalyzer.getInstance(project).restart(result.psiFile);
        }
        ToolWindow problems = ToolWindowManager.getInstance(project).getToolWindow("Problems View");
        if (problems != null) {
            problems.show();
        }

        String fileLabel = vf.getParent() != null
            ? vf.getParent().getName() + "/" + vf.getName()
            : vf.getName();

        if (result.problems.isEmpty()) {
            NotificationUtil.showInfo(project,
                SkillInspectorBundle.message("action.validate.current.skill.clean", fileLabel));
            return;
        }

        int errors = 0;
        int warnings = 0;
        for (SkillProblem p : result.problems) {
            if (p.severity() == SkillSeverity.ERROR) {
                errors++;
            } else {
                warnings++;
            }
        }
        String ruleIds = result.problems.stream()
            .map(SkillProblem::ruleId)
            .distinct()
            .collect(Collectors.joining(", "));
        String msg = SkillInspectorBundle.message(
            "action.validate.current.skill.completed",
            fileLabel, errors, warnings, ruleIds
        );
        // 通知图标三档对应 IDE 内置 NotificationType: ERROR=红色 X, WARNING=黄色 !, INFORMATION=蓝色 i
        // 之前所有非空结果都走 showInfo/showWarning, 导致 error 也显示成警告图标, 跟语义不符
        if (errors > 0) {
            NotificationUtil.showError(project, msg);
        } else if (warnings > 0) {
            NotificationUtil.showWarning(project, msg);
        } else {
            NotificationUtil.showInfo(project, msg);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 仅在当前文件为 SKILL.md 时可用; 既保护手动从 Search Everywhere 触发, 也方便未来挂菜单
        VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
        e.getPresentation().setEnabledAndVisible(
            e.getProject() != null
                && vf != null
                && SkillFileDetector.SKILL_FILE_NAME.equals(vf.getName())
        );
    }

    @Override
    @NotNull
    public ActionUpdateThread getActionUpdateThread() {
        // update 只读 DataContext 不碰 PSI, BGT 即可
        return ActionUpdateThread.BGT;
    }

    /**
     * 单文件校验结果传输对象. 用普通类便于在 read action 与 EDT 之间携带可空 PsiFile.
     * <p>携带完整 {@code problems} 列表 (而非只数量), 让 {@link #publishResult} 能在通知里
     * 列出 ruleId, 方便定位 Action 与 Inspection 结果不一致的根因.
     */
    private static final class FileValidation {
        final boolean valid;
        @Nullable
        final PsiFile psiFile;
        @NotNull
        final List<SkillProblem> problems;

        FileValidation(boolean valid, @Nullable PsiFile psiFile, @NotNull List<SkillProblem> problems) {
            this.valid = valid;
            this.psiFile = psiFile;
            this.problems = problems;
        }

        @NotNull
        static FileValidation invalid() {
            return new FileValidation(false, null, List.of());
        }
    }
}
