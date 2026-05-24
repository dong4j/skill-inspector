# Skill Inspector Design

本文档描述 Skill Inspector 的产品边界、核心模型、检查规则、插件模块划分和阶段性实现路线。README 用于说明项目愿景，本文档用于约束后续实现，避免在第一阶段过早扩展成完整
Skill 管理平台。

## 1. 设计目标

Skill Inspector 的第一目标是提供 JetBrains IDE 内的 `SKILL.md` 规范检查能力。

核心设计原则：

- 先做 `SKILL.md` authoring 体验，不先做 registry、同步、市场或远端分发。
- 先用 IntelliJ Platform Inspection 体系接入 Problems 面板，不自建一套问题展示机制。
- 先覆盖高确定性的结构规则，再逐步增加带主观判断的质量评分。
- 规则必须可解释、可配置、可禁用，避免把某个 Agent 的实现细节硬编码成唯一标准。
- Quick Fix 必须保守，只修复确定性问题，不自动重写用户的 skill 正文。

## 2. 范围边界

### 2.1 V1 范围

V1 只处理本地文件系统中的 skill 目录：

```text
<project>/
└── <agent-skill-root>/
    └── <skill-name>/
        ├── SKILL.md
        ├── references/
        ├── scripts/
        └── assets/
```

检查入口：

- 用户打开 `SKILL.md` 时实时检查。
- 用户在 Problems 面板查看检查结果。
- 用户通过 intention / quick fix 修复确定性问题。
- 用户右键执行 `Validate Skill` 手动触发完整检查。

### 2.2 V1 非目标

V1 不做以下能力：

- 不扫描 Maven / Gradle classpath。
- 不解包 SkillsJar。
- 不同步到 `.claude/skills`、`.junie/skills`、`.agents/skills`。
- 不做远端 Skill Registry。
- 不引入 LLM 自动改写正文。
- 不做跨项目的全局质量看板。

这些能力保留到 V3 以后。

## 3. 核心用户场景

### 3.1 编写新 Skill

用户在项目中创建一个新目录：

```text
.agents/skills/spring-boot-review/SKILL.md
```

插件应检查：

- `SKILL.md` 是否包含 frontmatter。
- `name` 是否为 `spring-boot-review`。
- `description` 是否存在且足够具体。
- 正文是否说明使用时机。
- 引用文件是否存在。

### 3.2 迁移已有 Skill

用户从 Claude Code、Codex、Junie 或 VS Code 项目中复制 skill。插件应提示：

- 当前 frontmatter 字段对哪些 Agent 兼容。
- 目录名和 `name` 是否匹配。
- `allowed-tools` 是否存在过宽权限。
- 引用路径是否因迁移失效。

### 3.3 审查团队 Skill

团队在 PR 中新增或修改 `SKILL.md`。插件应帮助 reviewer 快速看到：

- 是否存在阻塞级结构错误。
- 是否存在安全风险。
- 是否存在低质量 description。
- 是否存在未引用的资源文件。

## 4. 领域模型

领域模型集中在 `model/` 包下，全部用 Java `record` 表达，并尽量不依赖 IntelliJ API，便于后续把检查核心复用到 CLI 或构建期校验。

### 4.1 SkillFile

`SkillFile` 表示一个 `SKILL.md` 文件及其上下文。

```java
public record SkillFile(
    PsiFile psiFile,
    String skillDirectoryName,
    @Nullable VirtualFile skillDirectory,
    @Nullable SkillFrontMatter frontMatter,
    SkillBody body
) {}
```

关键约束：

- `psiFile.name` 必须等于 `SKILL.md`，由 `SkillFileDetector` 守门。
- `skillDirectoryName` 来源于 `SKILL.md` 的父目录，用于校验 frontmatter `name`。
- `skillDirectory` 仅在引用规则中用于解析相对路径，缺失时跳过引用检查。

### 4.2 SkillFrontMatter

`SkillFrontMatter` 表示 YAML frontmatter 的解析结果。同时保留外层 `---` 与内部 YAML 内容的两套 offset，便于 Quick Fix 在内容区追加字段。

```java
public record SkillFrontMatter(
    int startOffset,
    int endOffset,
    int contentStartOffset,
    int contentEndOffset,
    Map<String, String> values,
    List<SkillYamlEntry> entries,
    SkillMetadata metadata,
    @Nullable String parseError
) {}
```

