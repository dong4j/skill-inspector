package dev.dong4j.idea.skill.inspector;

/**
 * 插件内容配置类
 * <p> 用于存储插件的基本标识信息, 包括插件 ID 和插件名称, 供插件系统识别和使用
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.01.02
 * @since 1.0.0
 */
public final class PluginContents {
    /**
     * 插件的唯一标识符
     * <p>
     * 该常量用于标识插件的唯一 ID, 在整个系统中是唯一的.
     */
    public static final String PLUGIN_ID = "dev.dong4j.idea.skill.inspector";
    /** 插件名称 */
    public static final String PLUGIN_NAME = "Skill Inspector";

    /**
     * "校验项目全部 SKILL.md"动作的 ID, 必须与 plugin.xml 中 {@code <action id="..."/>} 保持一致.
     * <p> 右键菜单 / 全局入口使用. 浮动按钮请使用 {@link #ACTION_VALIDATE_CURRENT_SKILL_ID}.
     */
    public static final String ACTION_VALIDATE_SKILL_ID =
        "dev.dong4j.idea.skill.inspector.action.SkillInspectorAction";

    /**
     * "仅校验当前 SKILL.md"动作的 ID. 专门给编辑器内浮动按钮使用 — 浮动按钮贴在某个 SKILL.md
     * 编辑器上, 语义就是"校验这个文件", 不应该扫整个项目.
     */
    public static final String ACTION_VALIDATE_CURRENT_SKILL_ID =
        "dev.dong4j.idea.skill.inspector.action.ValidateCurrentSkillFileAction";

    /**
     * 私有构造函数, 防止外部实例化
     * <p> 该类为工具类, 包含插件相关常量, 不允许被实例化
     */
    private PluginContents() {
    }
}
