# Skill Inspector TODO

本文档只保留 Skill Inspector 当前未实现、待实现的功能清单。所有已落地的能力都应从此文档迁出，避免把 TODO 写成事后总结。

> 已交付清单 + 贯穿 V1-V5 的进度勾选请看 [`./status.md`](./status.md)。
> 检查规则见 [`./rules.md`](./rules.md)，版本路线见 [`./roadmap.md`](./roadmap.md)，项目愿景与规范基准见 [`../README.md`](../README.md)
> ，落地约束与领域模型见 [`./design.md`](./design.md)，AI 审查方向见 [`./ai-review.md`](./ai-review.md)。

## 已交付能力（V1 + V1 收尾）

V1 MVP 与收尾任务已全部在主线代码落地，留作历史索引（精确清单见 `status.md` §2 / §3）：

- `SKILL.md` 文件名识别（`SkillFileDetector`）。
- frontmatter 解析：Markdown PSI 定位 + YAML PSI 解析，支持 UTF-8 BOM 与 `>` / `|` 块标量。
- 必填字段检查（`name` / `description`）、长度上限（`name` 64 字符、`description` 1024 字符、`compatibility` 500 字符）。
- `name` kebab-case 规范、`name` 与父目录名一致性、父目录名 kebab-case。
- `compatibility` 提供时不允许为空。
- 描述质量提示：过短、过泛、缺触发语。
- 正文质量提示：缺一级标题、缺触发语、正文过长（> 12000 字符）。
- Markdown 引用检查：相对链接缺失、目录越界、大小写不一致、非法路径；自动跳过 URL / 锚点 / 绝对路径 / `mailto:`；**inline link 与 reference-style
  链接都被 PSI 递归解析覆盖**。
- 资源孤儿检查：`resource.unused-reference`（`references/` 下未被引用）、`script.missing-usage`（`scripts/` 下既无链接也无文本提及）。
- 安全扫描：疑似密钥、`rm -rf /` / `curl | sh` 等危险命令、过宽 `allowed-tools: Bash`、`.ssh` / `.env` / `~/.aws` 等敏感路径、
  `ignore previous instructions` 类 prompt injection。
- Quick Fix（6 种）：添加 frontmatter、添加 `name`、添加 `description`、同步 `name` 为父目录名、将非法 `name` 转换为 kebab-case、为缺失引用创建空文件。
- 应用级设置 + 状态栏开关：单一总开关 `skillInspectionEnabled`；状态栏改为 `IconPresentation`，单击图标直接切换。
- 右键 `Validate Skill` Action 实化：扫描项目里所有 `SKILL.md`，跑 `RuleRunner` 汇总 Error / Warning，自动激活 Problems View 并打开第一个有错的文件。
- **编辑器浮动按钮 PoC（双路并存）**：在 SKILL.md 编辑器内同时提供两种悬浮入口供产品对比：
    - **方案 A**（`floating/SkillFloatingToolbarProvider`）：官方 `editorFloatingToolbarProvider` 扩展点，
      位置=右上角，矩形浮动栏，鼠标悬停淡入，自动适配 IDE 主题与 ESC 关闭。
    - **方案 B**（`floating/SkillBottomFloatingButton` + `SkillBottomFloatingInstaller`）：自定义 36 dp
      蓝色圆形 FAB，挂在编辑器 `JLayeredPane#PALETTE_LAYER`，位置=右下角（带阴影 + 悬停加深），
      通过 `AppLifecycleListener` 启动 + `EditorFactoryListener` 逐编辑器挂载，编辑器释放时自动清理。
    - 两种方案点击后均走同一个 `SkillInspectorAction`（通过 `PluginContents.ACTION_VALIDATE_SKILL_ID`
      集中维护 ID），不重复实现扫描逻辑。
- 单元测试覆盖（63 个用例）：parser / 5 套 rules / RuleRunner / Quick Fix 文本 / settings / detector。

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
- 写作期增强：`New > SKILL.md` 文件模板、Live Template、Rename Skill Intention、Indusj@5282010spection 抑制注释、状态栏问题角标。
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
