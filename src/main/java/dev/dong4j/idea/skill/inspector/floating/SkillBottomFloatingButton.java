package dev.dong4j.idea.skill.inspector.floating;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import dev.dong4j.idea.skill.inspector.PluginContents;
import dev.dong4j.idea.skill.inspector.detection.SkillFileDetector;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;
import icons.SkillInspectorIcons;

/**
 * SKILL.md 编辑器右下角浮动按钮 (自定义 Swing 注入 + 毛玻璃质感)
 *
 * <p>视觉设计: 圆角矩形 + icon + 文本, 毛玻璃 (frosted glass) 背景, 与 macOS Control Center / iOS
 * 控件视觉一致, 在编辑器文本上有"浮动"质感. 比纯色填充更显眼又不喧宾夺主.
 *
 * <p>毛玻璃实现要点:
 * <ol>
 *   <li>每次 {@link #paintComponent} 抓取按钮下方 editor 内容 ({@code editor.getContentComponent})
 *       到 {@link BufferedImage} (而非整个 RootPane, 否则性能爆炸).</li>
 *   <li>对抓取的图像做高斯模糊 ({@link ConvolveOp} + 自构 Gaussian {@link Kernel}).</li>
 *   <li>用 {@link AlphaComposite} 叠半透明色 (浅色主题加白, 暗色加灰), 提高文本可读性.</li>
 *   <li>{@link #BLUR_THROTTLE_MS} 节流: paint 间隔 < 阈值时复用缓存, 避免编辑器频繁重绘时
 *       做无效模糊计算.</li>
 *   <li>主动失效缓存: 监听 editor {@link VisibleAreaListener} (滚动) 与 {@link DocumentListener}
 *       (内容修改), 第一时间让毛玻璃刷新, 避免出现"模糊背景与下方内容不一致"的视觉错位.</li>
 * </ol>
 *
 * <p>挂载与生命周期约束 (与之前实现一致):
 * <ul>
 *   <li>仅 {@link EditorKind#MAIN_EDITOR} 且文件名为 {@code SKILL.md} 才挂载.</li>
 *   <li>挂在 {@link JRootPane#getLayeredPane()}; 不能用 ancestor 搜索, 否则会命中
 *       {@code TextEditorComponent} (禁止外部 add, 抛 {@link IllegalCallerException}).</li>
 *   <li>等待时机用 {@link HierarchyEvent#SHOWING_CHANGED}, 不是 {@code PARENT_CHANGED}.</li>
 *   <li>tab 切换 / 关闭文件时按钮跟随 {@code editorComp.isShowing()} 隐藏.</li>
 *   <li>编辑器释放统一由 {@link SkillBottomFloatingInstaller#editorReleased} 触发清理.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.05.25
 * @since 1.0.0
 */
public final class SkillBottomFloatingButton extends JBLabel {

    private static final Logger LOG = Logger.getInstance(SkillBottomFloatingButton.class);

    /** 圆角半径 (dip) */
    private static final int CORNER_RADIUS_DIP = 12;

    /** 按钮高度 (dip): 跟 IDE 默认 toolbar 按钮的视觉高度对齐, 又略大让按钮明显 */
    private static final int BUTTON_HEIGHT_DIP = 36;

    /** 内边距 (dip): 控制 icon 左 / 文本右的留白 */
    private static final int PADDING_HORIZONTAL_DIP = 14;
    private static final int PADDING_VERTICAL_DIP = 8;

    /** icon 与文本之间的空隙 (dip) */
    private static final int ICON_TEXT_GAP_DIP = 8;

    /** 距编辑器右边距 (dip), 避开默认垂直滚动条 + 留视觉留白 */
    private static final int RIGHT_MARGIN_DIP = 26;

    /** 距编辑器下边距 (dip) */
    private static final int BOTTOM_MARGIN_DIP = 18;

    /** 高斯模糊半径 (像素): 数值越大模糊越重, 但 ConvolveOp 复杂度是 O(N^2), 12 已经很糊且性能可接受 */
    private static final int BLUR_RADIUS = 12;

    /** 节流: 两次毛玻璃重算最短间隔 (毫秒), 限制最高 ~10 fps, 避免编辑器频繁 repaint 时跑无效模糊 */
    private static final long BLUR_THROTTLE_MS = 100;

    /**
     * editor → cleanup 的全局映射.
     * 由 {@link SkillBottomFloatingInstaller#editorReleased} 统一触发.
     */
    private static final ConcurrentMap<Editor, Disposable> CLEANUPS = new ConcurrentHashMap<>();

