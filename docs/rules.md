# Skill Inspector 检查规则

本文档定义 Skill Inspector 对 `SKILL.md`
及其关联资源的完整检查规则集。所有规则以 [Agent Skills specification](https://agentskills.io/specification) 为默认基准。

> 待办任务见 [`./todo.md`](./todo.md)，版本路线见 [`./roadmap.md`](./roadmap.md)，领域模型见 [`./design.md`](./design.md)。

## 规则分层

规则按确定性和风险分为四层：

1. **Structural Rules**：结构规则，判断 skill 是否能被 Agent 正确识别（文件名、frontmatter 必填字段等）。
2. **Quality Rules**：质量规则，提高 skill 可用性，但不一定阻塞（描述质量、正文结构等）。
3. **Reference Rules**：引用规则，保证 skill 关联资源可访问（相对链接、资源引用等）。
4. **Security Rules**：安全规则，发现高风险内容（秘密泄露、危险命令等）。

## 文件结构

- `SKILL.md` 应位于一个独立 skill 目录下。
- 父目录名应使用 kebab-case。
- `name` 必须与父目录名一致。
- `references/`、`scripts/`、`assets/` 是推荐目录，不强制存在。
- `references/` 下的文件应被 `SKILL.md` 通过相对链接引用。
- `scripts/` 下的脚本应在 `SKILL.md` 中说明用途和调用方式。

## frontmatter 字段规则

基础字段以 [Agent Skills specification](https://agentskills.io/specification) 为准：

| 字段              | 规则                                                        |
|-----------------|-----------------------------------------------------------|
| `name`          | 必填；1-64 字符；只能包含小写字母、数字和连字符；不能以连字符开头或结尾；不能包含连续连字符；必须等于父目录名 |
| `description`   | 必填；1-1024 字符；不能为空；应说明 skill 做什么，以及什么时候使用                  |
| `license`       | 可选；许可证名称，或指向 skill 内许可证文件的引用                              |
| `compatibility` | 可选；1-500 字符；仅在存在特定运行环境要求时填写，例如目标产品、系统依赖、网络访问要求            |
| `metadata`      | 可选；字符串键值映射，用于保存规范未定义的扩展元数据                                |
| `allowed-tools` | 可选；空格分隔的预授权工具字符串；该字段仍是 experimental，不同 Agent 支持程度可能不同     |

字段检查分两层：

- **Specification errors**：违反官方规范的结构、必填字段、字段长度、命名规则和类型错误。
- **Compatibility warnings**：字段本身符合 specification，但在某些 Agent 实现中可能被忽略、不支持或语义不同。

## Markdown 正文规则

- specification 不限制 Markdown 正文格式，正文应写任何能帮助 Agent 完成任务的说明。
- 建议包含 step-by-step instructions。
- 建议包含输入输出示例。
- 建议说明常见边界情况。
- 如果正文过长，应提示拆分到 `references/`。
- 如果引用本地文件，应检查路径是否存在、大小写是否一致。
- 主 `SKILL.md` 建议保持轻量，详细说明放到 `references/`、`scripts/` 或 `assets/` 中按需加载。

## 跨 Agent 兼容性

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

## 安全规则

- 检查疑似 token、password、secret、private key。
- 检查 `rm -rf /`、`curl | sh` 等危险命令。
- 检查 `allowed-tools: Bash` 这类过宽权限。
- 检查访问 `.ssh`、`.env`、`~/.aws` 等敏感路径的说明。
- 检查 `ignore previous instructions` 等 prompt injection 风险文案。
- 检查硬编码绝对路径、内网地址和账号信息。