关键约束：

- frontmatter 必须从文件开头的 `---` 开始（兼容 UTF-8 BOM）。
- frontmatter 节点由 Markdown PSI 定位（`FRONT_MATTER` 元素类型），其内容再交给 YAML PSI 解析。
- YAML 解析失败时仍返回 `SkillFrontMatter` 并填充 `parseError`，让规则层只针对解析成功的字段继续工作。

### 4.3 SkillMetadata

`SkillMetadata` 是 specification 中显式定义字段的强类型视图，规则层通过它访问 `name`、`description`、`license`、`compatibility`、`metadata`、
`allowed-tools`，避免规则散落字符串 key。

```java
public record SkillMetadata(
    @Nullable SkillYamlEntry name,
    @Nullable SkillYamlEntry description,
    @Nullable SkillYamlEntry license,
    @Nullable SkillYamlEntry compatibility,
    @Nullable SkillYamlEntry metadata,
    @Nullable SkillYamlEntry allowedTools,
    Map<String, SkillYamlEntry> entries
) {}
```

### 4.4 SkillYamlEntry / SkillBody / SkillReference

- `SkillYamlEntry(key, value, keyRange, valueRange)`：YAML 顶层条目。
- `SkillBody(text, startOffset, endOffset)`：frontmatter 之后的 Markdown 正文及其 offset。
- `SkillReference(target, targetRange)`：Markdown 正文中的本地链接目标，由 `MarkdownReferenceParser` 通过 Markdown PSI 提取。

### 4.5 SkillProblem

`SkillProblem` 是插件内部问题模型，最后由 `SkillMdInspection` 映射为 IntelliJ 的 `ProblemDescriptor`。

```java
public record SkillProblem(
    String ruleId,
    SkillSeverity severity,
    String message,
    TextRange range,
    List<SkillFixType> fixTypes
) {}
```

内部使用独立模型的原因：

- 规则引擎可以脱离 IDE API 做单元测试。
- 后续 Maven 插件或 CLI 可以复用同一套检查逻辑。
- 不同输出渠道可以共享规则结果。
- `fixTypes` 是语义型枚举，避免规则层直接构造 IntelliJ `LocalQuickFix` 对象。

### 4.6 SkillFixType / SkillSeverity

- `SkillFixType`：当前共 6 种确定性修复 — `ADD_FRONT_MATTER`、`ADD_NAME`、`ADD_DESCRIPTION`、`SYNC_NAME_WITH_DIRECTORY`、`CONVERT_NAME_TO_KEBAB`、
  `CREATE_MISSING_REFERENCE`。
- `SkillSeverity`：`ERROR` / `WARNING` / `WEAK_WARNING`，由 `SkillMdInspection` 映射到 `ProblemHighlightType`。

## 5. 检查规则分层

规则按确定性和风险分为四层。下表中的 ID 与代码中 `SkillProblem.ruleId` 一一对应，以代码为准。

> 文件名检查 (`SKILL.md`) 不作为可上报规则，而是 `SkillFileDetector` 的入口门控，未命中文件名时整个 Inspection 直接跳过。

### 5.1 Structural Rules

结构规则用于判断 skill 是否能被 Agent 正确识别。

| Rule ID                              | Severity | 说明                        |
|--------------------------------------|----------|---------------------------|
| `skill.directory.name`               | Error    | 父目录名本身不符合 kebab-case      |
| `frontmatter.missing`                | Error    | 缺少 YAML frontmatter       |
| `frontmatter.invalid-yaml`           | Error    | frontmatter YAML 解析失败     |
| `frontmatter.name.missing`           | Error    | 缺少 `name`                 |
| `frontmatter.name.too-long`          | Error    | `name` 超过 64 字符           |
| `frontmatter.name.invalid`           | Error    | `name` 不符合 kebab-case     |
| `frontmatter.name.mismatch`          | Error    | `name` 与父目录名不一致           |
| `frontmatter.description.missing`    | Error    | 缺少 `description`          |
| `frontmatter.description.too-long`   | Error    | `description` 超过 1024 字符  |
| `frontmatter.compatibility.empty`    | Error    | 提供 `compatibility` 但内容为空  |
| `frontmatter.compatibility.too-long` | Error    | `compatibility` 超过 500 字符 |

