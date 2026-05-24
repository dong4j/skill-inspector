---
name: add-i18n-key
description: 添加国际化键，必须双语同步并使用 SkillInspectorBundle 加载
---

# /add-i18n-key — 添加国际化键

为任何用户可见文本添加 i18n key。**项目硬性约定**：所有面向用户的文案 **必须** 走 `SkillInspectorBundle`，禁止在代码里硬编码字符串。

## Usage

```
/add-i18n-key [key] [en-text]
```

- `key` — snake_case，分类前缀，例如 `action.validate.skill.title`、`inspection.frontmatter.tags.invalid`、`quickfix.tags.add`。
- `en-text` — 英文原文（IntelliJ 默认 locale）。

## Information Needed

1. **使用位置** — Action / Inspection / Quick Fix / Settings / Status Bar / Notification / 错误消息。
2. **是否含占位符** — `{0}` `{1}` 用于 `MessageFormat` 替换。
3. **中文翻译** — 项目支持中英双语，必须同步提供。

## 命名约定（强制）

| 前缀               | 用途                    | 示例                                    |
|------------------|-----------------------|---------------------------------------|
| `plugin.*`       | 插件基本信息                | `plugin.name`                         |
| `action.*`       | Action 标题 / 描述        | `action.validate.skill.title`         |
| `inspection.*`   | 规则消息 + display name   | `inspection.frontmatter.name.missing` |
| `quickfix.*`     | Quick Fix family name | `quickfix.name.add`                   |
| `settings.*`     | 设置页字段                 | `settings.enable.inspection`          |
| `statusbar.*`    | 状态栏 widget            | `statusbar.tooltip.enabled`           |
| `notification.*` | 通知标题 / 操作链接           | `notification.action.open.problems`   |
| `error.*`        | 错误提示                  | `error.no.project`                    |

**规则**：

- key 全部 **lowercase + dot-separated**，单词内用连字符分隔（如 `name.too-long`，与 ruleId 保持一致便于关联）。
- 不要随意建新前缀；新增功能尽量复用上表前缀。

## Files to Modify

### 1. 英文资源：`src/main/resources/messages/SkillInspectorBundle.properties`

```properties
inspection.frontmatter.tags.invalid=Tags must use kebab-case
inspection.frontmatter.tags.invalid.with.value=Tag "{0}" must use kebab-case
```

按已有分组（`# Inspection`、`# Quick Fix`、`# Status Bar` 等）追加，不要打乱顺序。

### 2. 中文资源：`src/main/resources/messages/SkillInspectorBundle_zh_CN.properties`

**必须同步**，否则中文用户会看到英文 fallback：

```properties
inspection.frontmatter.tags.invalid=tags 必须使用 kebab-case
inspection.frontmatter.tags.invalid.with.value=tag "{0}" 必须使用 kebab-case
```

**关键约束**：

- **中文文件支持原始 UTF-8**：现代 IntelliJ 已支持 `messages` bundle 的 UTF-8，**不需要再写 `\u63D2\u4EF6` 形式的 Unicode 转义**。但如果你看到旧
  key 是转义形式，**保留原样**，不要混用——后续可以单独走"全文件转 UTF-8"的清理任务。
- **占位符必须一致**：`{0}` `{1}` 在两个 bundle 文件里的数量、顺序、含义必须严格相同。
- **不要在 properties 文件里加注释解释占位符**：注释会跟着进 IDE 二进制，徒增体积。

### 3. 代码引用：通过 `SkillInspectorBundle.message(...)` 加载

**永远走 bundle，不要硬编码**：

```java
import dev.dong4j.idea.skill.inspector.util.SkillInspectorBundle;

String text = SkillInspectorBundle.message("inspection.frontmatter.tags.invalid");
String withArg = SkillInspectorBundle.message("inspection.frontmatter.tags.invalid.with.value", tagName);
```

`message(...)` 方法会触发 IDE 的 `@PropertyKey` 检查：

```62:66:src/main/java/dev/dong4j/idea/skill/inspector/util/SkillInspectorBundle.java
    @NotNull
    @Nls
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
        return INSTANCE.getMessage(key, params);
    }
```

**关键约束**：

- **写错 key IDE 会标红**：`@PropertyKey` 让 IntelliJ 在编辑器里直接校验 bundle，所以拼写错误编译期就能发现。
- **延迟加载场景用 `messagePointer(...)`**：返回 `Supplier<String>`，例如 Action 标题在 IDE 启动早期但 bundle 未必已加载时。

### 4. plugin.xml 中的引用

某些 IntelliJ 扩展点（Inspection / 设置页）支持直接通过 bundle key 加载文案，不需要 Java 代码：

```xml
<localInspection language="Markdown"
                 groupBundle="messages.SkillInspectorBundle"
                 groupKey="inspection.group.name"
                 displayNameKey="inspection.skill.md.display.name"
                 ...
                 />
```

**所有新建 `<localInspection>` / `<applicationConfigurable>` 等扩展点优先使用 `*Key` + `groupBundle` 加载**，而不是写死的
`displayName="My Inspection"`。

## Build & Verify

```bash
./gradlew compileJava       # @PropertyKey 校验，写错的 key 编译期报错
./gradlew runIde
# IDE 实例：切换 IDE 语言 (Help → Change Language) → 验证中英文切换正常
```

## Troubleshooting

- **`MissingResourceException: Can't find resource for bundle SkillInspectorBundle, key xxx`**：中文 bundle 漏加 key，或 key 拼写有出入。
- **IDE 中显示原始 key（如 `inspection.frontmatter.tags.invalid`）**：bundle 文件没被打包，确认 properties 文件位于
  `src/main/resources/messages/` 且 `plugin.xml` 中 `<resource-bundle>messages.SkillInspectorBundle</resource-bundle>` 已声明。
- **`{0}` 占位符没替换**：调用 `message()` 时漏传参数；或参数类型为 `null`（`MessageFormat` 会抛 `IllegalArgumentException`，传
  `String.valueOf(...)` 兜底）。
- **中文显示乱码**：确认文件保存为 UTF-8（IDE 默认）；老转义形式的中文不要用纯文本工具改成 UTF-8 再混合编辑，会乱掉。
- **`@PropertyKey` 标红但 key 实际存在**：IDE 缓存问题，`File → Invalidate Caches → Restart`。

## 参考代码

- Bundle 加载工具：`src/main/java/dev/dong4j/idea/skill/inspector/util/SkillInspectorBundle.java`
- 英文资源：`src/main/resources/messages/SkillInspectorBundle.properties`
- 中文资源：`src/main/resources/messages/SkillInspectorBundle_zh_CN.properties`
- Bundle 注册：`src/main/resources/META-INF/plugin.xml`（`<resource-bundle>` 标签）
