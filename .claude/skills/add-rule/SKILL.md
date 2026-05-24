---
name: add-rule
description: 在 Skill Inspector 中新增一条 SKILL.md 检查规则，统一走"单 Inspection + 多规则"路径
---

# /add-rule — 新增检查规则

在 Skill Inspector 中新增一条 SKILL.md 检查规则。**默认走"单 Inspection + 多规则"路径**，不要为每条规则新建一个 `LocalInspectionTool`（那是
`/add-inspection` 的场景）。

## Usage

```
/add-rule [ruleId]
```

- `ruleId` — 形如 `{category}.{specific}`，例如 `frontmatter.tags.invalid`、`description.too-generic`、`reference.missing-file`。

## Information Needed

1. **规则 ID** — `{category}.{specific}` 格式，必须与代码中 `SkillProblem.ruleId` 严格一致。
2. **分类** — 落到哪一个 `*Rules` 类：
    - `StructuralRules` — 结构（frontmatter 必填、命名、长度）。Severity: Error。
    - `QualityRules` — 描述质量、正文结构。Severity: Warning / Weak Warning。
    - `ReferenceRules` — 相对链接、资源引用。Severity: Warning。
    - `ResourceRules` — `references/` `scripts/` 反向引用。Severity: Warning。
    - `SecurityRules` — 危险命令、敏感信息。Severity: Error / Warning。
3. **严重度** — `SkillSeverity.ERROR` / `WARNING` / `WEAK_WARNING`。
4. **是否带 Quick Fix** — 如果带，先看 `SkillFixType` 是否已有合适枚举；没有则先走 `/add-quickfix`。
5. **触发的 TextRange** — 落在 frontmatter / 字段值 / 全文哪一段。

## Decision Tree — 加在哪里？

| 场景                        | 落点                                                         |
|---------------------------|------------------------------------------------------------|
| 已有 `*Rules` 类语义吻合（90% 情况） | 在该类中追加私有方法，主入口 `check()` 里调用                               |
| 一组规则共享一段解析（如反向资源扫描）       | 新建一个 `XxxRules implements SkillRule`，并加进 `RuleRunner` 默认列表 |
| 规则跑在 `SKILL.md` 之外的文件上    | 走 `/add-inspection`（独立 LocalInspectionTool）                |

## Files to Create/Modify

### 1. 规则实现：`src/main/java/dev/dong4j/idea/skill/inspector/rules/{Category}Rules.java`

参考 `StructuralRules.checkName()` 的写法：

```java
private void check{RuleName}(@NotNull List<SkillProblem> problems,
                              int textLength,
                              @NotNull SkillFile skillFile,
                              @NotNull SkillMetadata metadata) {
    // 1. 取出待校验字段 / 位置
    SkillYamlEntry entry = metadata.{field}();
    if (entry == null) {
        return;
    }

    // 2. 业务判断
    if (!isValid(entry.value())) {
        TextRange range = TextRangeUtil.clamp(entry.valueRange(), textLength);
        problems.add(new SkillProblem(
            "{category}.{specific}",                                        // ruleId, 与 docs/rules.md 一致
            SkillSeverity.{ERROR|WARNING|WEAK_WARNING},
            SkillInspectorBundle.message("inspection.{bundle.key}", arg),   // 用户可见文案，必须走 i18n
            range,                                                          // TextRange，必须 clamp 到文件长度内
            List.of(SkillFixType.{FIX_TYPE})                                // 没有修复就传 List.of()
        ));
    }
}
```

**关键约束**：

- **TextRange 必须经过 `TextRangeUtil.clamp()`**：否则在文件被快速改动时可能越界，IntelliJ Inspection API 会抛异常。
- **如果 frontmatter 为 null 还要校验**：使用 `TextRangeUtil.fileStart(textLength)` 拿一个安全的文件首字符范围。
- **`SkillRule.check()` 必须返回 `List<SkillProblem>`，不能返回 `null`**：空列表代表"无问题"。

如果是新建一个 `*Rules` 类，骨架照抄：