### 5.2 Quality Rules

质量规则用于提高 skill 可用性，但不一定阻塞。

| Rule ID                     | Severity     | 说明                                          |
|-----------------------------|--------------|---------------------------------------------|
| `description.too-short`     | Warning      | 描述过短（< 20 字符），可能无法帮助 Agent 发现 skill         |
| `description.too-generic`   | Warning      | 描述过泛，例如只写 `helper`、`tool`                   |
| `description.missing-usage` | Warning      | description 未说明使用时机（缺 "use when"、"适用" 等触发语） |
| `body.missing-title`        | Weak Warning | 正文缺少一级标题                                    |
| `body.missing-trigger`      | Warning      | 正文未说明何时使用                                   |
| `body.too-long`             | Warning      | 正文超过 12000 字符，建议拆分到 `references/`           |

### 5.3 Reference Rules

引用规则用于保证 skill 关联资源可访问。引用通过 Markdown PSI 提取 link destination 节点，规则在文件系统层校验存在性、目录边界和大小写。

| Rule ID                   | Severity | 说明                             |
|---------------------------|----------|--------------------------------|
| `reference.invalid-path`  | Warning  | Markdown 链接目标不是合法路径            |
| `reference.missing-file`  | Warning  | Markdown 相对链接指向的文件不存在          |
| `reference.outside-skill` | Warning  | 引用路径跳出当前 skill 目录              |
| `reference.case-mismatch` | Warning  | 路径大小写和文件系统不一致（防止迁移到 Linux 后失效） |

> 待实现：`resource.unused-reference`（`references/` 下未被引用的文件）、`script.missing-usage`（`scripts/` 下脚本未在正文说明用法）。

### 5.4 Security Rules

安全规则用于发现高风险内容。V1 只定位和提示，不自动改写。

| Rule ID                       | Severity | 说明                                         |
|-------------------------------|----------|--------------------------------------------|
| `security.secret-pattern`     | Error    | 疑似 token / password / secret / private key |
| `security.dangerous-command`  | Error    | 出现 `rm -rf /`、`curl ... \| sh` 等危险命令       |
| `security.allowed-tools-bash` | Warning  | `allowed-tools` 包含过宽的 `Bash` 权限            |
| `security.sensitive-path`     | Warning  | 引导访问 `.ssh` / `.env` / `~/.aws`            |
| `security.prompt-injection`   | Warning  | 出现 `ignore previous instructions` 等注入式文案   |

## 6. 检查流程

V1 的检查流程保持同步、轻量：

```text
PsiFile
  -> SkillFileDetector
  -> FrontMatterParser
  -> SkillModelBuilder
  -> RuleRunner
  -> ProblemDescriptorMapper
  -> IntelliJ Problems
```

### 6.1 SkillFileDetector

职责：

- 仅根据 `PsiFile.getName()` 判断当前文件是否等于 `SKILL.md`。

不负责：

- 不读取 frontmatter，不判断父目录名。
- 不判断当前 skill 属于 Claude、Codex、Junie 还是 Cursor。
- 不扫描整个项目。

父目录信息由 `SkillModelBuilder` 在构建 `SkillFile` 时通过 `VirtualFile.getParent()` 获取，规则层只消费已组装好的领域模型。

### 6.2 FrontMatterParser

职责：

- 通过 Markdown PSI 定位 `FRONT_MATTER` 节点（兼容 UTF-8 BOM）。
- 在 frontmatter 节点内部找到首尾 `---`，截取内容范围。
- 把内容文本交给 YAML PSI（`YAMLLanguage`）解析，提取顶层 key/value 与 offset。
- 把 YAML 解析错误聚合到 `SkillFrontMatter.parseError`。

设计约束：

- 不使用正则切分 frontmatter，避免错过 `---` 嵌入正文或字段值跨行的情况。
- 解析失败时仍返回 `SkillFrontMatter`，并携带 `parseError`，规则层只跳过依赖字段的检查，但仍能上报 `frontmatter.invalid-yaml`。
- 不能因为 YAML 错误导致整个 Inspection 崩溃。

### 6.3 MarkdownReferenceParser

职责：

- 在 `SkillFile.body` 范围内遍历 Markdown PSI。
- 收集 `LINK_DESTINATION` 元素，复用 IDE 自身的 Markdown 语法理解。
- 规范化 `<path with spaces>` 形式，返回 `SkillReference`。

