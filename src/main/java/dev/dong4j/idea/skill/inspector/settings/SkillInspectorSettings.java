package dev.dong4j.idea.skill.inspector.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

import org.jetbrains.annotations.NotNull;

/**
 * Skill Inspector 应用级设置
 * <p> 当前只保存一个全局开关: 是否启用 {@code SKILL.md} 格式检查. 后续增加 profile、
 * 规则开关、最大正文长度等配置时, 应继续挂在该应用级状态下, 避免配置入口分散.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.23
 * @since 1.0.0
 */
@State(name = "SkillInspectorSettings", storages = @Storage("skillInspector.xml"))
public class SkillInspectorSettings implements PersistentStateComponent<SkillInspectorSettings.State> {

    /** 当前持久化状态 */
    private State state = new State();

    /**
     * 获取应用级设置服务
     *
     * @return Skill Inspector 设置服务
     */
    @NotNull
    public static SkillInspectorSettings getInstance() {
        return ApplicationManager.getApplication().getService(SkillInspectorSettings.class);
    }

    /**
     * 判断 SKILL.md 格式检查是否启用
     *
     * @return 启用时返回 true
     */
    public boolean isSkillInspectionEnabled() {
        return state.skillInspectionEnabled;
    }

    /**
     * 设置 SKILL.md 格式检查开关
     *
     * @param enabled true 表示启用检查, false 表示跳过所有 Skill Inspector 规则
     */
    public void setSkillInspectionEnabled(boolean enabled) {
        state.skillInspectionEnabled = enabled;
    }

    @Override
    @NotNull
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    /**
     * 持久化状态
     * <p> 字段必须保持 public, 这是 IntelliJ XML 序列化的约定.
     */
    public static class State {
        /** 是否启用 SKILL.md 格式检查 */
        public boolean skillInspectionEnabled = true;
    }
}