    /** 当前是否处于鼠标悬停状态, 影响 overlay 半透明度 */
    private boolean hovered = false;

    /** 持有 editor 引用以便: 1) 抓取 contentComponent 做毛玻璃 2) 注册滚动/文档监听 */
    private final Editor editor;

    /** 缓存的毛玻璃图像, paint 时优先复用 */
    @Nullable
    private BufferedImage cachedBlur;

    /** 上次重算毛玻璃的时间戳, 配合 {@link #BLUR_THROTTLE_MS} 做节流 */
    private long lastBlurTimeMs = 0L;

    /**
     * 私有构造: 必须通过 {@link #attach(Editor)} 创建, 确保生命周期被纳管.
     * <p>按钮形态: 左侧 16x16 icon, 右侧粗体文本, 圆角矩形.
     */
    private SkillBottomFloatingButton(@NotNull Editor editor) {
        super(SkillInspectorBundle.message("action.validate.skill.title"),
            SkillInspectorIcons.SKILL_INSPECTOR_16,
            SwingConstants.LEADING);
        this.editor = editor;
        setOpaque(false);
        setIconTextGap(JBUIScale.scale(ICON_TEXT_GAP_DIP));
        setBorder(JBUI.Borders.empty(PADDING_VERTICAL_DIP, PADDING_HORIZONTAL_DIP));
        setToolTipText(SkillInspectorBundle.message("action.validate.skill.description"));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // 文本加粗让按钮更显眼, 且字号微调到 12pt 跟 IDE 工具栏按钮对齐
        setFont(getFont().deriveFont(Font.BOLD, JBUIScale.scale(12f)));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(@NotNull MouseEvent e) {
                hovered = true;
                invalidateBlurCache();   // 悬停时立刻刷新, 让用户感知到 hover 反馈
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

    /**
     * 按钮尺寸: 高度固定; 宽度根据 icon + 文本 + 边距自适应.
     * <p>不能完全依赖 super.getPreferredSize(), 因为 JBLabel 的高度可能小于我们要的 36dp,
     * 这里强制按 {@link #BUTTON_HEIGHT_DIP} 至少 36 dp 高.
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension natural = super.getPreferredSize();
        int targetH = JBUIScale.scale(BUTTON_HEIGHT_DIP);
        return new Dimension(natural.width, Math.max(natural.height, targetH));
    }

    /**
     * paint 流程: 毛玻璃背景 → 半透明色 overlay → 边框 → super.paintComponent 画 icon + 文本.
     */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int corner = JBUIScale.scale(CORNER_RADIUS_DIP);
            Shape clipShape = new RoundRectangle2D.Float(0, 0, w, h, corner, corner);

            // ---- 1. 毛玻璃背景: 抓取下方 editor 内容并高斯模糊 ----
            BufferedImage blur = ensureBlur(w, h);
            if (blur != null) {
                Shape oldClip = g2.getClip();
                g2.setClip(clipShape);
                g2.drawImage(blur, 0, 0, null);
                g2.setClip(oldClip);
            }

            // ---- 2. 半透明色 overlay: 提高文本可读性, 也是毛玻璃的"白纱"质感 ----
            // 浅色主题用白纱, 暗色主题用深灰纱; 悬停时略微提高不透明度让按钮"亮起来"
            float overlayAlpha = hovered ? 0.65f : 0.50f;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, overlayAlpha));
            g2.setColor(new JBColor(new Color(255, 255, 255), new Color(60, 62, 65)));
            g2.fill(clipShape);

