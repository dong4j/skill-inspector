package dev.dong4j.idea.skill.inspector.statusbar;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;

import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;

import org.jetbrains.annotations.NotNull;

/**
 * Skill Inspector 状态栏组件工厂
 * <p> 注册一个轻量 Status Bar Widget, 用户可以直接在状态栏查看并切换
 * {@code SKILL.md} 格式检查开关.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public class SkillInspectorStatusBarWidgetFactory implements StatusBarWidgetFactory {

    /** 状态栏组件 ID */
    public static final String ID = "SkillInspectorStatusBar";

    @Override
    @NotNull
    public String getId() {
        return ID;
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return SkillInspectorBundle.message("statusbar.display.name");
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    @NotNull
    public StatusBarWidget createWidget(@NotNull Project project) {
        return new SkillInspectorStatusBarWidget(project);
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
        widget.dispose();
    }

}