```java
public class {Category}Rules implements SkillRule {
    @Override
    @NotNull
    public List<SkillProblem> check(@NotNull SkillFile skillFile) {
        List<SkillProblem> problems = new ArrayList<>();
        int textLength = skillFile.psiFile().getTextLength();
        // 在这里调用一个个私有 check{XXX}()
        return problems;
    }
}
```

### 2. 注册新规则类（仅当新建了 `*Rules` 类）

`src/main/java/dev/dong4j/idea/skill/inspector/rules/RuleRunner.java`，按"结构 → 质量 → 引用 → 资源 → 安全"的顺序补：

```22:38:src/main/java/dev/dong4j/idea/skill/inspector/rules/RuleRunner.java
public class RuleRunner {

    /** V1 默认规则集合 */
    private final List<SkillRule> rules;

    /**
     * 创建默认规则执行器
     */
    public RuleRunner() {
        this(List.of(
            new StructuralRules(),
            new QualityRules(),
            new ReferenceRules(),
            new ResourceRules(),
            new SecurityRules()
        ));
    }
```

### 3. i18n 文案（必走）

在 `SkillInspectorBundle.properties` 与 `SkillInspectorBundle_zh_CN.properties` 中**成对**新增 `inspection.{bundle.key}`，详见
`/add-i18n-key`。

### 4. 文档同步：`docs/rules.md`

在对应分层表格里追加一行：

```markdown
| `{category}.{specific}` | Error/Warning | 规则说明 | `{FIX_TYPE}` 或 — |
```

### 5. 单元测试：`src/test/java/.../rules/{Category}RulesTest.java`

参考 `StructuralRulesTest`：

```java
class {Category}RulesTest {

    private final {Category}Rules rules = new {Category}Rules();

    @Test
    void shouldReport{RuleName}() {
        String text = """
            ---
            name: my-skill
            description: Use this skill when validating skill files.
            ---
            # Skill
            """;

        List<SkillProblem> problems = rules.check(skillFile(text, "my-skill"));

        assertThat(problems)
            .extracting(SkillProblem::ruleId)
            .contains("{category}.{specific}");
    }
}
```

`skillFile(text, dirName)` 来自 `SkillFileTestFactory`，**普通规则单测不要起 IntelliJ fixture**（开销大），它会构造一个 mock PsiFile + 自带的
frontmatter 解析。

## Severity 选择指南

| 触发条件                           | 推荐严重度          |
|--------------------------------|----------------|
| 违反此条 skill 在 Agent 侧无法被识别 / 加载 | `ERROR`        |
| 违反此条会显著降低 Agent 选用准确性          | `WARNING`      |
| 仅为格式建议，作者忽略也无功能影响              | `WEAK_WARNING` |

## Build & Verify

```bash
./gradlew compileJava
./gradlew test --tests "*{Category}RulesTest*"
./gradlew runIde      # 打开 SKILL.md 验证 Problems 面板
```

## Troubleshooting

- **TextRange 越界异常**：检查是否漏掉 `TextRangeUtil.clamp()`，或在 frontmatter 为 null 时直接用了字段范围。
- **i18n 报 `MissingResourceException`**：检查 properties key 是否两个 bundle 文件都加了；中文文件需要 Unicode 转义（IDE 保存时一般会自动处理）。
- **Inspection 没生效**：确认 `SkillFileDetector.isSkillFile(file)` 通过；目前规则只对 `SKILL.md` 文件名运行。
- **Problems 面板没刷新**：调用 `DaemonCodeAnalyzer.getInstance(project).restart(psiFile)`，或参考 `SkillInspectorAction.publishResult()`。

## 参考代码

- 规则接口：`src/main/java/dev/dong4j/idea/skill/inspector/rules/SkillRule.java`
- 问题模型：`src/main/java/dev/dong4j/idea/skill/inspector/model/SkillProblem.java`
- 严重度枚举：`src/main/java/dev/dong4j/idea/skill/inspector/model/SkillSeverity.java`
- 范围工具：`src/main/java/dev/dong4j/idea/skill/inspector/util/TextRangeUtil.java`
- 测试夹具：`src/test/java/dev/dong4j/idea/skill/inspector/test/SkillFileTestFactory.java`
- 完整规则清单：`docs/rules.md`
