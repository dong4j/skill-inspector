# 未解决 Bug 交接文档

> 本人（前任 AI）多轮尝试均失败，交接给下一位接手者。
>
> 创建日期：2026-05-25
>
> 受影响版本：`pluginVersion=2026.1.1000`，IntelliJ Platform 2024.2
>
> 包含两个独立但调查时常混淆的问题：
> - **Bug 1**：Problems View 里所有 SkillInspector 警告精确重复 2 次
> - **Bug 2**：Problems View 里所有 problem 都显示成同一个黄色 ⚠️ 图标，无视严重度

---

# Bug 1: Problems View 里所有 SKILL.md 警告精确重复 2 次

## 1.1 现象

打开任意 `SKILL.md`，Problems View 里所有 Skill Inspector 给出的警告 / 错误**都精确重复 2 次**：
ruleId、行号、文案完全相同，截图示例：

```
⚠ Resource file references/output-patterns.md is not referenced from SKILL.md :1
⚠ Resource file references/output-patterns.md is not referenced from SKILL.md :1
⚠ Resource file references/workflows.md is not referenced from SKILL.md :1
⚠ Resource file references/workflows.md is not referenced from SKILL.md :1
⚠ Script scripts/quick_validate.py is not mentioned in SKILL.md body :1
⚠ Script scripts/quick_validate.py is not mentioned in SKILL.md body :1
⚠ Description should explain when to use this skill :3
⚠ Description should explain when to use this skill :3
⚠ SKILL.md is longer than 12,000 characters; consider moving details to references/ :6
⚠ SKILL.md is longer than 12,000 characters; consider moving details to references/ :6
⚠ SKILL.md must start with YAML frontmatter :131
⚠ SKILL.md must start with YAML frontmatter :131
...
```

每一条都是 `(N, N)` 的 pair，不会出现 3 倍或其他比例。

而同一个文件通过编辑器右下角浮动按钮触发的 `ValidateCurrentSkillFileAction` 通知里，
ruleId 列表（去重后）**数量正确**，没有重复。

## 1.2 关键观察 / 已知事实

| 事实                                                | 含义                                                  |
|---------------------------------------------------|-----------------------------------------------------|
| 按钮通知数量正确（去 dup 后）                                 | `RuleRunner.run()` 输出本身是唯一的                         |
| Problems View 数量 = 真实数量 × 2                       | IDE 在把 `ProblemDescriptor` 写入 Problems View 时被触发了两次 |
| 重复 ratio 恒为 2，不是 3 或 N                            | 不是不停累积，是稳定的双倍调度                                     |
| 按钮 Action 跑出来的 ruleId 集合 = Problems View 去重后剩下的集合 | 两边走的规则 / 解析逻辑确实一致，差异不在规则层                           |
| 重复出现在所有 SkillInspector 规则上                        | 不是某条规则代码 bug，是 inspection 调度层 / 写入层问题               |

**结论**：根因在 IDE 怎么调度 `SkillMdInspection` 或怎么往 Problems View 写 `ProblemDescriptor`，
不在 `RuleRunner` / `SkillModelBuilder` / 规则代码本身。

## 1.3 已尝试的修复（全部失败）

按时间顺序，每条都列出**假设**和**为什么没解决**。

### 1.3.1 commitDocument + `PsiDocumentManager.getPsiFile(document)`

**文件**：`ValidateCurrentSkillFileAction.java`
**假设**：Action 拿到的 PSI 跟 daemon 看到的不同步，两边看到不同 SkillFile，导致 problems 不一致。
**做法**：进 BGT 前先 `commitDocument`，BGT 内用 `getPsiFile(document)` 而非 `findFile(vf)`。
**结果**：Action 通知里 ruleId 正确了；Problems View **依旧每条 ×2**。
**结论**：PSI 同步不是根因。

### 1.3.2 `FileContentUtil.reparseFiles` + `DaemonCodeAnalyzer.restart`

**文件**：`ValidateCurrentSkillFileAction.publishResult`
**假设**：Markdown PSI 懒解析导致 daemon 早期跑时拿到的 PSI 不完整，
强制 reparse + restart 让 daemon 用最新 PSI 重跑，Problems View 自动同步。
**结果**：Problems View 依旧每条 ×2。**而且这个调用甚至可能本身就是触发第二次写入的元凶**（见 §1.4 第 2 条怀疑方向）。
**结论**：没有触及根因，怀疑反而是负面。

