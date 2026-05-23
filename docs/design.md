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

### 4.1 SkillFile

`SkillFile` 表示一个 `SKILL.md` 文件及其上下文。

```java
public record SkillFile(
    PsiFile psiFile,
    String skillDirectoryName,
    @Nullable VirtualFile skillRoot,
    @Nullable SkillFrontMatter frontMatter,
    SkillBody body
) {}
```

关键约束：

- `psiFile.name` 必须等于 `SKILL.md`。
- `skillDirectoryName` 来源于 `SKILL.md` 的父目录。
- `skillRoot` 不在 V1 强依赖，因为不同 Agent 的根目录不同。

### 4.2 SkillFrontMatter

`SkillFrontMatter` 表示 YAML frontmatter 的解析结果。

```java
public record SkillFrontMatter(
    int startOffset,
    int endOffset,
    Map<String, Object> values,
    @Nullable String parseError
) {}
```

关键约束：

- frontmatter 必须从文件开头的 `---` 开始。
- 第二个 `---` 之前的内容按 YAML 解析。
- YAML 解析失败时不继续做字段类型判断，只报告解析错误。

### 4.3 SkillProblem

`SkillProblem` 是插件内部问题模型，最后映射为 IntelliJ 的 `ProblemDescriptor`。

```java
public record SkillProblem(
    String ruleId,
    SkillSeverity severity,
    String message,
    TextRange range,
    List<SkillQuickFix> quickFixes
) {}
```

内部使用独立模型的原因：

- 规则引擎可以脱离 IDE API 做单元测试。
- 后续 Maven 插件或 CLI 可以复用同一套检查逻辑。
- 不同输出渠道可以共享规则结果。

## 5. 检查规则分层

规则按确定性和风险分为四层。

### 5.1 Structural Rules

结构规则用于判断 skill 是否能被 Agent 正确识别。

| Rule ID                           | Severity | 说明                    |
|-----------------------------------|----------|-----------------------|
| `skill.file.name`                 | Error    | 文件名必须为 `SKILL.md`     |
| `skill.directory.name`            | Error    | 父目录名必须为 kebab-case    |
| `frontmatter.missing`             | Error    | 缺少 YAML frontmatter   |
| `frontmatter.invalid-yaml`        | Error    | frontmatter YAML 解析失败 |
| `frontmatter.name.missing`        | Error    | 缺少 `name`             |
| `frontmatter.name.mismatch`       | Error    | `name` 与父目录名不一致       |
| `frontmatter.description.missing` | Error    | 缺少 `description`      |

### 5.2 Quality Rules

质量规则用于提高 skill 可用性，但不一定阻塞。

| Rule ID                   | Severity     | 说明                         |
|---------------------------|--------------|----------------------------|
| `description.too-short`   | Warning      | 描述过短，可能无法帮助 Agent 选择 skill |
| `description.too-generic` | Warning      | 描述过泛，例如只写 `helper`、`tool`  |
| `body.missing-title`      | Weak Warning | 正文缺少一级标题                   |
| `body.missing-trigger`    | Warning      | 正文没有说明何时使用                 |
| `body.too-long`           | Warning      | 正文过长，建议拆分到 `references/`   |

### 5.3 Reference Rules

引用规则用于保证 skill 关联资源可访问。

| Rule ID                     | Severity     | 说明                                |
|-----------------------------|--------------|-----------------------------------|
| `reference.missing-file`    | Warning      | Markdown 相对链接指向的文件不存在             |
| `reference.outside-skill`   | Warning      | 引用路径跳出当前 skill 目录                 |
| `reference.case-mismatch`   | Warning      | 路径大小写和文件系统不一致                     |
| `resource.unused-reference` | Weak Warning | `references/` 下文件未被 `SKILL.md` 引用 |
| `script.missing-usage`      | Weak Warning | `scripts/` 下脚本未在正文说明用法            |

### 5.4 Security Rules

安全规则用于发现高风险内容。

