package dev.dong4j.idea.skill.inspector.floating;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider;
import com.intellij.openapi.vfs.VirtualFile;

import org.jetbrains.annotations.NotNull;

import dev.dong4j.idea.skill.inspector.detection.SkillFileDetector;

/**
 * SKILL.md 编辑器内浮动工具栏 Provider
 * <p>
 * 借助 IntelliJ Platform 官方扩展点 {@code com.intellij.editorFloatingToolbarProvider} 将
 * {@link dev.dong4j.idea.skill.inspector.action.SkillInspectorAction} 以浮动按钮的形式
 * 暴露在 SKILL.md 编辑器右上角, 鼠标移入时淡入, 移出后按平台默认时间淡出.
 *
 * <p>关键约束:
 * <ul>
 *   <li>{@link #isApplicable(DataContext)} 仅对文件名等于 {@code SKILL.md} 的文件生效,
 *       与 {@link SkillFileDetector#isSkillFile} 保持一致, 避免在普通 Markdown 上误显示.</li>
 *   <li>{@link #getActionGroup()} 通过 {@link ActionManager} 拉取 plugin.xml 中声明的
 *       {@code dev.dong4j.idea.skill.inspector.SkillFloatingGroup}, 同一个 Action 在右键菜单
 *       与浮动栏共用一份配置, 不重复维护.</li>
 *   <li>显式 override {@code getActionGroup} 是因为 {@link FloatingToolbarProvider} 在
 *       Kotlin 中是抽象 val, 对 Java 来说仍是必须实现的 getter.</li>
 *   <li>位置由平台决定 (FlowLayout RIGHT 顶部), 想要"右下角"需自定义 JLayeredPane 路径,
 *       当前作为 PoC 接受官方默认位置.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.25
 * @since 1.0.0
 */
public final class SkillFloatingToolbarProvider implements FloatingToolbarProvider {

    private static final Logger LOG = Logger.getInstance(SkillFloatingToolbarProvider.class);

    /**
     * 浮动栏使用的 ActionGroup ID, 必须与 plugin.xml 中 {@code <group id="..."/>} 一致.
     * 抽出常量是为了让 plugin.xml 出错时能在日志里给出明确的对照线索.
     */
    public static final String FLOATING_GROUP_ID = "dev.dong4j.idea.skill.inspector.SkillFloatingGroup";

    /**
     * 返回平台用来渲染浮动按钮的 ActionGroup.
     * <p> 若 plugin.xml 中 group 注册缺失或类型不符, 这里会抛出
     * {@link IllegalStateException}: 这是 fail-fast 行为, 避免渲染出一个空浮动栏却没人发现.
     */
    @Override
    public @NotNull ActionGroup getActionGroup() {
        AnAction action = ActionManager.getInstance().getAction(FLOATING_GROUP_ID);
        if (!(action instanceof ActionGroup group)) {
            LOG.error("Floating toolbar action group not found or has wrong type: " + FLOATING_GROUP_ID);
            throw new IllegalStateException("Missing ActionGroup: " + FLOATING_GROUP_ID);
        }
        return group;
    }

    /**
     * 仅当当前编辑器正在编辑 SKILL.md 时显示.
     * <p> 这里直接复用 {@link SkillFileDetector#SKILL_FILE_NAME} 而不调用 {@code isSkillFile(PsiFile)},
     * 因为该回调只能拿到 {@link VirtualFile}; 文件名匹配已足够作为 V1 的门槛.
     */
    @Override
    public boolean isApplicable(@NotNull DataContext dataContext) {
        VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
        return file != null && SkillFileDetector.SKILL_FILE_NAME.equals(file.getName());
    }

    /**
     * 是否自动隐藏: 保留平台默认 {@code true}, 即鼠标移出编辑器后淡出, 移入再淡入.
     * <p> 若后续产品反馈希望"常驻按钮", 改为返回 false 即可, 无需额外改造.
     */
    @Override
    public boolean getAutoHideable() {
        return true;
    }
}