### 1.3.3 `FrontMatterParser` 加纯文本 fallback

**文件**：`FrontMatterParser.java`
**假设**：Markdown plugin 在某些时机 / 某些版本下不把 `---\n...\n---` 识别为 `FRONT_MATTER` PSI 节点，
导致 `findFrontMatterElement` 返回 null，规则层把"明明有 frontmatter"的文件当成"没 frontmatter"报错。
**做法**：PSI 找不到 frontmatter 节点时，回退到纯文本扫描 `---` 分隔符。
**结果**：让 Action 和 daemon 看到一致的 `SkillFile` 模型，是个**有价值的副作用修复**（之前确实有 Problems View 错报 `frontmatter.missing`
但文件实际有 frontmatter 的情况），
**但 Problems View 还是每条 ×2**。
**结论**：解决了一个独立 bug（PSI 识别不稳定），但跟"翻倍"无关。

### 1.3.4 `RuleRunner.run()` 内部按 `ruleId + range` 去重

**文件**：`RuleRunner.java`
**假设**：规则间 / 规则内意外产生了相同 problem。
**做法**：`run()` 返回前按 `ruleId + range.startOffset + range.endOffset` 去重。
**结果**：单次 `run()` 输出确实唯一（按钮通知就是证明），**Problems View 还是 ×2**。
**结论**：单次 `RuleRunner.run()` 输出本来就唯一，去重是无效防御。

### 1.3.5 `SkillMdInspection.checkFile` 输出前再按 `ruleId + offsets` 去重

**文件**：`SkillMdInspection.java`
**假设**：IDE 可能在单次 `checkFile` 调用里因为某种 visitor 触发让 RuleRunner 跑出重复。
**做法**：把 `RuleRunner` 输出转 `ProblemDescriptor` 时再去一次重。
**结果**：每次 `checkFile` 内部的 `seen` Set 都是 new 的，跨调用根本不共享，**Problems View 还是 ×2**。
**结论**：完全无效——根因是 `checkFile` 本身被调多次，单次内去重解决不了。

### 1.3.6 `runForWholeFile() = true`

**文件**：`SkillMdInspection.java`
**假设**：`LocalInspectionTool` 默认 `runForWholeFile()` 返回 false 时，
IDE 同时调度 visitor 路径和 `checkFile` 路径，两条路径都触发了一次写入。
返回 true 应该让 IDE 只走 `checkFile`。
**做法**：

```java
@Override
public boolean runForWholeFile() {
    return true;
}
```

**结果**：**Problems View 还是 ×2**。
**结论**：要么这个假设错了（`runForWholeFile=true` 并不能阻止双倍调度），
要么根因压根不在 visitor vs checkFile 的选择上。

## 1.4 怀疑的根因方向（按可能性排序）

### 1.4.1 IDE 真的对同一 PsiFile 调了 `checkFile` 两次（最可能）

**怎么验证**：在 `SkillMdInspection.checkFile` **入口处加一行 `LOG.warn`**（不是 debug，直接 warn 强制输出到 IDE log），打印：

- `file.getName()` + `file.getVirtualFile().getPath()`
- `isOnTheFly`
- `Thread.currentThread().getName()`
- `System.identityHashCode(file)`（PsiFile 实例 hash）
- `new Throwable().fillInStackTrace()` 的 stack（看是谁触发的）

让用户跑一次 IDE 打开 SKILL.md，然后把 `idea.log` 里 `SkillMdInspection.checkFile` 的所有行贴出来。

- 如果只出现 1 次，且 ProblemDescriptor 数组长度 = 实际 problem 数，那双倍是更后面（写入 / 渲染层）的问题
- 如果出现 2 次：对比两次的 stack trace，能直接定位是哪个 IDE 调度路径在重复触发

### 1.4.2 `FileContentUtil.reparseFiles` + `DaemonCodeAnalyzer.restart` 自己制造了第二次调度

