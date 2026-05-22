# Skill Inspector

Skill Inspector 是一个面向 JetBrains IDE 的 Agent Skill 规范检查插件，重点检查 `SKILL.md` 及其关联目录、引用文件和脚本是否符合 Agent Skill 的常见写法约束。

项目的第一阶段只聚焦一件事：让 `SKILL.md` 像代码一样在 IDEA 中具备实时 Inspection、Problems 展示和 Quick Fix 能力。

## 背景

Agent Skills 正在被 Claude Code、Codex、GitHub Copilot、JetBrains Junie、Cursor 等工具采用。一个 skill 通常由一个目录和目录下的 `SKILL.md` 组成：

```text
skills/
└── my-skill/
    ├── SKILL.md
    ├── references/
    ├── scripts/
    └── assets/
```

`SKILL.md` 里通常包含 YAML frontmatter 和 Markdown 指令正文：

```markdown
---
name: my-skill
description: Use this skill when an agent needs to perform a specific workflow.
allowed-tools: Read Edit
---

# My Skill

Use this skill when...
```

目前 JetBrains 生态已经有 Skill 管理、发现、导入方向的能力，但缺少一个专门围绕 `SKILL.md` 写作质量、结构规范、跨 Agent 兼容性和安全风险做检查的轻量插件。Skill Inspector 的定位就是补上这块开发体验。

## 核心目标

- 识别项目中的 `SKILL.md` 文件。
- 检查 skill 目录结构是否合理。
- 校验 YAML frontmatter 的必填字段、字段类型和兼容性。
- 检查 `name` 与父目录名是否一致。
- 检查 `description` 是否足够清晰、具体。
- 检查 Markdown 正文是否包含必要使用说明。
- 检查引用文件、脚本和资源路径是否存在。
- 提示过长正文、未引用资源、危险工具权限和潜在敏感信息。
- 在 IDEA Problems 面板中展示问题，并提供 Quick Fix。

## MVP 功能

第一版先做 `SKILL.md` Inspection，不做复杂的 Skill 管理和远端同步。

| 功能 | 说明 |
| --- | --- |
| SKILL.md 识别 | 只对名为 `SKILL.md` 的文件启用检查 |
| frontmatter 校验 | 检查 `---` 包裹的 YAML 头部是否存在且可解析 |
| 必填字段检查 | 检查 `name`、`description` 是否存在 |
| 目录名匹配 | 检查 `name` 是否等于父目录名 |
| 命名规范 | 检查 skill 名称是否为 kebab-case |
| 描述质量 | 检查描述是否过短、过泛或缺少触发条件 |
| allowed-tools 检查 | 检查工具列表格式和危险权限 |
| Markdown 引用检查 | 检查相对链接指向的文件是否存在 |
| 长度提示 | 提醒 `SKILL.md` 过长，建议拆分到 `references/` |
| 安全扫描 | 检查疑似 secret、危险命令和 prompt injection 文案 |
| Quick Fix | 自动补 frontmatter、修正 name、创建缺失引用文件 |

## 检查规则

### 文件结构

- `SKILL.md` 应位于一个独立 skill 目录下。
- 父目录名应使用 kebab-case。
- `name` 必须与父目录名一致。
- `references/`、`scripts/`、`assets/` 是推荐目录，不强制存在。
- `references/` 下的文件应被 `SKILL.md` 通过相对链接引用。
- `scripts/` 下的脚本应在 `SKILL.md` 中说明用途和调用方式。

### frontmatter

基础字段：

| 字段 | 规则 |
| --- | --- |
| `name` | 必填；kebab-case；建议不超过 64 字符；必须等于父目录名 |
| `description` | 必填；建议 50-300 字符；应说明何时使用该 skill |
| `allowed-tools` | 可选；检查格式和危险权限 |
| `argument-hint` | 可选；应为字符串 |
| `user-invocable` | 可选；应为布尔值 |
| `disable-model-invocation` | 可选；应为布尔值 |
| `context` | 可选；用于兼容不同 Agent 的上下文策略 |
| `license` | 可选；企业内部 skill 建议填写 |
| `metadata` | 可选；应为对象 |

### Markdown 正文

- 建议包含一级标题。
- 建议说明 `Use this skill when...` 或等价触发条件。
- 建议包含清晰 workflow 或步骤。
- 建议说明输入、输出和边界情况。
- 如果正文过长，应提示拆分到 `references/`。
- 如果引用本地文件，应检查路径是否存在、大小写是否一致。

