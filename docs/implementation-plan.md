# Skill Inspector Implementation Plan

本文档按"已完成 / 进行中 / 未开始"三段视图记录 Skill Inspector 的实施状态，方便多个 Agent 协作时快速接续。

> 产品路线见 [`./roadmap.md`](./roadmap.md)，规则规范见 [`./rules.md`](./rules.md)，详细设计见 [`./design.md`](./design.md)，待办与剩余 Quick Fix 见 [`./todo.md`](./todo.md)。

## 优先级原则

1. 先打通最小可用的 IDE Inspection 闭环，再扩展规则覆盖面。
2. 规则层保持纯 Java、轻 IDE 依赖，便于后续复用到 CLI 或构建期校验。
3. Quick Fix 只修复确定性问题，不自动重写用户正文。
4. 默认规范以 Agent Skills specification 为准，Agent 私有差异放入后续 profile。

## 已完成

### Phase 1: V1 Inspection 最小闭环 ✅

- `SkillFileDetector` 仅根据文件名启用检查。
- `FrontMatterParser` 使用 Markdown PSI + YAML PSI 解析 frontmatter，兼容 UTF-8 BOM 与块标量。
- `SkillModelBuilder` 把 PSI 转换为规则层稳定模型（`SkillFile` / `SkillFrontMatter` / `SkillMetadata` / `SkillBody`）。
- `StructuralRules`：`skill.directory.name` / `frontmatter.missing` / `frontmatter.invalid-yaml` / `frontmatter.name.missing` / `frontmatter.name.too-long` / `frontmatter.name.invalid` / `frontmatter.name.mismatch` / `frontmatter.description.missing` / `frontmatter.description.too-long` / `frontmatter.compatibility.empty` / `frontmatter.compatibility.too-long`。
- `QualityRules`：`description.too-short` / `description.too-generic` / `description.missing-usage` / `body.missing-title` / `body.missing-trigger` / `body.too-long`。
- `SecurityRules`：`security.secret-pattern` / `security.dangerous-command` / `security.allowed-tools-bash` / `security.sensitive-path` / `security.prompt-injection`。
- `SkillMdInspection` 适配层把 `SkillProblem` 映射为 `ProblemDescriptor` 并挂接 Quick Fix。
- 保守 Quick Fix（6 种）：`ADD_FRONT_MATTER` / `ADD_NAME` / `ADD_DESCRIPTION` / `SYNC_NAME_WITH_DIRECTORY` / `CONVERT_NAME_TO_KEBAB` / `CREATE_MISSING_REFERENCE`。
- 文本生成纯函数集中在 `SkillQuickFixTexts`（包含 `toKebabCaseName` / `stripFragmentAndQuery`），便于单元测试。

### Phase 2: Reference Rules ✅（基础版本）

- `MarkdownReferenceParser` 复用 Markdown PSI `LINK_DESTINATION` 节点抽取链接目标，避免正则误判语法；inline link 与 reference-style 链接都通过递归遍历自动覆盖。
- 规则覆盖 `reference.invalid-path` / `reference.missing-file` / `reference.outside-skill` / `reference.case-mismatch`，自动跳过 URL / 锚点 / 绝对路径 / `mailto:`。
- 通过依赖注入支持单元测试：`ReferenceRules` 提供 `skillDirectoryResolver` + `referenceResolver` 构造函数。

### Phase 3: 单元测试覆盖 ✅（基础版本）

