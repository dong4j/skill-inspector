---
name: add-quickfix
description: 为已有规则添加 Quick Fix，遵守 Skill Inspector "保守修复"原则
---

# /add-quickfix — 添加 Quick Fix

为某条 `SkillProblem` 添加可一键执行的修复。**核心原则**：Quick Fix 只做"确定性"修复（创建文件、补字段、规范化命名……），**不替用户改写正文**或猜业务语义。

## Usage

```
/add-quickfix [FIX_TYPE]
```

- `FIX_TYPE` — `SkillFixType` 枚举的新成员，UPPER_SNAKE_CASE，例如 `ADD_TAGS_FIELD`、`REMOVE_TRAILING_DASH`。

## Information Needed

1. **Fix Type 名称** — 与 `SkillFixType` 枚举命名风格一致。
2. **修复语义** — 一句话描述"做了什么"，落到 i18n key `quickfix.{xxx}`。
3. **是否可纯文本表达** — 如果可以（如格式化字符串），把核心算法放进 `SkillQuickFixTexts` 便于单测。
4. **是否需要 PsiFile 之外的上下文** — 例如 `CREATE_MISSING_REFERENCE` 需要 `ProblemDescriptor.getTextRangeInElement()` 才能拿到链接
   destination。
5. **关联规则** — 哪条 `SkillProblem.ruleId` 在 `fixTypes` 列表里挂这个 fix。

## Files to Create/Modify

### 1. 枚举：`src/main/java/dev/dong4j/idea/skill/inspector/model/SkillFixType.java`

追加一个值，写明"为什么存在"和"修复多保守"：

```java
/**
 * 在 frontmatter 中追加 tags 字段
 * <p> 仅插入空数组占位 (`tags: []`), 不猜测合适标签, 由作者后续补充.
 */
ADD_TAGS_FIELD,
```

### 2. 修复实现：`src/main/java/dev/dong4j/idea/skill/inspector/quickfix/SkillQuickFix.java`

#### 2.1 在 `getFamilyName()` switch 里加分支

```52:63:src/main/java/dev/dong4j/idea/skill/inspector/quickfix/SkillQuickFix.java
    @Override
    @NotNull
    public String getFamilyName() {
        return switch (fixType) {
            case ADD_FRONT_MATTER -> SkillInspectorBundle.message("quickfix.frontmatter.add");
            case ADD_NAME -> SkillInspectorBundle.message("quickfix.name.add");
            case ADD_DESCRIPTION -> SkillInspectorBundle.message("quickfix.description.add");
            case SYNC_NAME_WITH_DIRECTORY -> SkillInspectorBundle.message("quickfix.name.sync");
            case CONVERT_NAME_TO_KEBAB -> SkillInspectorBundle.message("quickfix.name.kebab");
            case CREATE_MISSING_REFERENCE -> SkillInspectorBundle.message("quickfix.reference.create.file");
        };
    }
```

#### 2.2 在 `applyFix()` switch 里加分支

```java
case ADD_TAGS_FIELD -> addField(skillFile, document, "tags", "[]");
```

如果你的逻辑复杂（比如 `createMissingReference`），新建一个私有方法，并保持以下结构：

```java
private void doFix(@NotNull SkillFile skillFile, @NotNull Document document) {
    // 1. 通过 SkillFile 取出位置 / 字段
    SkillFrontMatter frontMatter = skillFile.frontMatter();
    if (frontMatter == null) {
        return;        // 防御：上游 inspection 应保证 frontmatter 存在，但保守起见直接 return
    }
    // 2. 计算插入 / 替换 offset
    int offset = ...;
    // 3. 调用 SkillQuickFixTexts 中的纯函数生成文本（保证可单测）
    String text = SkillQuickFixTexts.someTemplate(args);
    // 4. 用 Document API 写入
    document.replaceString(offset, offset, text);
}
```

**关键约束**：