            // ---- 3. 边框: 1px 半透明亮边, 强化"玻璃"反光感 ----
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setStroke(new BasicStroke(JBUIScale.scale(1f)));
            g2.setColor(new JBColor(new Color(255, 255, 255, 200), new Color(255, 255, 255, 60)));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, corner, corner));
        } finally {
            g2.dispose();
        }
        // 让 JBLabel 在毛玻璃之上绘制 icon + 文本
        super.paintComponent(g);
    }

    /**
     * 获取或重算毛玻璃图像. 通过 {@link #BLUR_THROTTLE_MS} + 缓存做节流, 避免每次 paint 都做重活.
     * <p>关键约束: 不能直接 paint() 整个 parent / RootPane, 因为那会重绘整个 IDE 帧, 性能爆炸;
     * 这里只抓取按钮下方的 editor content 区域.
     */
    @Nullable
    private BufferedImage ensureBlur(int w, int h) {
        long now = System.currentTimeMillis();
        if (cachedBlur != null
            && cachedBlur.getWidth() == w
            && cachedBlur.getHeight() == h
            && now - lastBlurTimeMs < BLUR_THROTTLE_MS) {
            return cachedBlur;
        }
        BufferedImage fresh = captureAndBlur(w, h);
        if (fresh != null) {
            cachedBlur = fresh;
            lastBlurTimeMs = now;
        }
        return cachedBlur;
    }

    /** 让外部触发 (滚动 / 文档变更 / hover) 时立即作废缓存, 下一次 paint 重算 */
    private void invalidateBlurCache() {
        cachedBlur = null;
    }

    /**
     * 抓取按钮下方 editor 内容 + 跑高斯模糊.
     * <p>失败时 (parent 不可用 / editor disposed / paint 抛异常) 返回 null, paint 流程会
     * 退化为"只画半透明 overlay 没有真模糊", 视觉退化但功能不挂.
     */
    @Nullable
    private BufferedImage captureAndBlur(int w, int h) {
        if (editor.isDisposed()) {
            return null;
        }
        JComponent source = editor.getContentComponent();
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return null;
        }
        // 按钮在 RootPane.layeredPane 上, 算出按钮左上角在 source 坐标系里的位置, 才能 translate
        Point originInSource = SwingUtilities.convertPoint(this, 0, 0, source);

        try {
            BufferedImage raw = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D rawG = raw.createGraphics();
            try {
                rawG.translate(-originInSource.x, -originInSource.y);
                source.paint(rawG);
            } finally {
                rawG.dispose();
            }
            return gaussianBlur(raw, BLUR_RADIUS);
        } catch (Throwable t) {
            // editor 在 paint 过程中被 dispose / 状态不一致都可能抛, 不能让 UI 因此崩
            LOG.debug("Failed to capture editor background for floating button blur", t);
            return null;
        }
    }

    /**
     * 标准高斯模糊: 构造 (2r+1)x(2r+1) 的 Gaussian 核, 用 {@link ConvolveOp} 一次卷积.
     * <p>EDGE_NO_OP 让边缘像素保持原样, 避免黑边; 对小尺寸按钮 (本场景 ~140x36) 是可接受的.
     */
    @NotNull
    private static BufferedImage gaussianBlur(@NotNull BufferedImage src, int radius) {
        if (radius <= 0) {
            return src;
        }
        int size = radius * 2 + 1;
        float[] data = new float[size * size];
        float sigma = radius / 2.5f;
        float twoSigmaSq = 2f * sigma * sigma;
        float sum = 0f;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                float v = (float) Math.exp(-(x * x + y * y) / twoSigmaSq);
                data[(y + radius) * size + (x + radius)] = v;
                sum += v;
            }
        }
        for (int i = 0; i < data.length; i++) {
            data[i] /= sum;
        }
        ConvolveOp op = new ConvolveOp(new Kernel(size, size, data), ConvolveOp.EDGE_NO_OP, null);
        return op.filter(src, null);
    }

    /**
     * 触发"校验当前 SKILL.md"动作.
     * <p>关键约束: <b>必须用 {@link SimpleDataContext} 显式注入按钮关联的 editor/file/project</b>,
     * 不能用 {@code DataManager.getDataContext(this)}. 因为按钮挂在 RootPane.layeredPane 上,
     * 不在任何 editor 的组件子树里, 普通 DataContext 沿组件树上溯只能拿到 IDE frame 级 context,
     * 最终拿到的是"当前 focus 编辑器"的文件 — 这就是"打开多个 SKILL.md 但点哪个按钮都校验同一个
     * 文件"的根因.
     */
    private void triggerValidateAction() {
        AnAction action = ActionManager.getInstance()
            .getAction(PluginContents.ACTION_VALIDATE_CURRENT_SKILL_ID);
        if (action == null) {
            LOG.warn("Validate Current Skill action not found: "
                + PluginContents.ACTION_VALIDATE_CURRENT_SKILL_ID);
            return;
        }
        Project project = editor.getProject();
        VirtualFile vf = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (project == null || project.isDisposed() || vf == null) {
            LOG.warn("Project or VirtualFile unavailable for floating button; editor=" + editor);
            return;
        }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);

        DataContext ctx = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, vf)
            .add(CommonDataKeys.EDITOR, editor)
            .add(CommonDataKeys.PSI_FILE, psiFile)
            .build();
        ActionUtil.invokeAction(action, ctx, ActionPlaces.UNKNOWN, null, null);
    }

    // ============================================================================
    // 生命周期挂载: attach / detach / install / doInstall
    // ============================================================================

    /**
     * 在编辑器右下角挂载浮动按钮; 不满足条件 (非 main editor / 非 SKILL.md / 无 project) 直接 no-op.
     * 调用方应在 {@code EditorFactoryListener#editorCreated} 中转发到本方法.
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
        if (editorComp.isShowing()) {
            installIfNotInstalled(editor, editorComp);
            return;
        }
        HierarchyListener[] holder = new HierarchyListener[1];
        holder[0] = e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
                return;
            }
            if (editorComp.isShowing()) {
                editorComp.removeHierarchyListener(holder[0]);
                installIfNotInstalled(editor, editorComp);
            }
        };
        editorComp.addHierarchyListener(holder[0]);
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
    private static JLayeredPane findRootLayeredPane(@NotNull JComponent editorComp) {
        JRootPane rootPane = SwingUtilities.getRootPane(editorComp);
        return rootPane == null ? null : rootPane.getLayeredPane();
    }

    private static void installIfNotInstalled(@NotNull Editor editor, @NotNull JComponent editorComp) {
        if (CLEANUPS.containsKey(editor)) {
            return;
        }
        JLayeredPane layered = findRootLayeredPane(editorComp);
        if (layered == null) {
            LOG.warn("RootPane layered pane not available, skip floating button for: " + editor);
            return;
        }
        doInstall(editor, editorComp, layered);
    }

    /**
     * 安装动作: 创建按钮 → 加到 RootPane layered pane → 计算并设置位置 →
     * 注册 component / hierarchy / scroll / document 监听 → 把 cleanup 登记到 {@link #CLEANUPS}.
     */
    private static void doInstall(@NotNull Editor editor,
                                  @NotNull JComponent editorComp,
                                  @NotNull JLayeredPane layered) {
        SkillBottomFloatingButton button = new SkillBottomFloatingButton(editor);
        layered.add(button, JLayeredPane.PALETTE_LAYER);

        Runnable reposition = () -> {
            boolean visible = editorComp.isShowing();
            button.setVisible(visible);
            if (!visible) {
                return;
            }
            int w = editorComp.getWidth();
            int h = editorComp.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            Dimension pref = button.getPreferredSize();
            int rightMargin = JBUIScale.scale(RIGHT_MARGIN_DIP);
            int bottomMargin = JBUIScale.scale(BOTTOM_MARGIN_DIP);
            Point pt = SwingUtilities.convertPoint(
                editorComp,
                w - pref.width - rightMargin,
                h - pref.height - bottomMargin,
                layered
            );
            button.setBounds(pt.x, pt.y, pref.width, pref.height);
            button.invalidateBlurCache();   // 位置变了, 下方背景必然变
            button.repaint();
        };
        reposition.run();

        ComponentAdapter resizer = new ComponentAdapter() {
            @Override public void componentResized(@NotNull ComponentEvent e) { reposition.run(); }
            @Override public void componentMoved(@NotNull ComponentEvent e)   { reposition.run(); }
            @Override public void componentShown(@NotNull ComponentEvent e)   { reposition.run(); }
            @Override public void componentHidden(@NotNull ComponentEvent e)  { reposition.run(); }
        };
        editorComp.addComponentListener(resizer);

        HierarchyListener visibilityListener = e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                reposition.run();
            }
        };
        editorComp.addHierarchyListener(visibilityListener);

        // 滚动时按钮下方 editor 内容会变, 主动失效毛玻璃缓存 + repaint, 让模糊与下方内容同步
        VisibleAreaListener scrollListener = (VisibleAreaEvent e) -> {
            button.invalidateBlurCache();
            button.repaint();
        };
        editor.getScrollingModel().addVisibleAreaListener(scrollListener);

        // 文档修改时同上 (例如用户改一段文字, 按钮下方的字也变了)
        DocumentListener documentListener = new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                button.invalidateBlurCache();
                button.repaint();
            }
        };
        editor.getDocument().addDocumentListener(documentListener);

        // 顺带把"父容器是 layered pane"做一次 sanity check: 万一未来 attach 路径变了, 这里能提示
        Container actualParent = button.getParent();
        if (!(actualParent instanceof JLayeredPane)) {
            LOG.warn("Floating button parent is not JLayeredPane: "
                + (actualParent == null ? "null" : actualParent.getClass().getName()));
        }

        Disposable cleanup = () -> {
            editorComp.removeComponentListener(resizer);
            editorComp.removeHierarchyListener(visibilityListener);
            editor.getScrollingModel().removeVisibleAreaListener(scrollListener);
            editor.getDocument().removeDocumentListener(documentListener);
            layered.remove(button);
            layered.revalidate();
            layered.repaint();
        };
        CLEANUPS.put(editor, cleanup);
    }
}
