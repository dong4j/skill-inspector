package dev.dong4j.idea.skill.inspector.floating;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JLayeredPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.swing.JComponent;

import dev.dong4j.idea.skill.inspector.PluginContents;
import dev.dong4j.idea.skill.inspector.detection.SkillFileDetector;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;
import icons.SkillInspectorIcons;

/**
 * SKILL.md 编辑器右下角浮动 FAB 按钮 (方案 B: 自定义 Swing 注入)
 *
 * <p>与方案 A ({@link SkillFloatingToolbarProvider}) 形成对照:
 * 方案 A 走 IntelliJ 官方 {@code editorFloatingToolbarProvider} 扩展点, 位置由平台决定 (右上角);
 * 方案 B 自行拿到编辑器外层 {@link JLayeredPane}, 把按钮挂到 {@link JLayeredPane#PALETTE_LAYER},
 * 自己负责定位与生命周期, 因此可以精确放置在编辑器右下角.
 *
 * <p>关键约束:
 * <ul>
 *   <li>仅 {@link EditorKind#MAIN_EDITOR} 且文件名为 {@code SKILL.md} 才挂载, 避免在 diff / console 中误显示.</li>
 *   <li>挂载在 layered pane 的 {@code PALETTE_LAYER}, 高于编辑器主体, 确保不会被代码内容覆盖.</li>
 *   <li>定位通过 {@link SwingUtilities#convertPoint} 把编辑器右下角坐标转换到 layered pane 坐标系,
 *       这样无论编辑器是否在 split / preview 容器内都能算准位置.</li>
 *   <li>编辑器尺寸变化 (componentResized / componentMoved) 必须重新定位, 否则窗口拉伸后按钮"漂出"可视区.</li>
 *   <li>编辑器释放时必须从 layered pane 移除并解绑监听, 通过 {@link #CLEANUPS} 让外层
 *       {@link SkillBottomFloatingInstaller} 统一调度, 不依赖 {@code EditorEx} 等内部 API.</li>
 * </ul>
 *
 * <p>视觉设计: 36 dp 圆形, 主题色填充, 悬停加深背景, 内嵌 16x16 插件图标; 让方案 B 与方案 A 的
 * "灰色矩形浮动栏"形成明显视觉差异, 方便对比效果.
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.25
 * @since 1.0.0
 */
public final class SkillBottomFloatingButton extends JBLabel {

    private static final Logger LOG = Logger.getInstance(SkillBottomFloatingButton.class);

    /** 圆形按钮直径 (dip), 经过 JBUI.scale 适配 HiDPI */
    private static final int DIAMETER_DIP = 36;

    /**
     * 距编辑器右边距 (dip): 26 是为了让按钮避开默认垂直滚动条的宽度 (~12) + 视觉留白 (~14).
     * 实际滚动条不一定可见, 但用偏大的 margin 可以避免与滚动条 thumb 重叠.
     */
    private static final int RIGHT_MARGIN_DIP = 26;

    /** 距编辑器下边距 (dip) */
    private static final int BOTTOM_MARGIN_DIP = 16;

    /**
     * editor → cleanup runnable 的全局映射.
     * <p> 由 {@link SkillBottomFloatingInstaller#editorReleased} 调用,
     * 将"移除组件 + 解绑监听"集中到 EditorFactoryListener 触发, 避免依赖 EditorEx#getDisposable
     * 这类 {@code @ApiStatus.Internal} 入口.
     */
    private static final ConcurrentMap<Editor, Disposable> CLEANUPS = new ConcurrentHashMap<>();

    /** 当前是否处于鼠标悬停状态, 控制背景色加深 */
    private boolean hovered = false;

