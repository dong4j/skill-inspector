---
name: add-icon
description: 在 IDEA 插件中添加并使用图标，含 SVG 规范、主题适配与命名约定
---

# /add-icon — 添加并使用图标

为 Action / Widget / ToolWindow / 通知等添加图标。**首选顺序**：

1. **`AllIcons` 内置图标**（首选，自动适配主题，零维护成本）
2. **自定义 SVG 图标**（项目品牌图标 / 内置图标无对应语义时）
3. PNG 图标（**禁用**，不适配 HiDPI / 主题）

## Usage

```
/add-icon [icon-name] [size]
```

- `icon-name` — kebab-case 文件名（不含尺寸后缀），例如 `skill-status`、`refresh-skills`。
- `size` — 推荐 `16`（最常用）；ToolWindow 标签为 `13`，主插件图标为 `40` / `80`（marketplace）。

## Information Needed

1. **使用场景** — Toolbar / Action / StatusBar / ToolWindow / Notification / 主插件图标 / Marketplace。
2. **是否需要状态变化** — 单图标还是状态对（如启用 / 禁用）。
3. **是否能用 `AllIcons`** — 先去 [JetBrains Icons Sample](https://intellij-icons.jetbrains.design/) 搜一遍，能用就用。

## 决策：用 AllIcons 还是自定义？

| 情况                             | 选择                                                               |
|--------------------------------|------------------------------------------------------------------|
| 通用语义（保存 / 刷新 / 设置 / 警告 / 检查中…） | ✅ `AllIcons.*`                                                   |
| 状态对（启用 / 禁用、激活 / 停滞）           | ✅ `AllIcons.General.InspectionsEye` + `InspectionsPowerSaveMode` |
| 项目品牌（主图标、ToolWindow 标识）        | ❌ 自定义 SVG                                                        |
| Marketplace 列表展示               | ❌ 必须自定义 `pluginIcon.svg`（40x40）                                  |

## Files to Create/Modify

### 路径 A：使用 AllIcons（无需新建文件）

直接 import：

```java
import com.intellij.icons.AllIcons;

// Action
super(title, description, AllIcons.Actions.Refresh);

// 状态栏
return enabled ? AllIcons.General.InspectionsEye : AllIcons.General.InspectionsPowerSaveMode;
```

参考：

```77:81:src/main/java/dev/dong4j/idea/skill/inspector/statusbar/SkillInspectorStatusBarWidget.java
    @Override
    @NotNull
    public Icon getIcon() {
        return SkillInspectorSettings.getInstance().isSkillInspectionEnabled()
            ? AllIcons.General.InspectionsEye
            : AllIcons.General.InspectionsPowerSaveMode;
    }
```

**常用 AllIcons 速查**：

| 语义      | AllIcons 路径                                 |
|---------|---------------------------------------------|
| 刷新      | `AllIcons.Actions.Refresh`                  |
| 设置      | `AllIcons.General.GearPlain`                |
| 启用检查（蓝） | `AllIcons.General.InspectionsEye`           |
| 禁用检查（灰） | `AllIcons.General.InspectionsPowerSaveMode` |
| 警告      | `AllIcons.General.Warning`                  |
| 错误      | `AllIcons.General.Error`                    |
| 添加      | `AllIcons.General.Add`                      |
| 删除      | `AllIcons.General.Remove`                   |

### 路径 B：自定义 SVG

#### B.1 SVG 文件：`src/main/resources/icons/{icon-name}_{size}.svg`

文件命名约定（项目惯例）：`{语义}_{尺寸}.svg`，参考 `skill_inspector_16.svg`。

**SVG 必须满足**：

```xml
<svg width="16" height="16" viewBox="0 0 16 16"
     fill="none" xmlns="http://www.w3.org/2000/svg">
    <!-- 几何 -->
    <rect width="16" height="16" rx="2" fill="#4a63d4"/>
    <path d="M6 6h4v4H6z" fill="#ffffff"/>
</svg>
```

要点：

- **方形 viewBox**，与 `width`/`height` 一致。
- **不要嵌入 `<style>` / `<font>`**：IDE 不解析。
- **单色图标可用 `fill="currentColor"`**：让图标自动跟随主题前景色，无需提供深色变体。
- **多色品牌图标**：使用具体颜色（如 `#4a63d4`），并按下文提供 `_dark` 变体。
- **填充而非描边**：避免在 1x / 2x 缩放下糊掉。

#### B.2 深色主题变体（仅多色图标需要）

同目录加 `{icon-name}_{size}_dark.svg`，IntelliJ 在深色主题下自动加载该文件。**单色 + `currentColor` 不需要**这个文件。

#### B.3 图标加载类：`src/main/java/icons/SkillInspectorIcons.java`

⚠️ **包名必须是 `icons`**（顶级），不要放进业务包，否则 plugin.xml `<icon path="..."/>` 解析会出错（这是 IntelliJ 历史约定）。

```java
package icons;

import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import javax.swing.Icon;

public class SkillInspectorIcons {

    @NotNull
    private static Icon load(@NotNull String iconPath) {
        return IconLoader.getIcon(iconPath, SkillInspectorIcons.class);
    }

    // ========== 16x16 — Toolbar/Action/Menu/ToolWindow ==========
    public static final Icon SKILL_INSPECTOR_16 = load("/icons/skill_inspector_16.svg");

    // ========== 13x13 — ToolWindow Tab ==========
    public static final Icon SKILL_INSPECTOR_13 = load("/icons/skill_inspector_13.svg");
}
```

参考：

```19:37:src/main/java/icons/SkillInspectorIcons.java
public class SkillInspectorIcons {
    /**
     * 加载图标
     * <p> 用于加载位于资源包路径下的图标文件. 路径应与插件包路径保持一致.</p>
     *
     * @param iconPath 图标文件路径, 相对于 resources 根目录
     * @return 加载的图标
     */
    @NotNull
    private static Icon load(@NotNull String iconPath) {
        return IconLoader.getIcon(iconPath, SkillInspectorIcons.class);
    }

    // ========== 16x16 图标 - 用于 Toolbar/Action/Menu/ToolWindow ==========

    /** 插件主图标 (16x16), 用于工具栏按钮, 动作图标, 菜单项及工具窗口标签 */
    public static final Icon SKILL_INSPECTOR_16 =
        load("/icons/skill_inspector_16.svg");
}
```

#### B.4 使用

```java
import icons.SkillInspectorIcons;

super(title, description, SkillInspectorIcons.SKILL_INSPECTOR_16);
```

也可在 plugin.xml 中通过路径直引：

```xml
<action id="..." icon="/icons/skill_inspector_16.svg"/>
```

但**优先用 `SkillInspectorIcons` 常量**，便于全局重命名 / 复用。

### 路径 C：主插件图标 / Marketplace

- **主插件图标**：`src/main/resources/META-INF/pluginIcon.svg`，**40x40 viewBox**，浅色主题专用。
- **深色变体**：`src/main/resources/META-INF/pluginIcon_dark.svg`（可选，建议加）。

不要加到 `SkillInspectorIcons`，IntelliJ 自动从 `META-INF/` 加载，用于：插件管理面板、Marketplace、新建项目向导。

## 尺寸速查

| 用途                                  | 推荐尺寸  |
|-------------------------------------|-------|
| Action / Toolbar / Menu / StatusBar | 16x16 |
| ToolWindow Tab                      | 13x13 |
| 设置页树节点                              | 16x16 |
| Marketplace 列表                      | 40x40 |
| 安装包 / 商店主图                          | 80x80 |

## Build & Verify

```bash
./gradlew compileJava
./gradlew runIde
# 在 IDE 实例中：触发对应 Action / 打开 widget → 验证图标可见
# 切换 Settings → Appearance & Behavior → Appearance → Theme → 检查深色 / 浅色都正常
```

## Troubleshooting

- **图标显示为占位符 □**：`IconLoader.getIcon` 路径错（必须以 `/` 开头）；或 SVG 内嵌了 `<style>`，IDE 解析失败。
- **图标在深色主题下看不清**：单色图标用 `fill="currentColor"`；多色图标补 `_dark.svg`。
- **图标尺寸错位**：检查 `viewBox` 与 `width`/`height` 一致；不一致会被拉伸。
- **`SkillInspectorIcons` 找不到**：确认包名是顶级 `icons`，不是 `dev.dong4j.icons`。
- **plugin.xml `<icon>` 不生效**：路径必须以 `/` 开头，且文件在 `resources/` 下。

## 参考资源

- AllIcons 速查：<https://intellij-icons.jetbrains.design/>
- 自定义图标加载类：`src/main/java/icons/SkillInspectorIcons.java`
- SVG 范例：`src/main/resources/icons/skill_inspector_16.svg`
- 主插件图标：`src/main/resources/META-INF/pluginIcon.svg`
- IntelliJ 官方文档：<https://plugins.jetbrains.com/docs/intellij/work-with-icons-and-images.html>