**理由**：这两个调用是上一任在尝试修复"Problems View 看不到最新结果"时加的（§1.3.2）。
它们在 `publishResult` 里被每次按钮点击触发，可能导致：

1. 第一次 daemon 跑：写入 N 个 descriptor
2. reparseFiles 触发 PSI 重建 → daemon 重跑：又写入 N 个 descriptor
3. IDE 没清前一次 → 看到 2N 个

**怎么验证**：临时注释掉 `ValidateCurrentSkillFileAction.publishResult` 里这两行：

```java
FileContentUtil.reparseFiles(project, Collections.singletonList(vf), true);
if (result.psiFile != null && result.psiFile.isValid()) {
    DaemonCodeAnalyzer.getInstance(project).restart(result.psiFile);
}
```

然后关掉所有 SKILL.md tab，重新打开，**不要点按钮**，直接看 Problems View。

- 如果数量正确（不再 ×2），那确认是 §1.3.2 的副作用，删掉这两行即可
- 如果还是 ×2，证伪这个方向

### 1.4.3 Markdown plugin 的 frontmatter / split-editor 让同一 inspection 被两个 PsiFile 路径触发

**理由**：Markdown 文件在 IDE 内是 `TextEditorWithPreview`，PSI 可能有 main + injected (YAML frontmatter as YAMLLanguage) 双重身份。
如果 `language="Markdown"` 的注册被 IDE 同时匹配到 `MarkdownLanguage` 和某种 baseLanguage，可能会双跑。

**怎么验证**：

1. `plugin.xml` 把 `language="Markdown"` 临时换成 `language="any"`，或加 `applyToDialects="false"`，看是否变化
2. 在 `checkFile` 入口打印 `file.getLanguage().getID()` 和 `file.getViewProvider().getAllFiles()`，看是不是有多个 PsiFile 实例

### 1.4.4 用 `buildVisitor` 完全替换 `checkFile`

**理由**：彻底绕开 `checkFile` 路径，避免任何 visitor / whole-file 的调度交叉问题。

**做法**：把 `SkillMdInspection` 改成

```java
@Override
public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
        @Override
        public void visitFile(@NotNull PsiFile file) {
            if (!SkillFileDetector.isSkillFile(file)) return;
            SkillFile skillFile = SkillModelBuilder.build(file);
            for (SkillProblem problem : ruleRunner.run(skillFile)) {
                holder.registerProblem(file,
                    TextRangeUtil.clamp(problem.range(), file.getTextLength()),
                    problem.message(),
                    toQuickFixes(problem.fixTypes()));
            }
        }
    };
}
```

并且**移除 `runForWholeFile()` 重写**、**移除 `checkFile` 重写**。
让 `LocalInspectionsPass` 完全按 visitor 路径调度，且 visitor 只在 `visitFile` 触发一次。

**注意**：如果这种方式跑出来还是 ×2，那就**完全确认**根因在 IDE 调度器外层（多个 InspectionToolWrapper 实例 / 多 profile 双跑），跟 `checkFile`
vs visitor 的选择无关。

### 1.4.5 检查 `inspection profile` 是否被注册了两次

**怎么验证**：在用户的 IDE 里：

1. `Settings → Editor → Inspections`
2. 搜索 "Skill"
3. 看是不是同一个 inspection 出现在两个不同的节点 / 两次

如果出现两次，那就是某种配置层注册重复（比如 `plugin.xml` 注册一次 + project profile xml 又注册一次）。

### 1.4.6 Markdown 是否有自定义 inspection wrapper

**理由**：IntelliJ Platform 的 Markdown plugin 可能会包裹（wrap）所有 `language="Markdown"` 的 inspection 跑一次它自己的预览刷新逻辑。

**怎么查**：grep IntelliJ Platform 源码（或 Markdown plugin 源码）里 `LocalInspectionToolWrapper`、`MarkdownInspection` 的相关实现，看是否对子
inspection 有 wrap 行为。

## 1.5 验证标准（Bug 1）

下一位的修复要满足：

- [ ] 打开任意 SKILL.md，Problems View 里 SkillInspector 警告**不再每条 ×2**
- [ ] Problems View 显示的 ruleId 集合 = 按钮通知 `[...]` 里的 ruleId 集合
- [ ] 项目级 `SkillInspectorAction`（右键 Validate Skill）的扫描结果与逐文件 Problems View 数量一致
- [ ] 全部现有测试通过（`./gradlew test`）