设计约束：

- 不用正则扫描正文，否则会被代码块、HTML 注释中的伪链接误导。
- 链接锚点、查询串和绝对 URL 由 `ReferenceRules` 在文件系统层进一步过滤。

### 6.4 RuleRunner

职责：

- 按固定顺序执行规则集合，输出 `SkillProblem` 聚合列表。
- V1 不区分 profile，所有默认规则都会运行；用户禁用 Inspection 总开关时，整个 Inspection 在入口跳过。

规则执行顺序（与 `RuleRunner` 构造函数一致）：

1. `StructuralRules`：frontmatter 必填字段、长度和命名规范。
2. `QualityRules`：description 和正文质量。
3. `ReferenceRules`：Markdown 引用。
4. `SecurityRules`：secret / 危险命令 / `allowed-tools` / 敏感路径 / prompt injection。

结构规则在 frontmatter 缺失或 YAML 解析失败时会提前 return，避免对依赖字段的后续检查产生噪音。其他规则若拿到 `parseError`，也会跳过依赖
metadata 字段的逻辑。

## 7. Quick Fix 策略

Quick Fix 只处理确定性问题，对应 `SkillFixType` 枚举值；具体写入逻辑集中在 `quickfix/SkillQuickFix.java`，纯文本拼接放在 `SkillQuickFixTexts`
便于单元测试。

当前已实现的修复：

- `ADD_FRONT_MATTER`：在文件开头插入 `---\nname: <dir>\ndescription: TODO ...\n---\n` 模板。
- `ADD_NAME`：在已有 frontmatter 内容区追加 `name` 字段。
- `ADD_DESCRIPTION`：在已有 frontmatter 内容区追加 `description` 字段（值为占位说明，不替用户生成主观内容）。
- `SYNC_NAME_WITH_DIRECTORY`：将 frontmatter 中的 `name` 值替换为父目录名。
- `CONVERT_NAME_TO_KEBAB`：把不合规的 `name` 规范化为合法 kebab-case，使用 `SkillQuickFixTexts.toKebabCaseName` 的纯函数。
- `CREATE_MISSING_REFERENCE`：为 `reference.missing-file` 创建空文件（含父目录），仅在 skill 目录内部写入，使用 VFS API 让 IDE 立即识别。

不自动修复（明确排除）：

- 不自动扩写 description。
- 不自动重写正文 workflow / 不自动拆分过长正文（拆分点带主观判断）。
- 不自动删除 `allowed-tools: Bash`（收窄策略带主观判断）。
- 不自动删除疑似敏感内容。

对于安全问题，只提供解释和定位，必要时提供文档链接或建议，不直接改写用户内容。

## 8. 配置模型

V1 设置项保持最小，由 `SkillInspectorSettings`（应用级 `PersistentStateComponent`）持久化：

```text
Skill Inspector
└── Enable SKILL.md format inspection  (默认开启)
```

- 设置页 `SkillInspectorConfigurable` 提供一个复选框。
- 状态栏 `SkillInspectorStatusBarWidget` 提供同一开关的快速切换，避免设置入口分散。
- 关闭开关时 `SkillMdInspection` 直接返回空问题数组，跳过所有规则。

未实现（计划见 `docs/roadmap.md` V2/V4）：

- 多 Agent Profile（Generic / Claude Code / Codex / Junie / Copilot / Cursor）。
- 规则严重度覆盖。
- 自定义最大正文长度等阈值。
- 单独的安全扫描开关。

## 9. 模块划分

当前实际代码结构：

