package dev.dong4j.idea.skill.inspector.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

import org.jetbrains.annotations.NotNull;

import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;
import dev.dong4j.idea.skill.inspector.util.NotificationUtil;
import icons.SkillInspectorIcons;

/**
 * Skill 校验入口动作
 * <p> 这是插件初始化阶段保留的最小 Action, 用于后续接入 SKILL.md 规范检查流程.
 * <p> 该动作会在项目和文件可用时启用, 并在执行时显示当前文件的占位校验提示.
 * <p> 具体功能包括:
 * <ul>
 * <li> 初始化动作标题和描述 </li>
 * <li> 在项目和文件存在的情况下启用动作 </li>
 * <li> 执行动作时显示占位校验消息, 为后续 Inspection 接入保留入口 </li>
 * <li> 在项目或文件不存在时显示错误信息 </li>
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
     * 构造函数, 用于初始化 SkillInspectorAction 实例
     * <p> 通过调用父类构造函数设置 Action 的标题, 描述和图标
     *
     */
    public SkillInspectorAction() {
        super(
            SkillInspectorBundle.message("action.validate.skill.title"),
            SkillInspectorBundle.message("action.validate.skill.description"),
            SkillInspectorIcons.SKILL_INSPECTOR_16
        );
    }

    /**
     * 执行 Action 的操作逻辑
     * <p> 当用户在编辑器中右键点击文件并选择此 Action 时触发该方法.
     * 当前阶段只检查上下文是否可用并显示占位通知, 后续会替换为真实的 SKILL.md 校验流程.
     *
     * @param e AnActionEvent 事件对象, 包含触发 Action 的上下文信息
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null) {
            NotificationUtil.showError(project, SkillInspectorBundle.message("error.no.project"));
            return;
        }

        if (psiFile == null) {
            NotificationUtil.showError(project, SkillInspectorBundle.message("error.no.file"));
            return;
        }

        // 当前只保留插件初始化入口, 后续接入真实 SKILL.md 规范检查.
        String fileName = psiFile.getName();
        NotificationUtil.showInfo(project, SkillInspectorBundle.message("success.validation.placeholder", fileName));
    }

    /**
     * 更新操作按钮的可用状态
     * <p> 根据当前项目和文件的存在性, 设置该操作按钮是否可用
     *
     * @param e 事件对象, 包含当前的项目和文件信息
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        // 只有在有项目和文件时才启用
        e.getPresentation().setEnabled(project != null && psiFile != null);
    }
}
