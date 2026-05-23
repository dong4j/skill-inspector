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

- `MarkdownReferenceParser` 复用 Markdown PSI `LINK_DESTINATION` 节点抽取链接目标，避免正则误判语法。
- 规则覆盖 `reference.invalid-path` / `reference.missing-file` / `reference.outside-skill` / `reference.case-mismatch`，自动跳过 URL / 锚点 / 绝对路径 / `mailto:`。
- 通过依赖注入支持单元测试：`ReferenceRules` 提供 `skillDirectoryResolver` + `referenceResolver` 构造函数。

### Phase 3: 单元测试覆盖 ✅（基础版本）

- `FrontMatterParserTest`：有效 frontmatter、缺失 frontmatter、缺失 closing delimiter、不完整 YAML、引号值、UTF-8 BOM、嵌套 metadata、块标量 description。
- `MarkdownReferenceParserTest`：inline link / image link / 带 title 的链接 / 空目标。
- `StructuralRulesTest`：缺失 frontmatter、必填字段、非法 name、name 不匹配、name/description/compatibility 长度上限、有效结构。
- `QualityRulesTest`：短描述、泛描述、缺触发语、缺正文标题、有效正文、官方 brand-guidelines 形态。
- `ReferenceRulesTest`：缺失引用、目录越界、URL 跳过、已有引用、大小写不一致。
- `SecurityRulesTest`：secret、危险命令、Bash 权限、敏感路径、prompt injection。
- `RuleRunnerTest`：默认规则集合接入、官方样例不误报、specification 违例端到端命中。
- `SkillQuickFixTextsTest`：frontmatter 模板、字段插入换行、目录名 fallback。
- `SkillInspectorSettingsTest`：默认值、修改、状态恢复。
- `SkillFileDetectorTest`：文件名匹配。

### Phase 4: 设置入口与状态栏开关 ✅

- `SkillInspectorSettings`（应用级 `PersistentStateComponent`）只保存 `skillInspectionEnabled`。
- `SkillInspectorConfigurable` 提供 `Settings | Tools | Skill Inspector` 入口。
- `SkillInspectorStatusBarWidget` + `SkillInspectorStatusBarWidgetFactory` 提供状态栏快速切换。

## 进行中

无。当前已完成的能力已覆盖 V1 设计目标，下一阶段任务全部在"未开始"列出。

## 未开始

### 规则增强

- `resource.unused-reference`：扫描 `references/` 下未被引用文件，定位 skill 与正文。
- `script.missing-usage`：检查 `scripts/` 下脚本是否在正文中说明用法。
- Markdown reference-style links 解析。

### Quick Fix 增强

- 拆分过长正文 / 收窄 `allowed-tools` 评估后**暂不实现**，原因见 `docs/todo.md`。

### Inspection Fixture 测试

- 真正启动 IntelliJ fixture，验证 `SkillMdInspection` 注册 `ProblemDescriptor`。
- 验证 `LocalQuickFix.applyFix` 实际改写文档内容。

### 配置 / Profile（对应 roadmap V2）

- 多 Agent Profile：Generic / Claude Code / Codex / Junie / Copilot / Cursor。
- 规则严重度覆盖、自定义阈值（最大正文长度等）。
- 安全扫描独立开关。

### Action 增强

- `SkillInspectorAction` 当前只触发占位通知，作为右键菜单入口保留。后续可让它一次性运行 Inspection 并把结果写入 Problems 面板，作为 PR Review 场景的手动入口。

## 当前验证命令

```bash
./gradlew test
./gradlew check
```

`check` 会触发 `verifyPlugin`，需要联网下载多个 IDE 版本进行兼容性扫描。
