# Skill Inspector — Agent Skills 索引

本目录沉淀 Skill Inspector 项目（以及未来同构 IDEA 插件）的可复用开发经验，覆盖**检查规则、Quick Fix、UI 接入、设置/通知/图标/国际化**等核心扩展点。

每个 skill 描述了一个原子开发任务的**完整步骤**
：要改哪些文件、要写什么模板代码、要跑什么命令验证。它们的写法风格参考了 [gitkraken/vscode-gitlens](https://github.com/gitkraken/vscode-gitlens/tree/main/.claude/skills)
的 `.claude/skills` 目录。

## 何时使用

- 直接执行 `/skill-name` 形式的任务时（例如 `/add-rule frontmatter.tags.invalid`）。
- 不熟悉某个扩展点的接线时（例如"如何让一个 Widget 出现在状态栏？"）。
- 想把自己的 IDEA 插件套用本项目的最佳实践时。

## 项目级共识（先读这里）

- **零外部依赖**：所有功能用 IntelliJ Platform 原生 API，不引入 `idea-plugin-kit` 等第三方公共库。
- **Java-only**：所有实现代码使用 Java 21；不要写 Kotlin 业务代码（`build.gradle.kts` 是例外）。
- **Specification-first**：检查规则以 [Agent Skills specification](https://agentskills.io/specification) 为默认基准。
- **国际化必须双语同步**：`SkillInspectorBundle.properties` 和 `SkillInspectorBundle_zh_CN.properties` 永远成对维护。
- **Quick Fix 保守原则**：只处理确定性问题，不替用户改写正文。

更多上下文见仓库根目录的 `AGENTS.md`、`docs/design.md`、`docs/rules.md`。

## Skill 列表

### 核心扩展点

| Skill                                          | 用途                             | 何时用                     |
|------------------------------------------------|--------------------------------|-------------------------|
| [`/add-rule`](./add-rule/SKILL.md)             | 新增 SKILL.md 检查规则               | 想在 Problems 面板里新增一种问题提示 |
| [`/add-quickfix`](./add-quickfix/SKILL.md)     | 为规则添加 Quick Fix                | 想让现有规则可以一键修复            |
| [`/add-inspection`](./add-inspection/SKILL.md) | 新建独立 Inspection（非 SKILL.md 文件） | 需要检查 SKILL.md 之外的文件类型   |

### UI / 接入点

| Skill                                                      | 用途                 | 何时用           |
|------------------------------------------------------------|--------------------|---------------|
| [`/add-action`](./add-action/SKILL.md)                     | 新增 Action（含后台扫描套路） | 加菜单项 / 工具栏按钮  |
| [`/add-statusbar-widget`](./add-statusbar-widget/SKILL.md) | 新增状态栏组件            | 在状态栏暴露开关 / 状态 |
| [`/add-icon`](./add-icon/SKILL.md)                         | 添加并使用图标            | 自定义图标 + 主题适配  |
| [`/add-notification`](./add-notification/SKILL.md)         | 发送通知（气泡）           | 完成后向用户反馈结果    |

### 基础设施

| Skill                                      | 用途      | 何时用          |
|--------------------------------------------|---------|--------------|
| [`/add-i18n-key`](./add-i18n-key/SKILL.md) | 添加国际化键  | 任何用户可见文本（必走） |
| [`/add-setting`](./add-setting/SKILL.md)   | 添加应用级设置 | 全局开关 / 配置项   |

## 通用工作流

无论使用哪个 skill，最终都要走完这套验证：

```bash
./gradlew compileJava       # 编译
./gradlew test              # 单元测试
./gradlew runIde            # 在 IDE 实例里手测
./gradlew verifyPlugin      # 跨版本兼容性验证
```

## 维护本目录

- 当某个扩展点的"标准写法"发生**重大变化**时，更新对应 skill。
- 新增的 skill 必须遵循已有结构：`YAML frontmatter` + `Usage` + `Information Needed` + `Files to Create/Modify` + `Build & Verify`。
- 不要在 skill 里抄 spec 全文，引用 `docs/rules.md` 之类的活文档即可。