    /** 私有构造: 必须通过 {@link #attach(Editor)} 创建并挂载, 确保生命周期被纳管 */
    private SkillBottomFloatingButton() {
        super(SkillInspectorIcons.SKILL_INSPECTOR_16);
        setOpaque(false);
        setHorizontalAlignment(SwingConstants.CENTER);
        setVerticalAlignment(SwingConstants.CENTER);
        setToolTipText(SkillInspectorBundle.message("action.validate.skill.title"));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(@NotNull MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(@NotNull MouseEvent e) {
                hovered = false;
                repaint();
            }

            @Override
            public void mouseClicked(@NotNull MouseEvent e) {
                triggerValidateAction();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        int d = JBUI.scale(DIAMETER_DIP);
        return new Dimension(d, d);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            // 软阴影: 在按钮外圈画一圈半透明黑色, 让 FAB 浮起来
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, hovered ? 0.35f : 0.20f));
            g2.setColor(JBColor.BLACK);
            int shadowOffset = JBUI.scale(2);
            g2.fillOval(0, shadowOffset, w, h - shadowOffset);

            // 主体填充: 主题色, 暗色主题用同色 (蓝色在两个主题下都识别度高)
            g2.setComposite(AlphaComposite.SrcOver);
            Color base = new JBColor(new Color(0x4A90E2), new Color(0x4A90E2));
            Color bg = hovered ? base.darker() : base;
            g2.setColor(bg);
            g2.fillOval(0, 0, w, h - shadowOffset);

            // 描边: 让按钮在浅色背景上有边界感
            // JBUI.scale(float) 在 2024+ 已 deprecated, 改用 JBUIScale.scale(float).
            g2.setStroke(new BasicStroke(JBUIScale.scale(1f)));
            g2.setColor(new JBColor(new Color(0x2E6FCB), new Color(0x6BB0FF)));
            g2.drawOval(0, 0, w - 1, h - shadowOffset - 1);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    /**
     * 触发 Validate Skill 动作.
     * <p> 复用 {@link dev.dong4j.idea.skill.inspector.action.SkillInspectorAction}
     * 同一份 ActionManager 注册项, 不重复实现扫描逻辑.
     */
    private void triggerValidateAction() {
        AnAction action = ActionManager.getInstance()
            .getAction(PluginContents.ACTION_VALIDATE_SKILL_ID);
        if (action == null) {
            LOG.warn("Validate Skill action not found: " + PluginContents.ACTION_VALIDATE_SKILL_ID);
            return;
        }
        DataContext ctx = DataManager.getInstance().getDataContext(this);
        // 2024.2 API: invokeAction(action, dataContext, place, inputEvent, onDone)
        // 内部会自行构造 AnActionEvent 并触发完整 update → actionPerformed 流程.
        ActionUtil.invokeAction(action, ctx, ActionPlaces.UNKNOWN, null, null);
    }

    /**
     * 在编辑器右下角挂载浮动按钮; 不满足条件 (非 main editor / 非 SKILL.md / 无 project) 直接 no-op.
     * <p> 调用方应在 {@code EditorFactoryListener#editorCreated} 中转发到本方法.
     */
    public static void attach(@NotNull Editor editor) {
        if (editor.getEditorKind() != EditorKind.MAIN_EDITOR) {
            return;
        }
        Project project = editor.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }
        VirtualFile vf = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (vf == null || !SkillFileDetector.SKILL_FILE_NAME.equals(vf.getName())) {
            return;
        }

        JComponent editorComp = editor.getComponent();

        // 编辑器组件可能在 editorCreated 时尚未挂到 Swing 容器, 此时拿不到 layered pane.
        // 通过 HierarchyListener 延迟到第一次有 parent 时再安装.
        JLayeredPane existing = findLayeredPane(editorComp);
        if (existing != null) {
            doInstall(editor, editorComp, existing);
        } else {
            editorComp.addHierarchyListener(new java.awt.event.HierarchyListener() {
                @Override
                public void hierarchyChanged(HierarchyEvent e) {
                    if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) == 0) {
                        return;
                    }
                    JLayeredPane lp = findLayeredPane(editorComp);
                    if (lp != null) {
                        editorComp.removeHierarchyListener(this);
                        doInstall(editor, editorComp, lp);
                    }
                }
            });
        }
    }

    /**
     * 编辑器释放时调用: 移除按钮 + 解绑监听.
     * 由 {@link SkillBottomFloatingInstaller#editorReleased} 触发.
     */
    public static void detach(@NotNull Editor editor) {
        Disposable cleanup = CLEANUPS.remove(editor);
        if (cleanup != null) {
            Disposer.dispose(cleanup);
        }
    }

    @Nullable
    private static JLayeredPane findLayeredPane(@NotNull JComponent editorComp) {
        return (JLayeredPane) SwingUtilities.getAncestorOfClass(JLayeredPane.class, editorComp);
    }

    /**
     * 真正的安装动作: 创建按钮 → 加到 layered pane → 计算并设置位置 →
     * 注册 ComponentListener 监听 resize → 把 cleanup 登记到 {@link #CLEANUPS}.
     */
    private static void doInstall(@NotNull Editor editor,
                                  @NotNull JComponent editorComp,
                                  @NotNull JLayeredPane layered) {
        SkillBottomFloatingButton button = new SkillBottomFloatingButton();
        layered.add(button, JLayeredPane.PALETTE_LAYER);

        Runnable reposition = () -> {
            int w = editorComp.getWidth();
            int h = editorComp.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            Dimension pref = button.getPreferredSize();
            int rightMargin = JBUI.scale(RIGHT_MARGIN_DIP);
            int bottomMargin = JBUI.scale(BOTTOM_MARGIN_DIP);
            // 把"editor 内部右下角坐标"转换到 layered pane 坐标系, 适配 split / preview 等嵌套容器
            Point pt = SwingUtilities.convertPoint(
                editorComp,
                w - pref.width - rightMargin,
                h - pref.height - bottomMargin,
                layered
            );
            button.setBounds(pt.x, pt.y, pref.width, pref.height);
            button.repaint();
        };
        reposition.run();

        ComponentAdapter resizer = new ComponentAdapter() {
            @Override
            public void componentResized(@NotNull ComponentEvent e) {
                reposition.run();
            }

            @Override
            public void componentMoved(@NotNull ComponentEvent e) {
                reposition.run();
            }
        };
        editorComp.addComponentListener(resizer);

        // 统一登记 cleanup, 由 SkillBottomFloatingInstaller#editorReleased 触发 dispose.
        Disposable cleanup = () -> {
            editorComp.removeComponentListener(resizer);
            layered.remove(button);
            layered.revalidate();
            layered.repaint();
        };
        CLEANUPS.put(editor, cleanup);
    }
}
