---
name: add-statusbar-widget
description: 在 IDEA 状态栏添加图标 / 文字 widget，含 Consumer 类型陷阱
---

# /add-statusbar-widget — 添加状态栏组件

在 IDEA 主窗口底部状态栏添加一个 widget。本项目的标准实现是 **`IconPresentation` + 单击切换**，避免弹二级菜单（用户最在意的是"
看一眼就知道开关状态"）。

## Usage

```
/add-statusbar-widget [WidgetName]
```

- `WidgetName` — 不带 `Widget` 后缀，例如 `SkillInspectorStatusBar`、`SkillModeIndicator`。

## Information Needed

1. **Widget ID** — 与 `plugin.xml` 中 `<statusBarWidgetFactory id="..."/>` 一致；project 范围全局唯一。
2. **展示形态** — `IconPresentation`（项目首选）/ `TextPresentation` / `MultipleTextValuesPresentation`。
3. **状态来源** — 一般是 `PersistentStateComponent` 设置（参考 `SkillInspectorSettings`）。
4. **点击行为** — 切换（toggle）/ 弹菜单 / 跳转设置 / 触发 Action。

## Files to Create/Modify

### 1. Factory：`src/main/java/dev/dong4j/idea/skill/inspector/statusbar/{Name}WidgetFactory.java`

```java
public class {Name}WidgetFactory implements StatusBarWidgetFactory {

    public static final String ID = "{WidgetId}";

    @Override
    @NotNull
    public String getId() {
        return ID;
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return SkillInspectorBundle.message("statusbar.{xxx}.display.name");
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    @NotNull
    public StatusBarWidget createWidget(@NotNull Project project) {
        return new {Name}Widget(project);
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
        widget.dispose();
    }
}
```

### 2. Widget：`src/main/java/dev/dong4j/idea/skill/inspector/statusbar/{Name}Widget.java`

#### 项目首选范式：图标 + 单击切换

```java
public class {Name}Widget implements StatusBarWidget, StatusBarWidget.IconPresentation {

    private final Project project;
    private StatusBar statusBar;

    public {Name}Widget(@NotNull Project project) {
        this.project = project;
    }

    @Override
    @NotNull
    public String ID() {
        return {Name}WidgetFactory.ID;
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
    public Icon getIcon() {
        return SkillInspectorSettings.getInstance().isXxxEnabled()
            ? AllIcons.General.InspectionsEye
            : AllIcons.General.InspectionsPowerSaveMode;
    }

    @Override
    @Nullable
    public String getTooltipText() {
        return SkillInspectorBundle.message(
            SkillInspectorSettings.getInstance().isXxxEnabled()
                ? "statusbar.tooltip.enabled"
                : "statusbar.tooltip.disabled"
        );
    }

    /**
     * ⚠️ 关键陷阱：返回类型必须是 com.intellij.util.Consumer，
     * 不能用 java.util.function.Consumer，否则 IDE 不会把它识别为点击回调，
     * 单击图标完全无响应（且不报错，最容易被坑）。
     */
    @Override
    @NotNull
    public Consumer<MouseEvent> getClickConsumer() {
        return event -> toggle();
    }

    private void toggle() {
        SkillInspectorSettings settings = SkillInspectorSettings.getInstance();
        settings.setXxxEnabled(!settings.isXxxEnabled());
        refresh();
    }

    private void refresh() {
        if (statusBar != null && !project.isDisposed()) {
            statusBar.updateWidget(ID());
        }
    }
}
```

**关键约束（务必逐条核对）**：

- **`Consumer<MouseEvent>` 必须 import `com.intellij.util.Consumer`**：用 `java.util.function.Consumer` 不会编译失败但点击完全无响应——这是状态栏
  widget 最常见的坑，IDE 默默吞掉。
