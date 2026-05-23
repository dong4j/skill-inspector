# Skill Inspector

Skill Inspector 是一个面向 JetBrains IDE 的 Agent Skill 规范检查插件，重点检查 `SKILL.md` 及其关联目录、引用文件和脚本是否符合 Agent Skill
的常见写法约束。

项目的第一阶段只聚焦一件事：让 `SKILL.md` 像代码一样在 IDEA 中具备实时 Inspection、Problems 展示和 Quick Fix 能力。

## 背景

Agent Skills 正在被 Claude Code、Codex、GitHub Copilot、JetBrains Junie、Cursor
等工具采用。它的官方入口是 [Agent Skills 官网](https://agentskills.io/home)
，完整格式要求以 [Agent Skills specification](https://agentskills.io/specification) 为准。

一个 skill 通常由一个目录和目录下的 `SKILL.md` 组成：

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

目前 JetBrains 生态已经有 Skill 管理、发现、导入方向的能力，但缺少一个专门围绕 `SKILL.md` 写作质量、结构规范、跨 Agent 兼容性和安全风险做检查的轻量插件。Skill
Inspector 的定位就是补上这块开发体验。

## 规范基准

Skill Inspector 的检查规则以 [Agent Skills specification](https://agentskills.io/specification) 作为默认基准，而不是以某一个 Agent
产品的私有扩展为基准。

这意味着 V1 的基础检查必须优先覆盖 specification 中明确说明的内容：

- 一个 skill 是一个目录，目录中至少包含 `SKILL.md`。
- `SKILL.md` 必须包含 YAML frontmatter，后面跟 Markdown 正文。
- `name` 和 `description` 是必填字段。
- `license`、`compatibility`、`metadata`、`allowed-tools` 是可选字段。
- `name` 必须匹配父目录名。
- `scripts/`、`references/`、`assets/` 是可选目录。
- 关联文件应通过相对路径引用。
- Agent 会先加载 `name` 和 `description` 做发现，再在命中任务时加载完整 `SKILL.md`。

其他 Agent 私有字段或产品差异，例如 Claude Code、Codex、Junie、VS Code Copilot、Cursor 的实现细节，只能放入兼容性 profile 中做提示，不应作为默认规范错误。

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

## 进一步阅读

| 文档                                                           | 内容                             |
|--------------------------------------------------------------|--------------------------------|
| [`docs/todo.md`](docs/todo.md)                               | MVP 功能清单与 Quick Fix 方向         |
| [`docs/rules.md`](docs/rules.md)                             | 检查规则规范（文件结构、frontmatter、安全规则等） |
| [`docs/roadmap.md`](docs/roadmap.md)                         | 版本路线、技术实现方向与产品定位               |
| [`docs/design.md`](docs/design.md)                           | 详细设计与模块划分（领域模型、检查流程）           |
| [`docs/implementation-plan.md`](docs/implementation-plan.md) | 当前实施优先级与多 Agent 协作计划           |