---

# Bug 2: Problems View 里所有 problem 都显示成同一个黄色 ⚠️ 图标，无视严重度

## 2.1 现象

SKILL.md 的 problems 在 Problems View 里**全部显示成黄色 ⚠️ 警告图标**，
无论其在 `SkillSeverity` 里被标记为 `ERROR` / `WARNING` / `WEAK_WARNING`。

举例（用户截图实际看到的）：

| 规则 ID                              | 代码里的 `SkillSeverity` | Problems View 实际图标 | 应该的图标     |
|------------------------------------|----------------------|--------------------|-----------|
| `frontmatter.missing`              | `ERROR`              | 🟡 黄色 ⚠️           | 🔴 红色 ❌   |
| `frontmatter.description.too-long` | `ERROR`              | 🟡 黄色 ⚠️           | 🔴 红色 ❌   |
| `body.too-long`                    | `WARNING`            | 🟡 黄色 ⚠️           | 🟡 黄色 ⚠️  |
| `body.missing-title`               | `WEAK_WARNING`       | 🟡 黄色 ⚠️           | 🔘 较浅的 ⚠️ |

后果：用户在 Problems View 里**无法一眼区分严重度**，错误 / 警告 / 弱警告全是同一个图标。
（前任在 IDE log 里观察到不同强度的描边 / 高亮，但 Problems View 列表里没体现。）

## 2.2 前任尝试过的修复（理解偏了）

前任只改了**通知**的图标分发：

- `ValidateCurrentSkillFileAction.publishResult`：根据 errors/warnings 计数选 `showError` / `showWarning` / `showInfo`
- `SkillInspectorAction.actionPerformed`：同样按汇总严重度选三档 `NotificationType`

效果：弹通知（NotificationGroupManager 的气泡）现在能正确显示 🔴 / 🟡 / 🔵 三档。

**但 Problems View 列表里每条 problem 的图标完全没动**——用户的诉求其实是 Problems View 的列表图标，前任理解错了方向。

## 2.3 根因分析

定位在 `SkillMdInspection.toHighlightType`：

```java
private ProblemHighlightType toHighlightType(@NotNull SkillSeverity severity) {
    return switch (severity) {
        case ERROR -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        case WARNING -> ProblemHighlightType.WARNING;
        case WEAK_WARNING -> ProblemHighlightType.WEAK_WARNING;
    };
}
```

`ProblemHighlightType.GENERIC_ERROR_OR_WARNING` 的语义：
**由 inspection profile 注册时的 `level` 决定最终图标**。

而 `plugin.xml` 里 `<localInspection>` 注册时写死了 `level="WARNING"`：

```xml
<localInspection language="Markdown"
                 groupBundle="messages.SkillInspectorBundle"
                 groupKey="inspection.group.name"
                 shortName="SkillMdInspection"
                 displayNameKey="inspection.skill.md.display.name"
                 enabledByDefault="true"
                 level="WARNING"
                 implementationClass="dev.dong4j.idea.skill.inspector.inspection.SkillMdInspection"/>
```

所以即便 `SkillProblem.severity = ERROR`，IDE 看到 `GENERIC_ERROR_OR_WARNING` + profile `level="WARNING"`，
就把所有 problem 一律按 WARNING 渲染成黄色 ⚠️。

**根因总结**：

- `level="WARNING"` 是 inspection-level 的默认严重度，不是 problem-level
- `GENERIC_ERROR_OR_WARNING` 永远跟着 inspection-level 走，不会真的根据 problem-level 的语义渲染成 ERROR

## 2.4 严重度划分约定（代码现状）

下一位修复时要遵循的"严重度 ↔ 规则"映射，全部从规则源码梳理而来：

### 2.4.1 SkillSeverity 三档定义

```java
public enum SkillSeverity {
    ERROR,         // 阻塞规范的硬错误，必须修
    WARNING,       // 强烈建议修，影响 Agent 使用 skill 的效果
    WEAK_WARNING   // 弱提示，不影响功能，仅作风格 / 优化建议
}
```