### 跨 Agent 兼容性

不同 Agent 对 `SKILL.md` 字段和目录的支持不完全一致。后续版本会支持多套 profile：

- Generic Agent Skills
- Claude Code
- OpenAI Codex
- GitHub Copilot / VS Code
- JetBrains Junie
- Cursor
- Zeka Stack

插件可以根据启用 profile 提示兼容性问题，例如：

```text
allowed-tools is supported by Claude Code, but may be ignored by some Agent Skills implementations.
```

### 安全规则

- 检查疑似 token、password、secret、private key。
- 检查 `rm -rf /`、`curl | sh` 等危险命令。
- 检查 `allowed-tools: Bash` 这类过宽权限。
- 检查访问 `.ssh`、`.env`、`~/.aws` 等敏感路径的说明。
- 检查 `ignore previous instructions` 等 prompt injection 风险文案。
- 检查硬编码绝对路径、内网地址和账号信息。

## Quick Fix 方向

- 创建缺失的 frontmatter。
- 补齐 `name` 和 `description`。
- 将 `name` 修正为父目录名。
- 将不合法名称转换为 kebab-case。
- 创建缺失的引用文件。
- 将过长正文提示拆分到 `references/`。
- 收窄危险的 `allowed-tools` 配置。

## 后续路线

### V1: SKILL.md Inspection

- 实时检查 `SKILL.md`。
- Problems 面板展示问题。
- 基础 Quick Fix。
- Markdown 相对链接检查。
- 安全风险提示。

### V2: 多 Agent Profile

- 支持 Claude Code、Codex、Junie、VS Code Copilot、Cursor 等 profile。
- 对 frontmatter 字段做兼容性提示。
- 对不同 Agent 的 skill 目录做兼容性提示。

### V3: Skill Explorer

- ToolWindow 展示项目级和用户级 skills。
- 支持打开、预览、复制 skill 名称。
- 支持按目录、Agent、风险等级过滤。

### V4: SkillsJar Manager

- 扫描 Maven / Gradle 依赖中的 `META-INF/skills/**/SKILL.md`。
- 展示依赖 JAR 中的 skill 列表。
- 预览 frontmatter 和正文。
- 解包到 `.claude/skills`、`.junie/skills`、`.agents/skills` 等目录。

### V5: Zeka Stack 集成

- 读取 `pom.xml` 和当前项目技术栈。
- 推荐 Zeka Stack skill 模板。
- 检查 skill 内容是否与当前 Spring Boot、Maven、组件版本匹配。
- 对接 Maven 插件的 `validate`、`index`、`extract` 结果。

## 技术实现方向

插件实现会优先采用 IntelliJ Platform 的 Inspection 体系：

- `LocalInspectionTool` 负责检查 `SKILL.md`。
- YAML frontmatter 先使用文本切分和 YAML parser 解析。
- Markdown 相对链接通过 PSI 或 Markdown 文本扫描识别。
- 问题通过 `ProblemDescriptor` 注册到 Problems 面板。
- 自动修复通过 `LocalQuickFix` 修改文档内容或创建文件。
- 配置项放入 IDE Settings，后续支持 profile 和规则开关。

建议的代码结构：

```text
src/main/kotlin/dev/dong4j/skillinspector/
├── inspection/
│   ├── SkillMdInspection.kt
│   ├── SkillFrontMatterInspection.kt
│   ├── SkillReferenceInspection.kt
│   └── SkillSecurityInspection.kt
├── parser/
│   ├── SkillFileDetector.kt
│   ├── FrontMatterParser.kt
│   └── SkillModel.kt
├── quickfix/
│   ├── AddMissingFrontMatterFix.kt
│   ├── RenameSkillNameFix.kt
│   ├── CreateMissingReferenceFix.kt
│   └── RestrictAllowedToolsFix.kt
├── settings/
│   └── SkillInspectorSettings.kt
└── toolwindow/
    └── SkillExplorerToolWindowFactory.kt
```

## 产品定位

Skill Inspector 不是普通 Markdown Linter，而是 Agent Skill Authoring 工具。

目标是让 skill 的编写、检查、修复和后续分发具备工程化体验：

```text
写 Skill -> IDEA 实时检查 -> Quick Fix 修复 -> 构建期校验 -> 打包 / 分发 / 同步
```

第一阶段先把 `SKILL.md` 写作规范检查做扎实，再逐步扩展到 Skill Explorer、SkillsJar 管理和 Zeka Stack 企业规范治理。
