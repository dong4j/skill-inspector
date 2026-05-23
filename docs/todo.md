# Skill Inspector TODO

本文档只保留 Skill Inspector 当前未实现、待实现的功能清单。所有已落地的能力都应从此文档迁出，避免把 TODO 写成事后总结。

> 检查规则见 [`./rules.md`](./rules.md)，版本路线见 [`./roadmap.md`](./roadmap.md)，项目愿景与规范基准见 [`../README.md`](../README.md)
> ，落地约束与领域模型见 [`./design.md`](./design.md)。

## 已交付能力（MVP）

V1 MVP 已经在主线代码落地，留作历史索引：

- `SKILL.md` 文件名识别（`SkillFileDetector`）。
- frontmatter 解析：Markdown PSI 定位 + YAML PSI 解析，支持 UTF-8 BOM 与 `>` / `|` 块标量。
- 必填字段检查（`name` / `description`）、长度上限（`name` 64 字符、`description` 1024 字符、`compatibility` 500 字符）。
- `name` kebab-case 规范、`name` 与父目录名一致性。
- `compatibility` 提供时不允许为空。
- 描述质量提示：过短、过泛、缺触发语。
- 正文质量提示：缺一级标题、缺触发语、正文过长（> 12000 字符）。
- Markdown 引用检查：相对链接缺失、目录越界、大小写不一致、非法路径；自动跳过 URL / 锚点 / 绝对路径 / `mailto:`。
- 安全扫描：疑似密钥、`rm -rf /` / `curl | sh` 等危险命令、过宽 `allowed-tools: Bash`、`.ssh` / `.env` / `~/.aws` 等敏感路径、`ignore previous instructions` 类 prompt injection。
- Quick Fix（6 种）：添加 frontmatter、添加 `name`、添加 `description`、同步 `name` 为父目录名、将非法 `name` 转换为 kebab-case、为缺失引用创建空文件。
- 父目录名 kebab-case 检查（`skill.directory.name`），与 `frontmatter.name.mismatch` 互补。
- 应用级设置 + 状态栏开关：单一总开关 `skillInspectionEnabled`。
- 单元测试覆盖：parser / 4 套 rules / RuleRunner / Quick Fix 文本生成 / settings。

## 待实现：规则

| 规则 ID                      | 类别         | 说明                                                |
|----------------------------|------------|---------------------------------------------------|
| `resource.unused-reference` | Reference  | `references/` 下未被 `SKILL.md` 通过相对链接引用             |
| `script.missing-usage`     | Reference  | `scripts/` 下脚本未在正文中说明用法                           |

## 待实现：Quick Fix

| 名称                  | 触发规则                          | 状态                                                                |
|---------------------|-------------------------------|-------------------------------------------------------------------|
| 拆分过长正文              | `body.too-long`               | **暂不提供**：拆分点带主观判断（章节边界、正文语义），违反"保守修复"原则。由作者人工拆分，规则只给出提示。      |
| 收窄 `allowed-tools: Bash` | `security.allowed-tools-bash` | **暂不提供**：哪些 Bash 子命令是"安全"的高度依赖上下文，且 `allowed-tools` 仍是 experimental。规则只定位，不替用户决策。 |

## 待实现：增强

- Inspection fixture 测试：使用 `BasePlatformTestCase` / `CodeInsightTestFixture` 验证 `SkillMdInspection` 注册问题与 Quick Fix 真实写入文档。
- Markdown reference-style links 解析（目前只识别 inline link / image link 的 `LINK_DESTINATION`）。
- 多 Agent Profile：Generic / Claude Code / Codex / Junie / Copilot / Cursor 的兼容性提示。
- Settings 扩展：规则严重度覆盖、自定义阈值（最大正文长度等）、安全扫描独立开关。

## 二期方向

- 接入 AI 审查能力，让 AI 基于 Agent Skills specification 和项目内置规则检查 `SKILL.md` 的规范性，并给出可执行的优化建议。
- Skill Explorer ToolWindow（V3）、SkillsJar Manager（V4）、Zeka Stack 集成（V5），详见 `roadmap.md`。
