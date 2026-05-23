# Skill Inspector Implementation Plan

本文档记录 Skill Inspector 的实施优先级和阶段状态，方便多个 Agent 协作时快速接续。

> 产品路线见 [`./roadmap.md`](./roadmap.md)，规则规范见 [`./rules.md`](./rules.md)，详细设计见 [`./design.md`](./design.md)。

## 优先级原则

1. 先打通最小可用的 IDE Inspection 闭环，再扩展规则覆盖面。
2. 规则层保持纯 Java、轻 IDE 依赖，便于后续复用到 CLI 或构建期校验。
3. Quick Fix 只修复确定性问题，不自动重写用户正文。
4. 默认规范以 Agent Skills specification 为准，Agent 私有差异放入后续 profile。

## Phase 1: V1 Inspection 最小闭环

状态：已启动。

目标：

- 识别文件名为 `SKILL.md` 的文件。
- 解析文件开头 YAML frontmatter。
- 检查 `name`、`description` 必填字段。
- 检查 `name` 是否为 kebab-case 且匹配父目录名。
- 检查基础 description/body 质量问题。
- 检查常见安全风险。
- 接入 IntelliJ Problems 面板。
- 提供保守 Quick Fix：
    - 添加 frontmatter。
    - 添加 `name` 字段。
    - 添加 `description` 字段。
    - 同步 `name` 为父目录名。

## Phase 2: Reference Rules

状态：基础版本已实现。

目标：

- 解析 Markdown 相对链接。
- 检查引用文件是否存在。
- 检查引用路径是否跳出当前 skill 目录。
- 检查路径大小写是否与文件系统一致。
- 检查 `references/` 下未引用文件。
- 检查 `scripts/` 下脚本是否在正文说明用途。

已完成：

- 支持 inline link / image link 解析。
- 跳过 URL、锚点、绝对路径和 mailto 链接。
- 检查相对路径缺失、越界和大小写不一致。

待增强：

- 支持 Markdown reference-style links。
- 检查 `references/` 下未引用文件。
- 检查 `scripts/` 下脚本是否在正文说明用途。

## Phase 3: 测试覆盖

状态：基础单元测试已实现。

目标：

- Parser 单元测试：
    - frontmatter 有效。
    - frontmatter 缺失。
    - closing delimiter 缺失。
    - 不支持的 YAML 行。
- Rule 单元测试：
    - 每条规则至少一个 positive 和 negative case。
- Inspection fixture 测试：
    - `SKILL.md` 能注册问题。
    - 非 `SKILL.md` 不启用检查。
- Quick Fix 测试：
    - 修复后文档内容符合预期。

已完成：

- `FrontMatterParserTest` 覆盖有效 frontmatter、缺失 frontmatter、缺失 closing delimiter、不支持 YAML 行和引号值。
- `MarkdownReferenceParserTest` 覆盖 inline link、image link、带 title 的链接和空目标。
- `StructuralRulesTest` 覆盖缺失 frontmatter、缺失必填字段、非法 name、name 不匹配和有效结构。
- `QualityRulesTest` 覆盖短 description、泛 description、正文标题/触发说明缺失和有效正文。
- `SecurityRulesTest` 覆盖 secret、危险命令、Bash 权限、敏感路径和 prompt injection。
- `ReferenceRulesTest` 覆盖缺失引用、目录越界、URL 跳过、已有引用和大小写不一致。
- `RuleRunnerTest` 覆盖默认规则集合接入。
- `SkillQuickFixTextsTest` 覆盖 Quick Fix 文本模板、字段插入换行和 fallback skill name。

待增强：

- Inspection fixture 测试。
- 真正执行 IDE `LocalQuickFix` 的 fixture 测试。

## Phase 4: 配置与 Profile

状态：待实现。

目标：

- 增加 Settings 页面。
- 支持规则开关。
- 支持最大正文长度配置。
- 支持 Generic / Claude Code / Codex / Cursor 等 profile 的兼容性提示。

## 当前实现摘要

已实现包：

```text
src/main/java/dev/dong4j/idea/skill/inspector/
├── detection/
├── inspection/
├── model/
├── parser/
├── quickfix/
└── rules/
```

当前验证命令：

```bash
./gradlew test
./gradlew check
```
