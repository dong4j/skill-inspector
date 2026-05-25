# Skill Inspector 检查规则

本文档定义 Skill Inspector 对 `SKILL.md`
及其关联资源的完整检查规则集。所有规则以 [Agent Skills specification](https://agentskills.io/specification) 为默认基准。

> 待办任务见 [`./todo.md`](./todo.md)，版本路线见 [`./roadmap.md`](./roadmap.md)，领域模型见 [`./design.md`](./design.md)。

## 规则分层

规则按确定性和风险分为五层，对应 `rules/` 包下五个实现类：

1. **Structural Rules** (`StructuralRules`)：结构规则，判断 skill 是否能被 Agent 正确识别（frontmatter 必填字段、命名规范、长度上限）。
2. **Quality Rules** (`QualityRules`)：质量规则，提高 skill 可用性，但不一定阻塞（描述长度、描述触发语、正文结构、正文过长）。
3. **Reference Rules** (`ReferenceRules`)：引用规则，保证 skill 关联资源可访问（相对链接存在性、路径越界、大小写一致）。
4. **Resource Rules** (`ResourceRules`)：资源规则，反向检查 `references/` 和 `scripts/` 中的文件是否被 `SKILL.md` 使用或说明。
5. **Security Rules** (`SecurityRules`)：安全规则，发现高风险内容（疑似密钥、危险命令、过宽 `allowed-tools`、敏感路径、prompt injection）。

`SkillMdInspection` 只在 `PsiFile.getName()` 等于 `SKILL.md` 时启用 ── 文件名校验不作为单独规则上报，而是 `SkillFileDetector` 的入口门控。

## Structural Rules

| Rule ID                              | Severity | 说明                                                      | Quick Fix                  |
|--------------------------------------|----------|---------------------------------------------------------|----------------------------|
| `skill.directory.name`               | Error    | 父目录名本身不符合 kebab-case (与 `frontmatter.name.mismatch` 互补) | —                          |
| `frontmatter.missing`                | Error    | 缺少 YAML frontmatter，整体跳过其他字段规则                          | `ADD_FRONT_MATTER`         |
| `frontmatter.invalid-yaml`           | Error    | frontmatter YAML 解析失败，报告 PSI error description          | —                          |
| `frontmatter.name.missing`           | Error    | 缺少 `name` 或 `name` 为空                                   | `ADD_NAME`                 |
| `frontmatter.name.too-long`          | Error    | `name` 超过 64 字符                                         | —                          |
| `frontmatter.name.invalid`           | Error    | `name` 不符合 `^[a-z0-9]+(?:-[a-z0-9]+)*$`                 | `CONVERT_NAME_TO_KEBAB`    |
| `frontmatter.name.mismatch`          | Error    | `name` 与父目录名不一致                                         | `SYNC_NAME_WITH_DIRECTORY` |
| `frontmatter.description.missing`    | Error    | 缺少 `description` 或为空                                    | `ADD_DESCRIPTION`          |
| `frontmatter.description.too-long`   | Error    | `description` 超过 1024 字符                                | —                          |
| `frontmatter.compatibility.empty`    | Error    | 提供 `compatibility` 但值为空                                 | —                          |
| `frontmatter.compatibility.too-long` | Error    | `compatibility` 超过 500 字符                               | —                          |

## Quality Rules

| Rule ID                     | Severity     | 说明                                                                          |
|-----------------------------|--------------|-----------------------------------------------------------------------------|
| `description.too-short`     | Warning      | `description` 少于 20 字符，难以帮助 Agent 选择 skill                                  |
| `description.too-generic`   | Warning      | `description` 是 `helper` / `tool` / `skill` / `assistant` / `utility` 之一，过泛 |
| `description.missing-usage` | Warning      | `description` 中未出现 "use when"、"use it when"、"when to use"、"适用"、"使用" 等触发语    |
| `body.missing-title`        | Weak Warning | 正文未以一级 Markdown 标题开头                                                        |
| `body.missing-trigger`      | Warning      | 正文与 description 合并后仍未出现使用时机说明                                               |
| `body.too-long`             | Warning      | 正文超过 12000 字符，建议拆分到 `references/`                                           |

## Reference Rules

引用通过 Markdown PSI 提取 `LINK_DESTINATION` 元素，再在文件系统层校验。规则会跳过 URL、锚点、`mailto:` 以及绝对路径。

| Rule ID                     | Severity | 说明                                                        |
|-----------------------------|----------|-----------------------------------------------------------|
| `reference.invalid-path`    | Warning  | Markdown 链接目标不是合法路径（`InvalidPathException`）               |
| `reference.missing-file`    | Warning  | 相对链接指向的文件不存在，附 `CREATE_MISSING_REFERENCE` 修复（创建空文件 + 父目录） |
| `reference.outside-skill`   | Warning  | 引用路径 normalize 后跳出当前 skill 目录                             |
| `reference.case-mismatch`   | Warning  | 在大小写不敏感的文件系统上，链接大小写与目录项不一致（防止迁移到 Linux 后失效）               |
| `resource.unused-reference` | Warning  | `references/` 下的文件没被 SKILL.md 任何 Markdown 链接引用            |
| `script.missing-usage`      | Warning  | `scripts/` 下的脚本既没被链接引用、文件名也未出现在正文中                        |

引用规则按"链接 → 目标"方向，资源规则（`resource.*` / `script.*`）按"目标 → 是否被引用"反向，两者正交互补。所有规则都通过
`MarkdownReferenceParser` 复用同一个 PSI 解析结果，包括 inline link 与 reference-style 链接的 destination 节点。

## Security Rules

V1 只定位和提示，不自动改写。

| Rule ID                       | Severity | 说明                                                                    |
|-------------------------------|----------|-----------------------------------------------------------------------|
| `security.secret-pattern`     | Error    | 疑似 `api_key` / `token` / `secret` / `password` / `private_key` 字段及高熵值 |
| `security.dangerous-command`  | Error    | 出现 `rm -rf /` 或 `curl ... \| sh` / `wget ... \| sh` 等危险命令             |
| `security.allowed-tools-bash` | Warning  | `allowed-tools` 值中包含 `bash`（不区分大小写）                                   |
| `security.sensitive-path`     | Warning  | 引导访问 `.ssh` / `.env` / `~/.aws`                                       |
| `security.prompt-injection`   | Warning  | 出现 `ignore previous instructions` 等注入式文案                              |

## frontmatter 字段规范

基础字段以 [Agent Skills specification](https://agentskills.io/specification) 为准：

| 字段              | 规则                                                        |
|-----------------|-----------------------------------------------------------|
| `name`          | 必填；1-64 字符；只能包含小写字母、数字和连字符；不能以连字符开头或结尾；不能包含连续连字符；必须等于父目录名 |
| `description`   | 必填；1-1024 字符；不能为空；应说明 skill 做什么，以及什么时候使用                  |
| `license`       | 可选；许可证名称，或指向 skill 内许可证文件的引用                              |
| `compatibility` | 可选；1-500 字符；仅在存在特定运行环境要求时填写，例如目标产品、系统依赖、网络访问要求            |
| `metadata`      | 可选；字符串键值映射，用于保存规范未定义的扩展元数据                                |
| `allowed-tools` | 可选；空格分隔的预授权工具字符串；该字段仍是 experimental，不同 Agent 支持程度可能不同     |

字段检查的两层定位：

- **Specification errors**：违反官方规范的结构、必填字段、字段长度、命名规则和类型错误（上述 Structural Rules 全部归此）。
- **Compatibility warnings**：字段本身符合 specification，但在某些 Agent 实现中可能被忽略、不支持或语义不同（V2 引入 profile 后扩展）。

## 文件结构建议

V1 不强制 skill 目录布局，但下列结构是 specification 与实践共识：

- `SKILL.md` 应位于一个独立 skill 目录下。
- 父目录名应使用 kebab-case，并与 `name` 一致。
- `references/`、`scripts/`、`assets/` 是推荐目录，不强制存在。
- `references/` 下的文件应被 `SKILL.md` 通过相对链接引用。
- `scripts/` 下的脚本应在 `SKILL.md` 中说明用途和调用方式。

后两条已由 `resource.unused-reference` / `script.missing-usage` 覆盖，属于 V1 收尾时补齐的资源反向检查。

## Markdown 正文建议

- specification 不限制 Markdown 正文格式，正文应写任何能帮助 Agent 完成任务的说明。
- 建议包含 step-by-step instructions、输入输出示例、常见边界情况。
- 正文过长（> 12000 字符）会触发 `body.too-long`，建议把详细说明拆到 `references/`、`scripts/` 或 `assets/`。
- 引用本地文件时，路径必须存在且大小写一致。

## 跨 Agent 兼容性（计划）

不同 Agent 对 `SKILL.md` 字段和目录的支持不完全一致，V2 计划提供多套 profile：

- Generic Agent Skills
- Claude Code
- OpenAI Codex
- GitHub Copilot / VS Code
- JetBrains Junie
- Cursor
- Zeka Stack

V1 当前不区分 profile，全部按 Agent Skills specification 默认规则执行；将来会以兼容性 warning 形式补充提示，例如：

```text
allowed-tools is supported by Claude Code, but may be ignored by some Agent Skills implementations.
```
