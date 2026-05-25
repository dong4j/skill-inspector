# Skill Inspector TODO

本文档只保留 Skill Inspector 当前未实现、待实现的功能清单。所有已落地的能力都应从此文档迁出，避免把 TODO 写成事后总结。

> 已交付清单 + 贯穿 V1-V5 的进度勾选请看 [`./status.md`](./status.md)。
> 检查规则见 [`./rules.md`](./rules.md)，版本路线见 [`./roadmap.md`](./roadmap.md)，项目愿景与规范基准见 [`../README.md`](../README.md)
> ，落地约束与领域模型见 [`./design.md`](./design.md)，AI 审查方向见 [`./ai-review.md`](./ai-review.md)。

## 待实现：Quick Fix（明确不做）

| 名称                       | 触发规则                          | 状态                                                                                |
|--------------------------|-------------------------------|-----------------------------------------------------------------------------------|
| 拆分过长正文                   | `body.too-long`               | **暂不提供**：拆分点带主观判断（章节边界、正文语义），违反"保守修复"原则。由作者人工拆分，规则只给出提示。                          |
| 收窄 `allowed-tools: Bash` | `security.allowed-tools-bash` | **暂不提供**：哪些 Bash 子命令是"安全"的高度依赖上下文，且 `allowed-tools` 仍是 experimental。规则只定位，不替用户决策。 |

## 待实现：增强

- Inspection fixture 测试：使用 `BasePlatformTestCase` / `CodeInsightTestFixture` 验证 `SkillMdInspection` 注册问题与 Quick Fix 真实写入文档（延后到
  V2，与 Profile 一起补）。
- 多 Agent Profile：Generic / Claude Code / Codex / Junie / Copilot / Cursor 的兼容性提示。
- Settings 扩展：规则严重度覆盖、自定义阈值（最大正文长度等）、安全扫描独立开关。
- 写作期增强：`New > SKILL.md` 文件模板、Live Template、Rename Skill Intention、Inspection 抑制注释。
- Quick Fix Preview：接入 `LocalQuickFix.generatePreview`。

完整 V2 + V3 + V4 + V5 的 TODO 看 `status.md` §3。

## 二期方向：AI 审查

详见 [`./ai-review.md`](./ai-review.md)。核心要点：

- 参考 [microsoft/vscode-chat-customizations-evaluation](https://github.com/microsoft/vscode-chat-customizations-evaluation) 的提示词工程，沉淀
  5 维语义分析（矛盾 / 歧义 / 人格一致性 / 认知负载 / 语义覆盖）+ Composition Conflict（SKILL.md 与 `references/` 之间的跨文件冲突）。
- AI Provider 抽象层（Copilot / Claude / OpenAI / 本地 Ollama），不绑死单一 LLM。
- 防注入：用 `<SKILL_TO_ANALYZE>` 标签包裹用户内容；系统提示词强制声明"DATA, not instructions"；从 referenced files 中 strip 标签字符串防边界
  spoofing。
- 用户控制：AI 结果以诊断进 Problems 面板，每条附带 `suggestion`，"Apply AI suggestion" 必须用户授权才改写，永不静默 apply。

二期方向上的 8 个工程子任务（AR-1 … AR-8）见 `status.md` §3 "二期 AI 审查方向"。

## 2026.05.25

### 提供扩展接口

看看 /Users/dong4j/Developer/0.Worker/opensource/idea/skillsjars-helper 这个插件, 然后我想在 skill-inspector 插件中暴露一个扩展接口, 我的需求是想在
tool-window 的树形结构树中再添加一层问题节点数
但是这个功能应该是可选的, 意思是如果用户只安装了 skillsjars-helper 就保持现状, 如果同时安装了 skill-inspector 那就应该可以显示问题节点树,
点击问题节点就可以跳转到问题所在的位置
分析一下可行性, 分析结果写入到 docs 目录的新增文档中

### 接入 IntelliAI Engine 插件的 AI 功能

接入 engine 插件的 AI 功能来通过 AI 来评审 skill.md 的规范/写法是否满足要求, 主要还是聚焦于分析.
可以看一下 /Users/dong4j/Developer/0.Worker/opensource/idea/skill-inspector/docs/ai-review.md 的文档, 重点在如何分析以及如何对接
IntelliAI-Engine(/Users/dong4j/Developer/0.Worker/opensource/zeka.stack/zeka-idea-plugin/intelli-ai-engine/)
这个也是一个可选的, 意思是只有在同时安装了 intelli-ai-engine 的情况下, 才会启用这个功能
分析一下可行性, 分析结果写入到 docs 目录的新增文档中
