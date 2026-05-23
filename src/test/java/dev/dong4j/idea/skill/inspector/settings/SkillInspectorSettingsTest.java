package dev.dong4j.idea.skill.inspector.settings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SkillInspectorSettings} 单元测试
 * <p> 覆盖全局检查开关的默认值、修改和持久化状态恢复, 确保 Settings 页面和 Status Bar
 * 操作的是同一个稳定状态模型.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
class SkillInspectorSettingsTest {

    @Test
    void shouldEnableInspectionByDefault() {
        SkillInspectorSettings settings = new SkillInspectorSettings();

        assertThat(settings.isSkillInspectionEnabled()).isTrue();
    }

    @Test
    void shouldUpdateInspectionSwitch() {
        SkillInspectorSettings settings = new SkillInspectorSettings();

        settings.setSkillInspectionEnabled(false);

        assertThat(settings.isSkillInspectionEnabled()).isFalse();
    }

    @Test
    void shouldRestorePersistedState() {
        SkillInspectorSettings settings = new SkillInspectorSettings();
        SkillInspectorSettings.State state = new SkillInspectorSettings.State();
        state.skillInspectionEnabled = false;

        settings.loadState(state);

        assertThat(settings.isSkillInspectionEnabled()).isFalse();
        assertThat(settings.getState()).isSameAs(state);
    }
}
