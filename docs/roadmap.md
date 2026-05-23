# Skill Inspector Roadmap

本文档记录 Skill Inspector 的版本路线、技术实现方向与产品定位。

> 待办任务见 [`./todo.md`](./todo.md)，检查规则见 [`./rules.md`](./rules.md)，领域模型见 [`./design.md`](./design.md)，项目愿景见 [
`../README.md`](../README.md)。

## 版本路线

### V1: SKILL.md Inspection

- 实时检查 `SKILL.md`。
- Problems 面板展示问题。
- 基础 Quick Fix。
- Markdown 相对链接检查。
- 安全风险提示。

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

插件实现会优先采用 IntelliJ Platform 的 Inspection 体系：

- `LocalInspectionTool` 负责检查 `SKILL.md`。
- YAML frontmatter 先使用文本切分和 YAML parser 解析。
- Markdown 相对链接通过 PSI 或 Markdown 文本扫描识别。
- 问题通过 `ProblemDescriptor` 注册到 Problems 面板。
- 自动修复通过 `LocalQuickFix` 修改文档内容或创建文件。
- 配置项放入 IDE Settings，后续支持 profile 和规则开关。

建议的代码结构：

```text
src/main/java/dev/dong4j/idea/skill/inspector/
├── inspection/
│   ├── SkillMdInspection.java
│   ├── SkillFrontMatterInspection.java
│   ├── SkillReferenceInspection.java
│   └── SkillSecurityInspection.java
├── parser/
│   ├── SkillFileDetector.java
│   ├── FrontMatterParser.java
│   └── SkillModel.java
├── quickfix/
│   ├── AddMissingFrontMatterFix.java
│   ├── RenameSkillNameFix.java
│   ├── CreateMissingReferenceFix.java
│   └── RestrictAllowedToolsFix.java
├── settings/
│   └── SkillInspectorSettings.java
└── toolwindow/
    └── SkillExplorerToolWindowFactory.java
```

## 产品定位

Skill Inspector 不是普通 Markdown Linter，而是 Agent Skill Authoring 工具。

目标是让 skill 的编写、检查、修复和后续分发具备工程化体验：

```text
写 Skill -> IDEA 实时检查 -> Quick Fix 修复 -> 构建期校验 -> 打包 / 分发 / 同步
```

第一阶段先把 `SKILL.md` 写作规范检查做扎实，再逐步扩展到 Skill Explorer、SkillsJar 管理和 Zeka Stack 企业规范治理。
