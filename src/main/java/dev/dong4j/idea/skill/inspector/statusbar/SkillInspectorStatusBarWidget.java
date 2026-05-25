package dev.dong4j.idea.skill.inspector.statusbar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBusConnection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import dev.dong4j.idea.skill.inspector.detection.SkillFileDetector;
import dev.dong4j.idea.skill.inspector.model.SkillFile;
import dev.dong4j.idea.skill.inspector.model.SkillProblem;
import dev.dong4j.idea.skill.inspector.model.SkillSeverity;
import dev.dong4j.idea.skill.inspector.parser.SkillModelBuilder;
import dev.dong4j.idea.skill.inspector.rules.RuleRunner;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;

/**
 * Skill Inspector 状态栏组件
 * <p>状态栏不再承担"启用 / 禁用检查"职责, 该能力交给 IDE 自带 Power Save Mode
 * 和 Settings 总开关。本组件只展示当前编辑器文件的 Skill Inspector 计数:
 * <ul>
 *   <li>当前文件不是 {@code SKILL.md}: 显示 {@code Skill: N/A}</li>
 *   <li>当前文件是 {@code SKILL.md}: 显示 {@code Skill: xE/yW}</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public class SkillInspectorStatusBarWidget implements StatusBarWidget, StatusBarWidget.TextPresentation {

    /** 所属项目, 用于在 dispose 时安全地跳过刷新 */
    private final Project project;

    /** 默认规则执行器 */
    private final RuleRunner ruleRunner = new RuleRunner();

    /** 当前状态栏实例, 用于刷新当前文件的问题计数 */
    private StatusBar statusBar;

    /** 文件切换消息连接, widget dispose 时释放 */
    private MessageBusConnection messageBusConnection;

    /**
     * 创建状态栏组件
     *
     * @param project 所属项目
     */
    public SkillInspectorStatusBarWidget(@NotNull Project project) {
        this.project = project;
    }

    @Override
    @NotNull
    public String ID() {
        return SkillInspectorStatusBarWidgetFactory.ID;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
        this.statusBar = statusBar;
        messageBusConnection = project.getMessageBus().connect(this);
        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                refresh();
            }

            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                refresh();
            }
        });
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                refresh();
            }
        }, this);
    }

    @Override
    public void dispose() {
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
            messageBusConnection = null;
        }
        statusBar = null;
    }

    @Override
    @Nullable
    public WidgetPresentation getPresentation() {
        return this;
    }

    @Override
    @NotNull
    public String getText() {
        StatusInfo statusInfo = currentStatus();
        if (!statusInfo.skillFile()) {
            return SkillInspectorBundle.message("statusbar.text.na");
        }
        return SkillInspectorBundle.message("statusbar.text.count", statusInfo.errors(), statusInfo.warnings());
    }

    @Override
    @Nullable
    public String getTooltipText() {
        StatusInfo statusInfo = currentStatus();
        if (!statusInfo.skillFile()) {
            return SkillInspectorBundle.message("statusbar.tooltip.na");
        }
        return SkillInspectorBundle.message("statusbar.tooltip.count", statusInfo.errors(), statusInfo.warnings());
    }

    @Override
    public float getAlignment() {
        return 0.5F;
    }

    /**
     * 计算当前编辑器文件的问题计数.
     * <p>规则执行会访问 PSI, 必须放在 read action 内. Dumb 模式下跳过计算, 避免索引未就绪时
     * 状态栏反复触发规则链路.
     */
    @NotNull
    private StatusInfo currentStatus() {
        if (project.isDisposed() || DumbService.isDumb(project)) {
            return StatusInfo.notSkillFile();
        }
        VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
        if (selectedFiles.length == 0 || !SkillFileDetector.SKILL_FILE_NAME.equals(selectedFiles[0].getName())) {
            return StatusInfo.notSkillFile();
        }

        return ApplicationManager.getApplication().runReadAction((com.intellij.openapi.util.Computable<StatusInfo>) () -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(selectedFiles[0]);
            if (psiFile == null || !SkillFileDetector.isSkillFile(psiFile)) {
                return StatusInfo.notSkillFile();
            }
            SkillFile skillFile = SkillModelBuilder.build(psiFile);
            List<SkillProblem> problems = ruleRunner.run(skillFile);
            int errors = 0;
            int warnings = 0;
            for (SkillProblem problem : problems) {
                if (problem.severity() == SkillSeverity.ERROR) {
                    errors++;
                } else {
                    warnings++;
                }
            }
            return StatusInfo.skillFile(errors, warnings);
        });
    }

    /**
     * 通知 StatusBar 重新拉取 text / tooltip.
     * <p>文档事件可能发生在写操作过程中, 直接同步刷新会让状态栏在事件栈内重新读取 PSI.
     * 延迟到当前事件之后执行, 可以让 IDE 先完成文档提交和 daemon 调度.
     */
    private void refresh() {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (statusBar != null && !project.isDisposed()) {
                statusBar.updateWidget(ID());
            }
        });
    }

    /**
     * 状态栏展示模型.
     */
    private record StatusInfo(boolean skillFile, int errors, int warnings) {

        @NotNull
        static StatusInfo notSkillFile() {
            return new StatusInfo(false, 0, 0);
        }

        @NotNull
        static StatusInfo skillFile(int errors, int warnings) {
            return new StatusInfo(true, errors, warnings);
        }
    }
}