- **整个 `applyFix()` 已经被 `WriteCommandAction` 包住**，不要再嵌一层。
- **写完后必须 `PsiDocumentManager.getInstance(project).commitDocument(document)`**，否则后续 PSI 树可能拿不到刚写入的文本（已在主入口完成，私有方法不要再
  commit 一次）。
- **任何文件 IO 必须走 `VirtualFile` API**，不要用 `java.nio.Files`，否则 IDE 文件系统视图不刷新。参考 `SkillQuickFix.createInVfs()`。
- **路径越界要拦**：写出的目标必须 `path.startsWith(skillDirectoryPath)`，避免 fix 把文件创建到 skill 目录外。

### 3. 纯文本工具（推荐）：`src/main/java/dev/dong4j/idea/skill/inspector/quickfix/SkillQuickFixTexts.java`

把"生成什么字符串"的逻辑挪到这里，配套写单测。例如：

```java
@NotNull
public static String tagsFieldInsertion(boolean hasExistingContent) {
    return (hasExistingContent ? "\n" : "") + "tags: []";
}
```

对应单测在 `src/test/java/.../quickfix/SkillQuickFixTextsTest.java`。

### 4. 让某条规则启用此 fix

在对应 `*Rules` 类的 `SkillProblem` 构造里把它加进 `fixTypes`：

```java
problems.add(new SkillProblem(
    "frontmatter.tags.missing",
    SkillSeverity.WARNING,
    SkillInspectorBundle.message("inspection.frontmatter.tags.missing"),
    range,
    List.of(SkillFixType.ADD_TAGS_FIELD)   // <-- 这里
));
```

> Inspection 适配层 (`SkillMdInspection.toQuickFixes()`) 会自动把 `fixTypes` 列表展开成 `LocalQuickFix[]`，不需要手动注册 fix。

### 5. i18n 文案

`SkillInspectorBundle.properties` 与中文版同步加：

```properties
quickfix.tags.add=Add tags field
```

中文文件：

```properties
quickfix.tags.add=添加 tags 字段
```

### 6. 文档同步：`docs/rules.md`

在规则表格的 "Quick Fix" 列把对应规则的 fix 名称写上。

### 7. 测试

- **纯文本逻辑** → `SkillQuickFixTextsTest`（单元测试，无 IDE）。
- **真实 IDE 写操作** → 在 `src/test/java/.../quickfix/` 下补 fixture 测试（基于 `BasePlatformTestCase`），可选；项目当前 V1 未强制覆盖，但涉及
  VFS 时建议加。

## Build & Verify

```bash
./gradlew compileJava
./gradlew test --tests "*SkillQuickFixTextsTest*"
./gradlew runIde     # 在 SKILL.md 上 Alt+Enter 验证
```

## Troubleshooting

- **Fix 在 Alt+Enter 菜单里没出现**：确认规则 `SkillProblem.fixTypes` 包含了新枚举；确认 `SkillQuickFix.applyFix` switch 里覆盖了它（**未覆盖会编译失败
  **，因为 switch 是 exhaustive 的，这是项目刻意的设计：避免漏分支）。
- **写入后 IDE 仍说"文件不存在"**：确认走了 VFS API（`VirtualFile.createChildData`），不是 `Files.createFile`。
- **多次执行 fix 出现重复字段**：在 fix 里先判断字段是否已存在，例如 `if (metadata.tags() != null) return;`。
- **`getFamilyName` 报 `pattern not exhaustive`**：Java switch 表达式要求所有枚举值都要覆盖，按提示补一个 case 即可。

## 参考代码

- 枚举：`src/main/java/dev/dong4j/idea/skill/inspector/model/SkillFixType.java`
- 主入口：`src/main/java/dev/dong4j/idea/skill/inspector/quickfix/SkillQuickFix.java`
- 纯文本工具：`src/main/java/dev/dong4j/idea/skill/inspector/quickfix/SkillQuickFixTexts.java`
- Inspection 适配：`src/main/java/dev/dong4j/idea/skill/inspector/inspection/SkillMdInspection.java`
