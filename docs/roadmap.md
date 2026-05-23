# Skill Inspector Roadmap

本文档记录 Skill Inspector 的版本路线、技术实现方向与产品定位。

> 待办任务见 [`./todo.md`](./todo.md)，检查规则见 [`./rules.md`](./rules.md)，领域模型见 [`./design.md`](./design.md)，项目愿景见 [
`../README.md`](../README.md)。

## 版本路线

### V1: SKILL.md Inspection（已落地）

- 实时检查 `SKILL.md`（结构、质量、引用、安全四套规则）。
- Problems 面板展示问题。
- 保守 Quick Fix：补 frontmatter / `name` / `description` / 同步 `name` 为父目录名。
- Markdown 相对链接检查：缺失、目录越界、大小写、非法路径。
- 安全风险提示：疑似密钥、危险命令、过宽 `allowed-tools`、敏感路径、prompt injection。
- 应用级总开关 + 状态栏快速切换。

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

插件实现采用 IntelliJ Platform 的 Inspection 体系：

- `LocalInspectionTool` 负责检查 `SKILL.md`，单一 `SkillMdInspection` 通过内部 `RuleRunner` 调度多套规则。
- YAML frontmatter 由 Markdown PSI 定位 `FRONT_MATTER` 节点后交给 YAML PSI 解析（不再使用文本切分 / 正则）。
- Markdown 相对链接通过 Markdown PSI `LINK_DESTINATION` 节点提取，不做正则扫描。
- 问题通过 `ProblemDescriptor` 注册到 Problems 面板；严重度由 `SkillSeverity` 映射到 `ProblemHighlightType`。
- 自动修复通过单一 `SkillQuickFix` + `SkillFixType` 枚举派发，纯文本逻辑集中在 `SkillQuickFixTexts` 便于单测。
- 配置项放入 IDE Settings + Status Bar，V2 起逐步支持 profile 与规则开关。

实际代码结构（与 `docs/design.md` §9 同步，仅示意，不需对齐建议层级）：

```text
src/main/java/dev/dong4j/idea/skill/inspector/
├── action/
├── detection/
├── inspection/
├── model/
├── parser/
├── quickfix/
├── rules/
├── settings/
├── statusbar/
└── util/
```

## 产品定位

Skill Inspector 不是普通 Markdown Linter，而是 Agent Skill Authoring 工具。

目标是让 skill 的编写、检查、修复和后续分发具备工程化体验：

```text
写 Skill -> IDEA 实时检查 -> Quick Fix 修复 -> 构建期校验 -> 打包 / 分发 / 同步
```

第一阶段先把 `SKILL.md` 写作规范检查做扎实，再逐步扩展到 Skill Explorer、SkillsJar 管理和 Zeka Stack 企业规范治理。