- `FrontMatterParserTest`：有效 frontmatter、缺失 frontmatter、缺失 closing delimiter、不完整 YAML、引号值、UTF-8 BOM、嵌套 metadata、块标量 description。
- `MarkdownReferenceParserTest`：inline link / image link / 带 title 的链接 / 空目标 / reference-style link。
- `StructuralRulesTest`：缺失 frontmatter、必填字段、非法 name、name 不匹配、name/description/compatibility 长度上限、有效结构。
- `QualityRulesTest`：短描述、泛描述、缺触发语、缺正文标题、有效正文、官方 brand-guidelines 形态。
- `ReferenceRulesTest`：缺失引用、目录越界、URL 跳过、已有引用、大小写不一致。
- `ResourceRulesTest`：未使用资源、嵌套未使用资源、脚本无用法、脚本通过链接 / 文件名提及视为已用、未提供目录时跳过、URL/锚点不算引用。
- `SecurityRulesTest`：secret、危险命令、Bash 权限、敏感路径、prompt injection。
- `RuleRunnerTest`：默认规则集合接入、官方样例不误报、specification 违例端到端命中。
- `SkillQuickFixTextsTest`：frontmatter 模板、字段插入换行、目录名 fallback。
- `SkillInspectorSettingsTest`：默认值、修改、状态恢复。
- `SkillFileDetectorTest`：文件名匹配。

### Phase 4: 设置入口与状态栏开关 ✅

- `SkillInspectorSettings`（应用级 `PersistentStateComponent`）只保存 `skillInspectionEnabled`。
- `SkillInspectorConfigurable` 提供 `Settings | Tools | Skill Inspector` 入口。
- `SkillInspectorStatusBarWidget` + `SkillInspectorStatusBarWidgetFactory` 提供状态栏快速切换；最终版采用 `IconPresentation` + `getClickConsumer`，单击图标直接切换并通过 `AllIcons.General.Inspections*` 两个图标做颜色对比。

### Phase 5: V1 收尾 ✅

- `ResourceRules`：新增 `resource.unused-reference` / `script.missing-usage` 两条规则，从"目录孤儿"反向出发，与 `ReferenceRules` 正交互补。
- Markdown reference-style links：通过 PSI 递归遍历天然覆盖 `LINK_DEFINITION` 节点中的 `LINK_DESTINATION`，新增专门测试用例锁定行为。
- `SkillInspectorAction` 实化：扫描项目里所有 `SKILL.md`，跑 `RuleRunner` 统计 Error / Warning，激活 Problems View 并自动打开第一个有错的文件。在后台 `Task.Backgroundable` 内执行，大项目下不阻塞 UI。
- 单元测试总数 63，覆盖 5 套规则 + parser + Quick Fix + settings + detector + RuleRunner。

## 进行中

无。V1 与 V1 收尾任务全部完成，下一阶段任务全部在"未开始"列出。

## 未开始

### 配置 / Profile（对应 roadmap V2）

- 多 Agent Profile：Generic / Claude Code / Codex / Junie / Copilot / Cursor。
- 规则严重度覆盖、自定义阈值（最大正文长度等）。
- 安全扫描独立开关。

### Inspection Fixture 测试（延后到 V2，与 Profile 一起补）

- 真正启动 IntelliJ fixture，验证 `SkillMdInspection` 注册 `ProblemDescriptor`。
- 验证 `LocalQuickFix.applyFix` 实际改写文档内容。
- 验证 `SkillInspectorAction` 在打开 SKILL.md 后能让 Problems Panel 显示完整规则结果。

### 写作期增强（V2 收尾）

- `New > SKILL.md` 文件模板：注册 `FileTemplate`，新建即带合规 frontmatter。
- Live Template / Postfix 补全：`skill-meta`、`skill-section` 等。
- Intention：Rename Skill（同步重命名父目录 + name）。
- Inspection 抑制注释：支持 `<!-- noinspection ruleId -->` 行级抑制。
- 状态栏图标问题角标：SKILL.md 有 N 个 Error 时显示数字角标。
- Quick Fix Preview：接入 `LocalQuickFix.generatePreview`。

### Quick Fix 增强（明确不做）

- 拆分过长正文 / 收窄 `allowed-tools` 评估后**暂不实现**，原因见 `docs/todo.md`。

### 二期：AI 审查（对应 roadmap"二期方向"）

详见 [`./ai-review.md`](./ai-review.md)，8 个工程子任务 AR-1 … AR-8 见 `status.md` §3。

## 当前验证命令

```bash
./gradlew test
./gradlew check
```

`check` 会触发 `verifyPlugin`，需要联网下载多个 IDE 版本进行兼容性扫描。
