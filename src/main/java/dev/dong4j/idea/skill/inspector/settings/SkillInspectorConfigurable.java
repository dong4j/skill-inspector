package dev.dong4j.idea.skill.inspector.settings;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.components.JBCheckBox;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.BorderLayout;

import javax.swing.JComponent;
import javax.swing.JPanel;

import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;

/**
 * Skill Inspector 设置页
 * <p> 当前只提供一个全局开关, 用于启用或禁用 {@code SKILL.md} 格式检查. 状态栏只展示
 * 当前文件的问题计数, 不再承担开关职责; 临时关闭检查应使用 IDE 自带 Power Save Mode.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
public class SkillInspectorConfigurable implements SearchableConfigurable {

    /** 设置页 ID, 用于 Settings 搜索 */
    private static final String ID = "dev.dong4j.idea.skill.inspector.settings";

    /** 全局开关控件 */
    private JBCheckBox enabledCheckBox;

    @Override
    @NotNull
    public String getId() {
        return ID;
    }

    @Override
    @Nls
    public String getDisplayName() {
        return SkillInspectorBundle.message("settings.display.name");
    }

    @Override
    @Nullable
    public JComponent createComponent() {
        enabledCheckBox = new JBCheckBox(SkillInspectorBundle.message("settings.enable.inspection"));
        enabledCheckBox.setSelected(SkillInspectorSettings.getInstance().isSkillInspectionEnabled());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(enabledCheckBox, BorderLayout.NORTH);
        return panel;
    }

    @Override
    public boolean isModified() {
        return enabledCheckBox != null
               && enabledCheckBox.isSelected() != SkillInspectorSettings.getInstance().isSkillInspectionEnabled();
    }

    @Override
    public void apply() {
        if (enabledCheckBox != null) {
            SkillInspectorSettings.getInstance().setSkillInspectionEnabled(enabledCheckBox.isSelected());
        }
    }

    @Override
    public void reset() {
        if (enabledCheckBox != null) {
            enabledCheckBox.setSelected(SkillInspectorSettings.getInstance().isSkillInspectionEnabled());
        }
    }

    @Override
    public void disposeUIResources() {
        enabledCheckBox = null;
    }
}