```text
src/main/java/dev/dong4j/idea/skill/inspector/
├── PluginContents.java                # 插件标识常量
├── action/
│   └── SkillInspectorAction.java      # 右键菜单入口（V1 占位通知，未接入完整 Inspection）
├── detection/
│   └── SkillFileDetector.java         # 仅根据文件名 SKILL.md 决定是否启用 Inspection
├── inspection/
│   └── SkillMdInspection.java         # LocalInspectionTool 适配层
├── model/
│   ├── SkillFile.java
│   ├── SkillFrontMatter.java
│   ├── SkillMetadata.java
│   ├── SkillBody.java
│   ├── SkillYamlEntry.java
│   ├── SkillReference.java
│   ├── SkillProblem.java
│   ├── SkillFixType.java
│   └── SkillSeverity.java
├── parser/
│   ├── FrontMatterParser.java         # Markdown PSI 定位 + YAML PSI 解析
│   ├── FrontMatterParseResult.java
│   ├── MarkdownReferenceParser.java   # Markdown PSI 提取 link destination
│   └── SkillModelBuilder.java
├── quickfix/
│   ├── SkillQuickFix.java             # 通用 LocalQuickFix 实现，按 SkillFixType 分发
│   └── SkillQuickFixTexts.java        # 纯文本生成工具（便于单测）
├── rules/
│   ├── SkillRule.java
│   ├── RuleRunner.java
│   ├── StructuralRules.java
│   ├── QualityRules.java
│   ├── ReferenceRules.java
│   └── SecurityRules.java
├── settings/
│   ├── SkillInspectorSettings.java    # 应用级 PersistentStateComponent
│   └── SkillInspectorConfigurable.java
├── statusbar/
│   ├── SkillInspectorStatusBarWidget.java
│   └── SkillInspectorStatusBarWidgetFactory.java
└── util/
    ├── NotificationUtil.java
    ├── SkillInspectorBundle.java
    └── TextRangeUtil.java             # IntelliJ Inspection 范围安全裁剪
```

模块边界：

- `model`、`rules`、`quickfix/SkillQuickFixTexts` 尽量不依赖 IntelliJ API，可以独立单元测试。
- `parser` 必须依赖 IntelliJ Markdown / YAML PSI；测试通过 `ProjectExtension` 拉起轻量 fixture。
- `inspection`、`quickfix/SkillQuickFix`、`settings`、`statusbar`、`action` 直接对接 IntelliJ Platform API。
- 这样后续可以把检查核心迁移到 CLI 或 Maven 插件，只需要替换 `parser` 适配层。

## 10. 测试策略

V1 应优先覆盖规则测试，而不是 UI 测试。

测试层级：

- Parser tests：frontmatter 有效、缺失、YAML 错误、空 body。
- Rule tests：每条规则至少一个 positive 和 negative case。
- Quick Fix tests：验证修复后的文档内容。
- Inspection tests：验证 IntelliJ fixture 能正确注册问题。

关键样例：

```text
valid-skill/
missing-frontmatter/
invalid-yaml/
name-mismatch/
short-description/
missing-reference/
dangerous-command/
secret-pattern/
```

## 11. 后续扩展

### 11.1 Skill Explorer

在 V2/V3 增加 ToolWindow：

- 扫描项目级 skill 目录。
- 展示 skill 名称、description、profile、风险等级。
- 双击打开 `SKILL.md`。
- 支持按问题数量过滤。

### 11.2 SkillsJar Manager

在 V4 增加依赖扫描：

- Maven 普通依赖。
- Maven plugin dependencies。
- Gradle dependencies。
- JAR 内 `META-INF/skills/**/SKILL.md`。

该模块应复用 V1 的 parser 和 rules，对 JAR 内 skill 做只读检查。

### 11.3 Zeka Stack Integration

在 V5 增加项目上下文检查：

- 读取 `pom.xml`。
- 识别 Spring Boot、Zeka Stack、MyBatis、Nacos、XXL-JOB 等组件。
- 判断 skill 内容是否与当前技术栈匹配。
- 对接未来的 Maven 插件 `validate`、`index`、`extract`。

## 12. 风险与取舍

### 12.1 不同 Agent 规范不一致

风险：某个字段在 A Agent 合法，在 B Agent 中被忽略或误报。

取舍：通过 profile 解决，不把单一 Agent 当成唯一真理。

### 12.2 Markdown 正文质量难以客观判断

风险：规则过强会干扰用户写作。

取舍：正文质量规则默认使用 Warning 或 Weak Warning，且允许关闭。

### 12.3 安全扫描可能误报

风险：示例代码中可能故意包含 fake token 或危险命令说明。

取舍：安全规则给出明确匹配依据，并允许用注释或配置忽略。

### 12.4 V1 过早做平台化

风险：插件变成庞大的 Skill 管理平台，延迟核心 Inspection 落地。

取舍：V1 只做本地 `SKILL.md` 检查，其他能力全部延后。
