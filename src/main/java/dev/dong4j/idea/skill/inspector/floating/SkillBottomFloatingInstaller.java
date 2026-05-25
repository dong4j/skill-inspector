package dev.dong4j.idea.skill.inspector.floating;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 浮动按钮的启动入口: 应用级生命周期监听器, 在 IDE 主窗口出现后挂载全局
 * {@link EditorFactoryListener}, 把 {@link SkillBottomFloatingButton#attach}
 * 拼接到每个新创建的编辑器上.
 *
 * <p>关键设计:
 * <ul>
 *   <li>用 {@link AppLifecycleListener#appFrameCreated} 而非 {@code ProjectActivity}
 *       是因为 Java 实现 {@code ProjectActivity} 需要 Kotlin suspend Continuation, 样板太多.
 *       AppLifecycleListener 触发时点已经足够晚 (Swing/EDT 就绪), 又比 service 构造函数更明确.</li>
 *   <li>用 {@link AtomicBoolean#compareAndSet} 防止重复注册: AppLifecycleListener 理论上只会
 *       触发一次, 但留个 fail-safe 让代码对未来扩展更友好.</li>
 *   <li>处理"已存在的编辑器"(进程恢复打开的文件): 注册监听后立刻遍历
 *       {@link EditorFactory#getAllEditors()}, 否则会议程恢复的 SKILL.md 拿不到按钮.</li>
 *   <li>parent disposable 用 {@code ApplicationManager.getApplication()}, 让 listener
 *       与应用同生命周期; 编辑器级清理在 {@link #editorReleased} 中调用
 *       {@link SkillBottomFloatingButton#detach}, 不在这里 disposer 上做.</li>
 * </ul>
 *
 * <p>plugin.xml 注册示例:
 * <pre>{@code
 *   <applicationListeners>
 *     <listener class="dev.dong4j.idea.skill.inspector.floating.SkillBottomFloatingInstaller"
 *               topic="com.intellij.ide.AppLifecycleListener"/>
 *   </applicationListeners>
 * }</pre>
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.25
 * @since 1.0.0
 */
public final class SkillBottomFloatingInstaller implements AppLifecycleListener {

    /** 应用级 listener 是否已注册, 避免多次挂载导致按钮重复 */
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }

        EditorFactory factory = EditorFactory.getInstance();
        factory.addEditorFactoryListener(new EditorFactoryListener() {
            @Override
            public void editorCreated(@NotNull EditorFactoryEvent event) {
                SkillBottomFloatingButton.attach(event.getEditor());
            }

            @Override
            public void editorReleased(@NotNull EditorFactoryEvent event) {
                SkillBottomFloatingButton.detach(event.getEditor());
            }
        }, ApplicationManager.getApplication());

        // 处理 IDE 重启后已恢复的编辑器: 这些编辑器的 editorCreated 事件早于本监听器注册,
        // 因此手动遍历一次, 让恢复的 SKILL.md 也能拿到按钮.
        for (Editor editor : factory.getAllEditors()) {
            SkillBottomFloatingButton.attach(editor);
        }
    }
}
