# AGENTS.md - Skill Inspector 项目协作指南

本文档面向参与 Skill Inspector 项目开发的所有 Agent，用于快速理解项目上下文、技术约束和协作规范。

## 项目概述

**Skill Inspector** 是一个 JetBrains IDE 插件，专注于 Agent Skill 规范检查。核心使命是让 `SKILL.md` 文件在 IDEA 中具备实时 Inspection、Problems
面板展示和 Quick Fix 能力。

- **目标平台**: JetBrains IDE (IntelliJ IDEA 2024.2+)
- **规范基准**: [Agent Skills specification](https://agentskills.io/specification)
- **开发语言**: Java 21
- **构建工具**: Gradle Kotlin DSL

## 技术栈与关键配置

### 核心依赖

| 依赖                              | 版本      | 用途     |
|---------------------------------|---------|--------|
| IntelliJ Platform Gradle Plugin | 2.16.0  | 插件构建框架 |
| IntelliJ IDEA Platform          | 2024.2  | 目标平台   |
| Lombok                          | 1.18.32 | 代码生成   |
| JUnit 5                         | 5.9.2   | 测试框架   |

### 关键配置项 (`gradle.properties`)

```properties
pluginVersion=2026.1.1000
platformVersion=2024.2
platformSinceBuild=242.2
platformUntilBuild=261.*
javaVersion=21
```

### 平台兼容性

- **sinceBuild**: 242.2 (IntelliJ IDEA 2024.2)
- **untilBuild**: 261.* (长尾兼容至 2025.x)
- **支持版本**: IC/IU 2024.2 ~ 2025.3

### 零外部依赖原则

本项目**不依赖**任何第三方公共库（如 `idea-plugin-kit`），所有功能使用 IntelliJ Platform 原生 API 实现。

## 代码规范

### 语言选择

- **服务端代码**: Java 21 (使用 `record` 等现代特性)
- **禁止**: Kotlin（设计文档用 Java 示例，保持统一）

### 包结构

```text
src/main/java/dev/dong4j/idea/skill/inspector/
├── PluginContents.java          # 插件常量
├── action/                      # 用户交互入口
├── detection/                   # Skill 文件检测 (待实现)
├── inspection/                  # Inspection 实现 (待实现)
├── model/                       # 领域模型 (待实现)
├── parser/                      # frontmatter/Markdown 解析 (待实现)
├── quickfix/                    # Quick Fix 实现 (待实现)
├── rules/                       # 检查规则 (待实现)
├── settings/                    # 插件配置 (待实现)
├── toolwindow/                  # ToolWindow (待实现)
└── util/                        # 工具类
    ├── NotificationUtil.java      # 通知工具
    └── SkillInspectorBundle.java  # 国际化
```

### 注释风格

必须包含以下注释：

- 模块/类级说明（"为什么存在"）
- 关键函数说明（输入输出、约束条件）
- 复杂逻辑解释（"为什么这样做"）
- 禁止仅重复代码的注释（如 `// 获取用户`）

示例：

```java
/**
 * SkillFile 表示一个 SKILL.md 文件及其上下文.
 * <p> 关键约束:
 * <ul>
 *   <li>psiFile.name 必须等于 SKILL.md</li>
 *   <li>skillDirectoryName 来源于 SKILL.md 的父目录</li>
 * </ul>
 */
public record SkillFile(
    PsiFile psiFile,
    String skillDirectoryName,
    @Nullable VirtualFile skillRoot,
    @Nullable SkillFrontMatter frontMatter,
    SkillBody body
) {}
```

### 命名约定

| 类型     | 命名风格        | 示例                  |
|--------|-------------|---------------------|
| 类      | PascalCase  | `SkillFileDetector` |
| 方法     | camelCase   | `detectSkillFile()` |
| 常量     | UPPER_SNAKE | `PLUGIN_ID`         |
| 资源 key | snake_case  | `error.no.file`     |

## 文档结构

| 文档                            | 用途                   | 维护者         |
|-------------------------------|----------------------|-------------|
| `README.md`                   | 项目愿景、规范基准、核心目标       | 全员          |
| `docs/design.md`              | 领域模型、检查流程、模块划分       | 架构决策时更新     |
| `docs/todo.md`                | MVP 功能清单、待办任务        | 任务完成时迁出     |
| `docs/rules.md`               | 检查规则规范               | 规则变更时更新     |
| `docs/roadmap.md`             | 版本路线、技术方向、产品定位       | 里程碑达成时更新    |
| `docs/implementation-plan.md` | 当前实施优先级与多 Agent 协作计划 | 阶段任务推进时更新   |
| `AGENTS.md`                   | 本文件，Agent 协作上下文      | 技术栈/规范变更时更新 |

## 国际化 (i18n)

- **资源文件**: `src/main/resources/messages/SkillInspectorBundle.properties`
- **中文资源**: `SkillInspectorBundle_zh_CN.properties`（使用 Unicode 转义）
- **引用方式**: `SkillInspectorBundle.message("key")`

新增消息步骤：

1. 在英文资源添加键值
2. 同步更新中文资源
3. 在代码中使用 `SkillInspectorBundle.message("key", args...)`

## 检查规则体系

### 规则分层

1. **Structural Rules** (Error): 文件名、frontmatter 必填、目录名匹配
2. **Quality Rules** (Warning): 描述质量、正文结构
3. **Reference Rules** (Warning): 相对链接、资源引用
4. **Security Rules** (Error/Warning): 危险命令、敏感信息

### 规则 ID 命名

格式: `{category}.{specific}`

```
skill.file.name
frontmatter.missing
frontmatter.name.mismatch
description.too-short
reference.missing-file
security.dangerous-command
```

## 常见任务指南

### 添加新 Inspection

1. 在 `inspection/` 创建 `XxxInspection.java` 继承 `LocalInspectionTool`
2. 在 `plugin.xml` 注册 `<localInspection>` 扩展
3. 在 `rules/` 添加对应规则类（可脱离 IDE API 测试）
4. 更新 `docs/rules.md` 规则文档
5. 添加单元测试

### 添加 Quick Fix

1. 在 `quickfix/` 创建 `XxxFix.java` 实现 `LocalQuickFix`
2. 在对应 Inspection 中返回包含该 Fix 的 `ProblemDescriptor`
3. 更新 `docs/todo.md` 中的 Quick Fix 方向章节

### 修改插件配置

1. 在 `settings/` 创建/修改配置类
2. 在 `plugin.xml` 注册 `<applicationConfigurable>` 或 `<projectConfigurable>`
3. 使用 `SkillInspectorSettings` 单例访问配置

### 构建与验证

```bash
# 编译
./gradlew compileJava

# 运行 IDE 测试实例
./gradlew runIde

# 插件验证
./gradlew verifyPlugin

# 打包
./gradlew buildPlugin
```

## 重要约束

### 不做的事 (V1 明确排除)

- ❌ 不扫描 Maven/Gradle classpath 中的 SkillsJar
- ❌ 不同步到 `.claude/skills`、`.junie/skills` 等目录
- ❌ 不做远端 Skill Registry
- ❌ 不引入 LLM 自动改写正文
- ❌ 不做跨项目全局质量看板

这些保留到 V3+，详见 `docs/roadmap.md`。

### 必须遵守的原则

1. **零外部依赖**: 不使用 `idea-plugin-kit` 等第三方公共库
2. **Specification-first**: 以 Agent Skills 官方规范为默认基准
3. **Java-only**: 所有实现代码使用 Java，不用 Kotlin
4. **Profile 隔离**: Agent 私有扩展仅作兼容性提示，不作为默认错误
5. **保守修复**: Quick Fix 只处理确定性问题，不自动重写用户正文

## 调试技巧

### 查看通知

通知使用 `NotificationGroupManager`，组 ID 为 `Skill Inspector Notifications`。

### 查看日志

运行时日志位于 IDE 的 `Help > Diagnostic Tools > Debug Log Settings`，添加 `#dev.dong4j.idea.skill.inspector`。

### PSI 调试

使用 PSI Viewer (Tools > View PSI Structure of Current File) 分析 Markdown/YAML 结构。

## 参考资源

- [Agent Skills Specification](https://agentskills.io/specification)
- [IntelliJ Platform SDK Docs](https://plugins.jetbrains.com/docs/intellij/)
- [LocalInspectionTool API](https://plugins.jetbrains.com/docs/intellij/inspection-profiles.html)
- [NotificationGroupManager](https://plugins.jetbrains.com/docs/intellij/notifications.html)

## 更新本文件

当以下情况发生时，应更新 AGENTS.md：

- 技术栈变更（新增依赖、升级版本）
- 代码规范调整（语言选择、包结构变化）
- 重要设计决策（新增/移除核心能力）
- 常见任务模式变化（新增典型开发场景）

---

*最后更新: 2026-05-23 - 初始版本，整理项目基础信息*
