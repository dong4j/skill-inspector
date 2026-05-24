---
name: add-action
description: 新增 IDEA Action（菜单项 / 工具栏按钮），含后台扫描套路
---

# /add-action — 新增 Action

新增一个 IDEA `AnAction`：菜单项、工具栏按钮、右键菜单或快捷键。**默认配套写一个后台扫描套路**，避免阻塞 EDT。

## Usage

```
/add-action [ActionName]
```

- `ActionName` — PascalCase Java 类名，例如 `RebuildSkillsIndexAction`、`ExportSkillReportAction`。

## Information Needed

1. **Action 标题 / 描述** — i18n key `action.{xxx}.title` / `.description`。
2. **挂载位置** — `EditorPopupMenu`（编辑器右键）、`MainMenu`（主菜单）、`ProjectViewPopupMenu`（项目树右键）、`ToolsMenu` 等。
3. **是否需要图标** — 没有则只走文字；需要先走 `/add-icon`。
4. **执行体是否耗时** — > 100ms 必须走后台 `Task.Backgroundable`。
5. **可见 / 可用条件** — 例如"仅当有 project 时启用"、"仅在 SKILL.md 上启用"。

## Files to Create/Modify

### 1. Action 类：`src/main/java/dev/dong4j/idea/skill/inspector/action/{ActionName}.java`

#### 同步 / 轻量场景（< 100ms）

```java
public class {ActionName} extends AnAction {

    public {ActionName}() {
        super(
            SkillInspectorBundle.message("action.{xxx}.title"),
            SkillInspectorBundle.message("action.{xxx}.description"),
            SkillInspectorIcons.SKILL_INSPECTOR_16   // 没图标就传 null
        );
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            NotificationUtil.showError(null, SkillInspectorBundle.message("error.no.project"));
            return;
        }
        // TODO: 业务
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    @NotNull
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
```

#### 后台扫描场景（推荐范式）

参考 `SkillInspectorAction`：把 `actionPerformed` 拆成"后台扫描 → EDT 发布结果"两段。

```java
@Override
public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
        NotificationUtil.showError(null, SkillInspectorBundle.message("error.no.project"));
        return;
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(
        project,
        SkillInspectorBundle.message("action.{xxx}.title"),
        true   // canBeCancelled
    ) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
            // 1. 在后台 + read action 内做重活（PSI / VFS 访问必须在 read action）
            Result result = ApplicationManager.getApplication()
                .runReadAction((Computable<Result>) () -> doScan(project, indicator));

            // 2. 切回 EDT 写 UI（通知、激活 ToolWindow、打开文件等）
            ApplicationManager.getApplication().invokeLater(() -> publish(project, result));
        }
    });
}
```

**关键约束**：

- **`actionPerformed` 在 EDT 上执行**：不要直接做 PSI 遍历或文件 IO，否则会卡 UI。
- **PSI / VFS 访问必须在 read action 内**：`ApplicationManager.getApplication().runReadAction(...)`，否则会触发
  `Read access is allowed from inside read-action only`。
- **写 UI（通知、ToolWindow、打开文件）必须在 EDT**：用 `invokeLater`。
- **`getActionUpdateThread()` 必须实现**：2024.2+ 强制要求，绝大多数情况返回 `BGT`（Background Thread），让 IDE 在后台线程做 `update()` 计算。
- **`update()` 里禁止做耗时操作**：只读 `e.getProject()` / 当前文件等廉价数据。

### 2. 扫描结果模型（小工程不用 record）

```java
private static final class ScanSummary {
    final int totalFiles;
    final int totalErrors;
    @Nullable final VirtualFile firstHit;

    ScanSummary(int totalFiles, int totalErrors, @Nullable VirtualFile firstHit) { ... }

    @NotNull
    static ScanSummary empty() { return new ScanSummary(0, 0, null); }
}
```

> 用普通类而不是 record 是项目惯例：方便在 read action 与 EDT 之间传递并保留可读字段名。

### 3. 注册：`src/main/resources/META-INF/plugin.xml`

```xml
<actions>
    <action id="dev.dong4j.idea.skill.inspector.action.{ActionName}"
            class="dev.dong4j.idea.skill.inspector.action.{ActionName}">
        <add-to-group group-id="EditorPopupMenu" anchor="last"/>
    </action>
</actions>
```

参考：

```57:63:src/main/resources/META-INF/plugin.xml
    <!-- 插件定义的动作 -->
    <actions>
        <!-- 编辑器右键菜单 -->
        <action id="dev.dong4j.idea.skill.inspector.action.SkillInspectorAction"
                class="dev.dong4j.idea.skill.inspector.action.SkillInspectorAction">
            <add-to-group group-id="EditorPopupMenu" anchor="last"/>
        </action>
    </actions>
```

**关键约束**：

- **`id` 必须是全限定类名**：保持与 GitLens 等成熟插件一致的可追溯性，便于在 `Help → Find Action` 里搜到。
- **`<add-to-group>` 的 `group-id`**：常用值 `EditorPopupMenu`、`ProjectViewPopupMenu`、`ToolsMenu`、`MainToolbarLeft`、`EditorTabPopupMenu`。
- **如果要支持快捷键**：用 `<keyboard-shortcut keymap="$default" first-keystroke="..."/>`
  ，但要先到 [keymap 注册表](https://plugins.jetbrains.com/docs/intellij/all-actions.html) 检查是否冲突。

### 4. 国际化

```properties
action.{xxx}.title=My Action
action.{xxx}.description=Detailed description shown in tooltip
```

详见 `/add-i18n-key`。

### 5. 完成反馈

操作完成后用 `NotificationUtil` 通知用户，详见 `/add-notification`。

## ToolWindow / Problems View 激活

如果你的 Action 跑完要把 IDE 视图切到某面板：

```java
ToolWindow problems = ToolWindowManager.getInstance(project).getToolWindow("Problems View");
if (problems != null) {
    problems.show();
}
```

常见 ToolWindow ID：`Problems View`、`Project`、`TODO`、`Run`、`Debug`、`Version Control`、`Terminal`。

## Build & Verify

```bash
./gradlew compileJava
./gradlew runIde
# 在 IDE 实例中：右键编辑器 / 打开 Help → Find Action → 搜你的 Action 名 → 触发并观察通知
```

## Troubleshooting

- **`Action class not found in classpath`**：plugin.xml 里的 `class` 写错了全限定名。
- **`Read access is allowed from inside read-action only`**：在后台线程访问了 PSI，套一层 `runReadAction`。
- **`Cannot access disposed project`**：长任务完成时 project 已经关闭。在 EDT 段开头加 `if (project.isDisposed()) return;`，参考
  `SkillInspectorAction.publishResult()`。
- **`update()` 在 2024.2+ 报 `Cannot determine action update thread`**：必须实现 `getActionUpdateThread()` 返回 `BGT` 或 `EDT`。
- **`ProgressIndicator` 不更新进度条**：必须在循环内调 `indicator.checkCanceled()` + `setFraction(...)` + `setText2(...)`，参考
  `SkillInspectorAction.scanProject()`。

## 参考代码

- 完整范式（后台扫描）：`src/main/java/dev/dong4j/idea/skill/inspector/action/SkillInspectorAction.java`
- plugin.xml 注册：`src/main/resources/META-INF/plugin.xml`