- **`refresh()` 必须先判 `project.isDisposed()`**：用户关项目时 widget 还在执行回调会抛 `Cannot access disposed project`。
- **`dispose()` 必须把 `statusBar` 置 null**：之后不要再调它。
- **优先用 `AllIcons` 内置图标**：如 `AllIcons.General.InspectionsEye`、`AllIcons.General.InspectionsPowerSaveMode`，自动适配浅色 /
  深色主题；自定义图标参考 `/add-icon`。
- **图标对要有强对比**：开 / 关两个状态用户必须一眼区分，不要用"颜色相近的两只眼睛"。

#### 备选：`MultipleTextValuesPresentation`（弹下拉菜单）

如果一定要弹菜单（例如多 profile 切换），实现 `getPopup()` 返回 `JBPopup`，参考 `com.intellij.openapi.wm.impl.status.widget`。本项目 V1
已经从该模式精简到"单击切换"，新增 widget **优先用 `IconPresentation`**。

### 3. 注册：`src/main/resources/META-INF/plugin.xml`

```xml
<extensions defaultExtensionNs="com.intellij">
    <statusBarWidgetFactory id="{WidgetId}"
                            implementation="dev.dong4j.idea.skill.inspector.statusbar.{Name}WidgetFactory"/>
</extensions>
```

参考：

```34:37:src/main/resources/META-INF/plugin.xml
        <!-- 状态栏开关：id 必须与 SkillInspectorStatusBarWidgetFactory.getId() 一致，
             否则 verifyPluginStructure 会报错 -->
        <statusBarWidgetFactory id="SkillInspectorStatusBar"
                                implementation="dev.dong4j.idea.skill.inspector.statusbar.SkillInspectorStatusBarWidgetFactory"/>
```

**关键约束**：

- **plugin.xml 的 `id` 必须等于 Factory `getId()` 返回值**：不一致会被 `./gradlew verifyPlugin` 直接拦下。

### 4. 国际化

```properties
statusbar.{xxx}.display.name=My Widget
statusbar.tooltip.enabled=My Widget enabled (click to disable)
statusbar.tooltip.disabled=My Widget disabled (click to enable)
```

### 5. 设置联动

如果点击行为是切换设置，确保 `SkillInspectorSettings`（或同形 service）已经存在该开关。否则先走 `/add-setting`。

设置变更时通知所有 widget：在 `setXxxEnabled()` 内不需要主动调用，**当前 widget 通过自身 `refresh()` 即可**；如果有第三方组件需要联动，可以走
`MessageBus` 或 `ApplicationManager.getApplication().getMessageBus()` 发布主题。

## Build & Verify

```bash
./gradlew compileJava
./gradlew verifyPlugin    # 必跑，shortName / id 错位会被发现
./gradlew runIde
# IDE 状态栏右键 → "Status Bar Widgets" → 确认你的 widget 在列表里且可勾选
# 单击 widget → 图标 / tooltip 应立刻切换
```

## Troubleshooting

- **单击没反应**：99% 是 `Consumer<MouseEvent>` import 错了——核对是 `com.intellij.util.Consumer` 而不是 `java.util.function.Consumer`。
- **状态栏没看到 widget**：右键状态栏 → "Status Bar Widgets" 把它勾上；首次需要重启 IDE 实例。
- **`verifyPluginStructure` 报 `widget id mismatch`**：plugin.xml 的 `id` 与 Factory `getId()` 不一致。
- **图标在深色主题下看不清**：换 `AllIcons` 同语义图标，或为自定义图标提供 `_dark.svg` 变体（详见 `/add-icon`）。
- **`statusBar.updateWidget` 抛 NPE**：`install` 还没回调时就触发了 refresh，加 `if (statusBar != null) ...`。

## 参考代码

- Factory：`src/main/java/dev/dong4j/idea/skill/inspector/statusbar/SkillInspectorStatusBarWidgetFactory.java`
- Widget：`src/main/java/dev/dong4j/idea/skill/inspector/statusbar/SkillInspectorStatusBarWidget.java`
- 设置联动：`src/main/java/dev/dong4j/idea/skill/inspector/settings/SkillInspectorSettings.java`
