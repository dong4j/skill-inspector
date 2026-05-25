# Skill Inspector Fixture 测试集

这个目录下的每个子目录都是一个独立的 SKILL.md fixture，用于在 `runIde` 启动的 IDE 测试实例里
**手动验证 Skill Inspector 所有校验规则**。

## 怎么用

```bash
./gradlew runIde
```

在弹出的 IDE 测试实例里：

1. **File → Open** 选择 `src/test/resources/fixtures/skills` 这个目录作为项目
2. 逐个打开每个子目录下的 `SKILL.md`
3. 对照下表验证 **Problems View 列表 + 编辑器内高亮 + 浮动按钮通知** 是否符合"预期触发"列

## fixture 清单（按规则严重度分组）

### 对照组（应该零警告）

| 目录 | 预期触发 | 说明 |
|------|---------|------|
| `perfect-skill/` | （无） | 完整且合规的 SKILL.md，含 `references/` + `scripts/` 且都被正确引用 |

### Structural 规则（ERROR）

| 目录 | 预期触发 ruleId | 备注 |
|------|----------------|------|
| `no-frontmatter/` | `frontmatter.missing` | 没有 YAML 头部；body 含触发词，所以 body.missing-trigger 不会触发 |
| `invalid-yaml/` | `frontmatter.invalid-yaml` | YAML 中故意写 `broken: [ unterminated`；命中后 StructuralRules 直接 return，因此 name/description 后续规则**不会**报 |
| `missing-name/` | `frontmatter.name.missing` | frontmatter 里没 name 字段 |
| `name-too-long/` | `frontmatter.name.too-long` + `frontmatter.name.mismatch` | name 91 字符 > 64；name 是合规 kebab-case，所以 name.invalid 不触发；name ≠ 目录名所以 mismatch 顺带触发 |
| `name-invalid/` | `frontmatter.name.invalid` + `frontmatter.name.mismatch` | name 是 PascalCase 不合规；name ≠ 目录名所以 mismatch 顺带触发 |
| `name-mismatch/` | `frontmatter.name.mismatch` | name 合规且 ≠ 目录名 |
| `missing-description/` | `frontmatter.description.missing` | frontmatter 里没 description |
| `description-too-long/` | `frontmatter.description.too-long` | description > 1024 字符 |
| `compatibility-empty/` | `frontmatter.compatibility.empty` | compatibility 字段值为空 |
| `compatibility-too-long/` | `frontmatter.compatibility.too-long` | compatibility > 500 字符 |
| `BadDirectoryName/` | `skill.directory.name` + `frontmatter.name.invalid` | 目录用了 PascalCase；name 等于目录名所以 mismatch 不触发，但 name 本身也不是 kebab-case 所以 name.invalid 顺带触发 |

### Quality 规则（WARNING / WEAK_WARNING）

| 目录 | 预期触发 ruleId | 备注 |
|------|----------------|------|
| `short-description/` | `description.too-short` + `description.missing-usage` | description "too short" 9 字符 < 20，且不含触发词 |
| `generic-description/` | `description.too-short` + `description.too-generic` + `description.missing-usage` | description 是 "helper"（6 字符），同时命中过短 + 过泛 + 缺触发词 |
| `description-no-usage/` | `description.missing-usage` + `body.missing-trigger` | description / body 都不含 "use when" 等触发词 |
| `body-no-title/` | `body.missing-title` | body 第一行不是 `# 一级标题`；description / body 都含触发词所以 missing-trigger 不触发 |
| `body-no-trigger/` | `description.missing-usage` + `body.missing-trigger` | body + description 都无触发词，所以 description.missing-usage 也顺带触发 |
| `body-too-long/` | `body.too-long` | body 文本长度 ≈ 36 KB > 12000 字符阈值 |

### Reference / Resource 规则（WARNING）

| 目录 | 预期触发 ruleId | 备注 |
|------|----------------|------|
| `reference-missing-file/` | `reference.missing-file` | body 链接的 references/missing.md 不存在 |
| `reference-case-mismatch/` | `reference.case-mismatch` | body 写 `references/usage.md`，实际文件是 `References/usage.md`（macOS 大小写不敏感才能验证；Linux 上会先触发 `reference.missing-file`） |
| `reference-outside-skill/` | `reference.outside-skill` | body 链接 `../escape.md` 跳出 skill 根目录 |
| `unused-reference-resource/` | `resource.unused-reference` | `references/orphan.md` 存在但 body 从未引用 |
| `script-missing-usage/` | `script.missing-usage` | `scripts/orphan.sh` 存在但 body 从未提及 |

### Security 规则

| 目录 | 预期触发 ruleId | 严重度 |
|------|----------------|--------|
| `security-secret/` | `security.secret-pattern` | ERROR |
| `security-dangerous-command/` | `security.dangerous-command` | ERROR |
| `security-allowed-tools-bash/` | `security.allowed-tools-bash` | WARNING |
| `security-sensitive-path/` | `security.sensitive-path` | WARNING |
| `security-prompt-injection/` | `security.prompt-injection` | WARNING |

## 注意

- **多规则联动**：很多 fixture 会附带触发"前置缺失"导致的额外规则，已在备注列说明。逐项核对 ruleId 列表而非数量。
- **macOS 大小写陷阱**：`reference-case-mismatch/` 在 Linux 上行为不同（直接报 `reference.missing-file`），属于已知差异。
- **不要在这些 fixture 上跑 fix**：它们是为了**触发**问题，被 Quick Fix 修掉之后再跑就失效了，建议改完后 `git checkout` 还原。
