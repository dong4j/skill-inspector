---
name: add-inspection
description: 何时新建独立的 LocalInspectionTool（非 SKILL.md 文件检查）
---

# /add-inspection — 新建独立 Inspection

为**非 SKILL.md 文件**新增一个独立的 IntelliJ `LocalInspectionTool`。绝大多数 SKILL.md 检查应走 `/add-rule` 复用 `SkillMdInspection`，**不要**
为每条规则建一个 inspection。

## 何时使用本 skill

| 需求                                                | 选择                                                    |
|---------------------------------------------------|-------------------------------------------------------|
| 检查 SKILL.md 内部的某种新模式                              | ❌ 用 `/add-rule`                                       |
| 检查 `references/*.md` 自身格式                         | ✅ 用 `/add-inspection`                                 |
| 检查 `package.json` / `pyproject.toml` 中的 skill 元数据 | ✅ 用 `/add-inspection`                                 |
| 检查 `.claude/skills/*/SKILL.md` 这种特殊路径             | ✅ 用 `/add-inspection`（因为 `SkillFileDetector` 默认只看文件名） |

## Usage

```
/add-inspection [InspectionShortName]
```

- `InspectionShortName` — IntelliJ shortName（不带 `Inspection` 后缀），例如 `SkillReferencesInspection`。

## Information Needed

1. **目标语言** — `Markdown` / `JSON` / `XML` / `Python` / `JAVA` / `YAML` 之一，决定 `<localInspection language="..."/>` 注册值。
2. **文件 detector** — 用什么条件认定"该 inspection 要跑"（例如
   `file.getName().endsWith(".md") && file.getParent().getName().equals("references")`）。
3. **严重度默认值** — `level="ERROR" | "WARNING" | "WEAK WARNING"`。
4. **是否需要单独的 settings UI** — 否（默认走 `Editor → Inspections` 页面即可）。

## Files to Create/Modify

### 1. Inspection 实现：`src/main/java/dev/dong4j/idea/skill/inspector/inspection/{Name}Inspection.java`

骨架照抄 `SkillMdInspection`：

```java
public class {Name}Inspection extends LocalInspectionTool {

    private final RuleRunner ruleRunner = new RuleRunner();

    @Override
    @NotNull
    public String getGroupDisplayName() {
        return SkillInspectorBundle.message("inspection.group.name");
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return SkillInspectorBundle.message("inspection.{name}.display.name");
    }

    @Override
    @NotNull
    public String getShortName() {
        return "{Name}Inspection";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nullable
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file,
                                         @NotNull InspectionManager manager,
                                         boolean isOnTheFly) {
        if (!SkillInspectorSettings.getInstance().isSkillInspectionEnabled() || !is{Target}File(file)) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        // TODO: 构造领域模型 + 跑规则 + 转换为 ProblemDescriptor
        return ProblemDescriptor.EMPTY_ARRAY;
    }

    private boolean is{Target}File(@NotNull PsiFile file) {
        // TODO: 实现 detector
        return false;
    }
}
```

**关键约束**：

- **`isEnabledByDefault()` 返回 `true`**：项目策略是开箱即用，让用户在 Settings 里关，而不是要他们主动开。
- **必须先判断全局开关 `SkillInspectorSettings.isSkillInspectionEnabled()`**：保持与 `SkillMdInspection` 一致，让状态栏 widget 一键关全部。
- **detector 越精确越好**：避免 inspection 在不相关文件上空跑，影响 IDE 性能。
- **不要在 `checkFile` 里发起耗时 IO**：`LocalInspectionTool` 在编辑器输入时高频调用，重活放到 Action（参考 `SkillInspectorAction`）。

### 2. 注册：`src/main/resources/META-INF/plugin.xml`

在 `<extensions>` 块下加：

```xml
<localInspection language="Markdown"
                 groupBundle="messages.SkillInspectorBundle"
                 groupKey="inspection.group.name"
                 shortName="{Name}Inspection"
                 displayNameKey="inspection.{name}.display.name"
                 enabledByDefault="true"
                 level="WARNING"
                 implementationClass="dev.dong4j.idea.skill.inspector.inspection.{Name}Inspection"/>
```

参考已有注册：

```46:53:src/main/resources/META-INF/plugin.xml
        <!-- SKILL.md 规范检查 -->
        <localInspection language="Markdown"
                         groupBundle="messages.SkillInspectorBundle"
                         groupKey="inspection.group.name"
                         shortName="SkillMdInspection"
                         displayNameKey="inspection.skill.md.display.name"
                         enabledByDefault="true"
                         level="WARNING"
                         implementationClass="dev.dong4j.idea.skill.inspector.inspection.SkillMdInspection"/>
```

**关键约束**：

- **`shortName` 必须与 Java 类的 `getShortName()` 返回值一致**：否则 Settings 里"分组"和"启用 / 禁用"会错乱。
- **`groupKey` 复用 `inspection.group.name`**：所有 Inspection 共享一个分组，避免在 Settings 里散落。
- **`language` 必须是 IDEA 已注册的 Language ID**：Markdown 走 `org.intellij.plugins.markdown` 提供，确认 `plugin.xml` 的 `<depends>`
  已声明对应平台插件。

### 3. i18n 文案

```properties
inspection.{name}.display.name=Validate {Target} files
```

中文：

```properties
inspection.{name}.display.name=校验 {Target} 文件
```

### 4. 测试

- **fixture 测试**（推荐）：`src/test/java/.../inspection/{Name}InspectionTest.java`，继承 `BasePlatformTestCase`，调用
  `myFixture.enableInspections()`。
- **纯模型测试**：如果你把规则逻辑独立成 `XxxRules`，参考 `/add-rule` 的测试模式，无需启 IDE fixture。

### 5. 文档同步

- `docs/design.md` — 在"模块划分"小节加新 Inspection 的描述。
- `docs/rules.md` — 如果 Inspection 内含多条规则，单独开一个表格。
- `AGENTS.md` — 在"包结构"列出新 Inspection 类。

## Build & Verify

```bash
./gradlew compileJava
./gradlew runIde
# 在 IDE 实例中：Settings → Editor → Inspections → 找到 "Skill Inspector" 分组 → 看你的新 inspection 是否出现并默认启用
./gradlew verifyPlugin   # 必跑：shortName 错位会被这里发现
```

## Troubleshooting

- **`verifyPluginStructure` 报 `shortName mismatch`**：plugin.xml 的 `shortName` 与 Java 类 `getShortName()` 不一致，对齐即可。
- **Settings 找不到这条 Inspection**：检查 `<depends>` 中是否声明了对应语言插件（如 Markdown / YAML / Python）。
- **`MissingResourceException` for inspection.group.name**：bundle 里少了 key，**任何新 Inspection 都直接复用 `inspection.group.name`，不要建新分组键
  **，否则 Settings 树会出现孤儿节点。
- **Inspection 跑得太多导致输入卡顿**：detector 不够精确，先用 `file.getName()` / `file.getVirtualFile().getPath()` 做快速过滤。

## 参考代码

- 已有 Inspection：`src/main/java/dev/dong4j/idea/skill/inspector/inspection/SkillMdInspection.java`
- 文件 detector 范式：`src/main/java/dev/dong4j/idea/skill/inspector/detection/SkillFileDetector.java`
- plugin.xml 注册位置：`src/main/resources/META-INF/plugin.xml`