### 2.4.2 ERROR 级别（应显示为 🔴 红色 ❌）

来自 `StructuralRules`：

- `frontmatter.missing`
- `frontmatter.invalid-yaml`
- `frontmatter.name.missing`
- `frontmatter.name.too-long`
- `frontmatter.name.invalid`
- `frontmatter.name.mismatch`
- `frontmatter.description.missing`
- `frontmatter.description.too-long`
- `frontmatter.compatibility.empty`
- `frontmatter.compatibility.too-long`
- `skill.directory.name`

来自 `SecurityRules`（部分）：

- `security.dangerous-command`

### 2.4.3 WARNING 级别（应显示为 🟡 黄色 ⚠️）

来自 `QualityRules`：

- `description.too-short`
- `description.too-generic`
- `description.missing-usage`
- `body.missing-trigger`
- `body.too-long`

来自 `ReferenceRules`：

- `reference.missing-file`
- `reference.case-mismatch`

来自 `ResourceRules`：

- `resource.unused-reference`
- `script.missing-usage`

来自 `SecurityRules`（部分）：

- 含敏感信息提示（具体 ruleId 请查代码）

### 2.4.4 WEAK_WARNING 级别（应显示为 🔘 较浅的 ⚠️）

来自 `QualityRules`：

- `body.missing-title`

> **完整且权威的清单**：在 `docs/rules.md`。下一位修复前请以那份为准并对齐本节。

## 2.5 修复方向

### 2.5.1 方向 A：`toHighlightType` 改用强制 highlight type（推荐，最小改动）

```java
private ProblemHighlightType toHighlightType(@NotNull SkillSeverity severity) {
    return switch (severity) {
        case ERROR -> ProblemHighlightType.ERROR;          // 强制 ERROR
        case WARNING -> ProblemHighlightType.WARNING;      // 显式 WARNING
        case WEAK_WARNING -> ProblemHighlightType.WEAK_WARNING;
    };
}
```

⚠️ 注意：

- `ProblemHighlightType.ERROR` 在某些版本被标记 deprecated，需要确认 IntelliJ 2024.2 是否仍支持
- 如果 deprecated，替代是 `HighlightSeverity.ERROR` 配合 `holder.registerProblem(..., HighlightSeverity, ...)`，
  但这只在 `buildVisitor` 路径下能用（见方向 C）

### 2.5.2 方向 B：移除 `level="WARNING"` 限制 + 用 `GENERIC_ERROR_OR_WARNING`

`plugin.xml` 里去掉 `level` 属性（或改成 `level="INFO"`），让 IDE 不限制 inspection 默认 level。
但 `GENERIC_ERROR_OR_WARNING` 的实际行为仍依赖 profile，效果可能不稳定。**不推荐**。

### 2.5.3 方向 C：改用 `buildVisitor` + `holder.registerProblem(..., HighlightSeverity)`

如果 Bug 1 也走 `buildVisitor` 路线（§1.4.4），就一起做：

```java
@Override
public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
        @Override
        public void visitFile(@NotNull PsiFile file) {
            if (!SkillFileDetector.isSkillFile(file)) return;
            SkillFile skillFile = SkillModelBuilder.build(file);
            for (SkillProblem problem : ruleRunner.run(skillFile)) {
                holder.registerProblem(
                    holder.getManager().createProblemDescriptor(
                        file,
                        TextRangeUtil.clamp(problem.range(), file.getTextLength()),
                        problem.message(),
                        toHighlightType(problem.severity()),  // 直接传 ERROR / WARNING / WEAK_WARNING
                        isOnTheFly,
                        toQuickFixes(problem.fixTypes())
                    )
                );
            }
        }
    };
}
```

这种方式 problem-level severity 通过 `ProblemHighlightType` 直接传给 IDE，
**不受 inspection-level `<localInspection level="...">` 限制**。

### 2.5.4 方向 D：注册多个 Inspection，每个对应一个 severity

把 `SkillMdInspection` 拆成 `SkillMdErrorInspection` / `SkillMdWarningInspection` / `SkillMdWeakWarningInspection`，
`plugin.xml` 里分别注册三个 `<localInspection>`，每个用不同的 `level`。

