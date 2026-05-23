package dev.dong4j.idea.skill.inspector.statusbar;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.Consumer;

import dev.dong4j.idea.skill.inspector.settings.SkillInspectorSettings;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;

import javax.swing.Icon;

/**
 * Skill Inspector 状态栏组件
 * <p> 使用 {@link StatusBarWidget.IconPresentation} 提供一个轻量"图标 + 单击切换"控件,
 * 不再展示文字, 也不再弹出二级菜单, 用户单击图标即可在"启用 / 禁用 SKILL.md 检查"之间切换.
 * <p> 通过两个 IntelliJ 内置图标天然形成颜色对比:
 * <ul>
 *   <li>{@link AllIcons.General#InspectionsEye} —— 启用 (蓝色"眼睛", 主题原生表达"主动检查中")</li>
 *   <li>{@link AllIcons.General#InspectionsPowerSaveMode} —— 禁用 (灰色"眼睛", 主题原生表达"检查已停")</li>
 * </ul>
 * 这样可以避免自定义图标 / 颜色, 自动适配 IDE 浅色 / 深色主题.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public class SkillInspectorStatusBarWidget implements StatusBarWidget, StatusBarWidget.IconPresentation {

    /** 所属项目, 用于在 dispose 时安全地跳过刷新 */
    private final Project project;

    /** 当前状态栏实例, 用于切换后刷新展示 */
    private StatusBar statusBar;

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
    }

    @Override
    public void dispose() {
        statusBar = null;
    }

    @Override
    @Nullable
    public WidgetPresentation getPresentation() {
        return this;
    }

    @Override
    @NotNull
    public Icon getIcon() {
        return SkillInspectorSettings.getInstance().isSkillInspectionEnabled()
            ? AllIcons.General.InspectionsEye
            : AllIcons.General.InspectionsPowerSaveMode;
    }

    @Override
    @Nullable
    public String getTooltipText() {
        return SkillInspectorBundle.message(
            SkillInspectorSettings.getInstance().isSkillInspectionEnabled()
                ? "statusbar.tooltip.enabled"
                : "statusbar.tooltip.disabled"
        );
    }

    /**
     * 单击图标即切换开关.
     * <p> 关键约束: 返回类型必须是 {@link com.intellij.util.Consumer}, 而不是
     * {@link java.util.function.Consumer}, 否则 IntelliJ 不会把它当成点击回调.
     *
     * @return 单击回调
     */
    @Override
    @NotNull
    public Consumer<MouseEvent> getClickConsumer() {
        return event -> toggle();
    }

    /**
     * 切换全局开关并刷新状态栏图标
     */
    private void toggle() {
        SkillInspectorSettings settings = SkillInspectorSettings.getInstance();
        settings.setSkillInspectionEnabled(!settings.isSkillInspectionEnabled());
        refresh();
    }

    /**
     * 通知 StatusBar 重新拉取 icon / tooltip
     */
    private void refresh() {
        if (statusBar != null && !project.isDisposed()) {
            statusBar.updateWidget(ID());
        }
    }
}
