package dev.dong4j.idea.skill.inspector.statusbar;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;

import dev.dong4j.idea.skill.inspector.settings.SkillInspectorSettings;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Skill Inspector 状态栏组件
 * <p> 使用 {@link StatusBarWidget.MultipleTextValuesPresentation} 提供一个简单下拉菜单,
 * 当前只包含“启用/禁用 SKILL.md 检查”两个状态. 后续如果增加 Profile, 可在同一菜单中继续扩展.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public class SkillInspectorStatusBarWidget implements StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {

    /** 所属项目 */
    private final Project project;

    /** 当前状态栏实例, 用于切换后刷新展示文本 */
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
    public String getSelectedValue() {
        return SkillInspectorSettings.getInstance().isSkillInspectionEnabled()
            ? SkillInspectorBundle.message("statusbar.enabled")
            : SkillInspectorBundle.message("statusbar.disabled");
    }

    @Override
    @Nullable
    public ListPopup getPopupStep() {
        return JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(
            SkillInspectorBundle.message("statusbar.popup.title"),
            List.of(
                new ToggleOption(true, "statusbar.action.enable"),
                new ToggleOption(false, "statusbar.action.disable")
            )
        ) {
            @Override
            @NotNull
            public String getTextFor(ToggleOption value) {
                return SkillInspectorBundle.message(value.messageKey());
            }

            @Override
            public PopupStep<?> onChosen(ToggleOption selectedValue, boolean finalChoice) {
                SkillInspectorSettings.getInstance().setSkillInspectionEnabled(selectedValue.enabled());
                refresh();
                return FINAL_CHOICE;
            }
        });
    }

    @Override
    @Nullable
    public String getTooltipText() {
        return SkillInspectorBundle.message("statusbar.tooltip");
    }

    /**
     * 切换设置后刷新状态栏文本
     */
    private void refresh() {
        if (statusBar != null && !project.isDisposed()) {
            statusBar.updateWidget(ID());
        }
    }

    /**
     * 状态栏开关菜单项
     * <p> 菜单项用布尔值表达真实行为, 文案 key 只负责展示, 避免用本地化字符串反推业务状态.
     *
     * @param enabled    选择该项后是否启用检查
     * @param messageKey 菜单展示文案 key
     */
    private record ToggleOption(boolean enabled, String messageKey) {
    }
}