代价：要按 severity 把规则分到三个 Inspection 类里，复杂度高、维护成本大、还可能让 Bug 1 的双倍问题变成三倍。**不推荐**。

### 2.5.5 方向 E：注册自定义 `Severity`

用 `com.intellij.lang.annotation.HighlightSeverity` + `SeverityRegistrar` 注册插件自己的严重度等级。
适合需要在标准 ERROR / WARNING / WEAK_WARNING 之外有更细分级（比如 spec-violation vs style-suggestion）的场景。
**当前项目用不上**。

**最终推荐**：先试方向 A，如果 deprecated 警告或在 2024.2 行为不对，再走方向 C（跟 Bug 1 的 `buildVisitor` 修复一起做）。

## 2.6 验证标准（Bug 2）

下一位的修复要满足：

- [ ] Problems View 里 `frontmatter.missing`、`frontmatter.description.too-long` 等 ERROR 级别问题显示 **🔴 红色 ❌ 错误图标**
- [ ] `body.too-long`、`description.missing-usage` 等 WARNING 级别问题显示 **🟡 黄色 ⚠️ 警告图标**
- [ ] `body.missing-title` 等 WEAK_WARNING 级别问题显示 **🔘 较浅的 ⚠️ 弱警告图标**
- [ ] 按钮通知图标仍按汇总严重度三档（已修，不要回归）
- [ ] 编辑器内的高亮（红波浪 / 黄波浪 / 灰底线）也对齐严重度（IntelliJ 默认会根据 highlight type 自动渲染）
- [ ] 全部现有测试通过（`./gradlew test`）

---

# 通用：相关文件清单

调查时**不需要重新读全部**，按需打开：

| 文件                                                                                         | 关注点                                                                  |
|--------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| `src/main/java/dev/dong4j/idea/skill/inspector/inspection/SkillMdInspection.java`          | inspection 入口，含 `checkFile`、`toHighlightType`、`runForWholeFile`      |
| `src/main/java/dev/dong4j/idea/skill/inspector/action/ValidateCurrentSkillFileAction.java` | 按钮 Action，含 reparseFiles + DaemonCodeAnalyzer.restart（怀疑负面，见 §1.4.2） |
| `src/main/java/dev/dong4j/idea/skill/inspector/action/SkillInspectorAction.java`           | 项目级扫描 Action                                                         |
| `src/main/java/dev/dong4j/idea/skill/inspector/rules/RuleRunner.java`                      | 已加 dedup                                                             |
| `src/main/java/dev/dong4j/idea/skill/inspector/rules/*Rules.java`                          | 5 个规则类，按 §2.4 的严重度约定                                                 |
| `src/main/java/dev/dong4j/idea/skill/inspector/parser/FrontMatterParser.java`              | 已加纯文本 fallback                                                       |
| `src/main/java/dev/dong4j/idea/skill/inspector/util/NotificationUtil.java`                 | 通知三档 API                                                             |
| `src/main/resources/META-INF/plugin.xml`                                                   | `<localInspection level="WARNING">` —— Bug 2 根因之一                    |
| `src/test/java/dev/dong4j/idea/skill/inspector/parser/FrontMatterParserTest.java`          | 含 3 个 fallback 回归测试                                                  |
| `docs/rules.md`                                                                            | 完整规则清单 + 严重度（权威）                                                     |

---

# 给下一位接手者的总建议

1. **先看 Bug 1 §1.4.1**：加 LOG.warn 输出 `checkFile` 调用栈，是定位 Bug 1 唯一靠谱的第一步。前任没做这一步，靠猜浪费了 6 轮。
2. **Bug 1 §1.4.2 一起做**：临时禁用 `reparseFiles` + `DaemonCodeAnalyzer.restart`，强怀疑是元凶。
3. **Bug 2 优先 §2.5.1**（方向 A）：单行改动就能验证。如果不行再走 §2.5.3（与 Bug 1 共用 `buildVisitor` 重写）。
4. **不要再改的方向**：规则代码 / parser / Action 里的 dedup 路径。前任已经在这些层加了多重防御，全部无效。
5. **遵循 AGENTS.md**：本项目零外部依赖、Java-only、specification-first。任何修复别引新依赖。
