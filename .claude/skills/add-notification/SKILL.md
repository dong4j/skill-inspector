---
name: add-notification
description: 在 IDEA 插件中通过统一通知组发送气泡通知（信息 / 警告 / 错误）
---

# /add-notification — 发送通知

通过项目统一的 `NotificationUtil` 工具类发送一条气泡通知。**项目惯例**：所有通知共用一个通知组 `Skill Inspector Notifications`，避免在
`Settings → Notifications` 里散落多个开关。

## Usage

```
/add-notification [type] [message-key]
```

- `type` — `info` / `warning` / `error`
- `message-key` — i18n key（不要直接写死字符串）

## Information Needed

1. **触发场景** — Action 完成、Quick Fix 失败、扫描结果汇总……
2. **严重度** — INFORMATION（绿）/ WARNING（黄）/ ERROR（红）。
3. **是否需要操作链接** — 例如"点击查看详情"。
4. **是否在没有 project 时也要发** — 如插件加载失败提示，传 `project=null`。

## Files to Create/Modify

### 不需要新建文件，直接调用 `NotificationUtil`

```java
import dev.dong4j.idea.skill.inspector.util.NotificationUtil;
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;

NotificationUtil.showInfo(project, SkillInspectorBundle.message("xxx.completed"));
NotificationUtil.showWarning(project, SkillInspectorBundle.message("xxx.has.errors"));
NotificationUtil.showError(project, SkillInspectorBundle.message("xxx.failed"));
```

参考：

```49:73:src/main/java/dev/dong4j/idea/skill/inspector/util/NotificationUtil.java
    public static void showInfo(@Nullable Project project, @NotNull String message) {
        notify(project, message, NotificationType.INFORMATION);
    }

    /**
     * 显示警告通知
     * <p> 调用 IntelliJ Platform 的通知系统显示警告信息
     *
     * @param project 项目对象, 可以为空
     * @param message 要显示的警告内容, 不能为空
     */
    public static void showWarning(@Nullable Project project, @NotNull String message) {
        notify(project, message, NotificationType.WARNING);
    }

    /**
     * 显示错误通知
     * <p> 在指定的项目中显示错误级别的通知信息
     *
     * @param project 项目对象, 可以为空
     * @param message 错误通知的内容, 不能为空
     */
    public static void showError(@Nullable Project project, @NotNull String message) {
        notify(project, message, NotificationType.ERROR);
    }
```

### 严重度选择指南

| 场景                                | 选哪个                         |
|-----------------------------------|-----------------------------|
| 任务成功完成、纯信息提示                      | `showInfo`                  |
| 任务完成但发现需要用户关注的问题（如扫到 N 个 warning） | `showWarning`               |
| 任务失败、操作被拦截                        | `showError`                 |
| 重大异常 / 需要用户立刻处理                   | 用 `showError` 并附操作链接（见下方扩展） |

参考用法：

```174:191:src/main/java/dev/dong4j/idea/skill/inspector/action/SkillInspectorAction.java
        String message = SkillInspectorBundle.message(
            "action.validate.skill.completed",
            summary.totalFiles,
            summary.totalErrors,
            summary.totalWarnings
        );

        if (summary.totalErrors > 0 && summary.firstErrorFile != null) {
            NotificationUtil.showWarning(project, message);
            // 自动把第一个有错的 SKILL.md 打到编辑器前台, 方便用户立刻处理.
            FileEditorManager.getInstance(project).openFile(summary.firstErrorFile, true);
        } else if (summary.totalErrors > 0) {
            NotificationUtil.showWarning(project, message);
        } else if (summary.totalWarnings > 0) {
            NotificationUtil.showInfo(project, message);
        } else {
            NotificationUtil.showInfo(project, message);
        }
```

## 通知组（已注册，不要新建）

通知组 ID 在 `plugin.xml` 中注册：

```40:43:src/main/resources/META-INF/plugin.xml
        <!-- 通知组 -->
        <!--suppress PluginXmlI18n -->
        <notificationGroup id="Skill Inspector Notifications"
                           displayType="BALLOON"
                           isLogByDefault="true"/>
```

**关键约束**：

- **`displayType` 选 `BALLOON`**（项目惯例）：右下角气泡，不阻塞、可被用户在 Settings 里调成 STICKY_BALLOON / TOOL_WINDOW / NONE。
- **`isLogByDefault="true"`**：确保通知历史可在 `Event Log` 里回看，方便用户查证。
- **不要为新功能建新通知组**：除非语义完全不同（例如"自动同步"vs"规则检查"分得很开），否则一律复用 `Skill Inspector Notifications`。

## 扩展：附操作链接

如果通知需要用户点击执行某个 Action（例如"查看详情"），用 `Notification.addAction()`：

```java
Notification notification = NotificationGroupManager.getInstance()
    .getNotificationGroup("Skill Inspector Notifications")
    .createNotification(PluginContents.PLUGIN_NAME, message, NotificationType.WARNING);

notification.addAction(NotificationAction.createSimple(
    SkillInspectorBundle.message("notification.action.open.problems"),
    () -> {
        ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow("Problems View");
        if (tw != null) tw.show();
    }
));

notification.notify(project);
```

> 当前 `NotificationUtil` 只暴露三个无操作链接的便捷方法。**如果某个 skill 一定要带操作链接**，要么在调用处直接用上面的原生 API，要么扩展
`NotificationUtil` 加一个 `showWithAction(...)` 方法（注意保持 i18n + bundle key 同步）。

## i18n 文案

```properties
xxx.completed=Operation completed: {0} files processed
xxx.failed=Operation failed: {0}
notification.action.open.problems=Open Problems View
```

详见 `/add-i18n-key`。

## Build & Verify

```bash
./gradlew compileJava
./gradlew runIde
# 在 IDE 实例触发动作 → 右下角应弹出气泡，且 Event Log 中可见同条记录
```

## Troubleshooting

- **`Notification group not registered: ...`**：通知组 ID 与 `plugin.xml` 不一致。`NotificationUtil.NOTIFICATION_GROUP_ID` 常量与
  `<notificationGroup id="...">` 必须 byte-for-byte 相同。
- **气泡不出现**：用户可能在 `Settings → Notifications` 把对应组关成了 NONE；可在 Event Log 中确认通知是否真的发出。
- **`PluginXmlI18n` 警告**：通知组的 `id` 是英文常量，IntelliJ 静态检查会要求加 i18n，本项目已用 `<!--suppress PluginXmlI18n -->`
  抑制。新增通知组沿用相同抑制即可。
- **通知里换行不生效**：`message` 用 `<br/>` 而不是 `\n`（IntelliJ 通知支持简单 HTML）。
- **没有 project 时通知 NPE**：用 `NotificationUtil.showError(null, ...)`，工具类已经处理 nullable。

## 参考代码

- 工具类：`src/main/java/dev/dong4j/idea/skill/inspector/util/NotificationUtil.java`
- 通知组注册：`src/main/resources/META-INF/plugin.xml`
- 调用范式：`src/main/java/dev/dong4j/idea/skill/inspector/action/SkillInspectorAction.java`
- IntelliJ 官方文档：<https://plugins.jetbrains.com/docs/intellij/notifications.html>