| Rule ID                       | Severity | 说明                            |
|-------------------------------|----------|-------------------------------|
| `security.secret-pattern`     | Error    | 疑似 token、password、private key |
| `security.dangerous-command`  | Error    | 出现 `rm -rf /`、`curl           | sh` 等危险命令 |
| `security.allowed-tools-bash` | Warning  | `allowed-tools` 包含过宽的 `Bash`  |
| `security.sensitive-path`     | Warning  | 引导访问 `.ssh`、`.env`、`~/.aws`   |
| `security.prompt-injection`   | Warning  | 出现忽略上级指令等注入式文案                |

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

- 判断当前文件是否为 `SKILL.md`。
- 获取父目录名。
- 初步判断父目录是否像 skill 目录。

不负责：

- 不判断当前 skill 属于 Claude、Codex、Junie 还是 Cursor。
- 不扫描整个项目。

### 6.2 FrontMatterParser

职责：

- 找到文件开头的 frontmatter。
- 记录 frontmatter 在文档中的 offset。
- 使用 YAML parser 解析键值。
- 保留解析错误和原始文本范围。

设计约束：

- 解析失败时仍返回 `SkillFrontMatter`，并携带 `parseError`。
- 不能因为 YAML 错误导致整个 Inspection 崩溃。

### 6.3 RuleRunner

职责：

- 按规则集合执行检查。
- 根据用户启用的 profile 决定规则开关。
- 输出统一的 `SkillProblem`。

规则执行顺序：

1. 结构规则。
2. frontmatter 字段规则。
3. Markdown 正文规则。
4. 引用规则。
5. 安全规则。

结构规则失败时，后续依赖模型的规则应跳过，避免重复噪音。

## 7. Quick Fix 策略

Quick Fix 只处理确定性问题。

允许自动修复：

- 创建缺失 frontmatter 模板。
- 补齐缺失 `name`。
- 将 `name` 修正为父目录名。
- 将非法目录名建议为 kebab-case。
- 创建缺失的引用文件。

不自动修复：

- 不自动扩写 description。
- 不自动重写正文 workflow。
- 不自动删除 `allowed-tools: Bash`。
- 不自动删除疑似敏感内容。

对于安全问题，只提供解释和定位，必要时提供文档链接或建议，不直接改写用户内容。

## 8. 配置模型

V1 设置项保持最小：

```text
Skill Inspector
├── Enabled profiles
│   ├── Generic Agent Skills
│   ├── Claude Code
│   ├── OpenAI Codex
│   ├── JetBrains Junie
│   └── GitHub Copilot / VS Code
├── Rule severity overrides
├── Max SKILL.md length
└── Security scan enabled
```

默认启用：

- Generic Agent Skills
- OpenAI Codex
- Claude Code

原因是这些 profile 覆盖当前最常见的 `SKILL.md` 写作场景，同时避免 V1 一开始引入过多工具差异。

## 9. 模块划分

建议代码结构：

```text
src/main/java/dev/dong4j/idea/skill/inspector/
├── detection/
│   └── SkillFileDetector.java
├── model/
│   ├── SkillFile.java
│   ├── SkillFrontMatter.java
│   ├── SkillBody.java
│   └── SkillProblem.java
├── parser/
│   ├── FrontMatterParser.java
│   └── MarkdownReferenceParser.java
├── rules/
│   ├── SkillRule.java
│   ├── RuleRunner.java
│   ├── StructuralRules.java
│   ├── FrontMatterRules.java
│   ├── BodyQualityRules.java
│   ├── ReferenceRules.java
│   └── SecurityRules.java
├── inspection/
│   └── SkillMdInspection.java
├── quickfix/
│   ├── AddFrontMatterFix.java
│   ├── SyncNameWithDirectoryFix.java
│   └── CreateMissingReferenceFix.java
└── settings/
    ├── SkillInspectorSettings.java
    └── SkillInspectorConfigurable.java
```

模块边界：

- `model`、`parser`、`rules` 尽量不依赖 IntelliJ API。
- `inspection`、`quickfix`、`settings` 可以依赖 IntelliJ API。
- 这样后续可以把检查核心迁移到 CLI 或 Maven 插件。

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
