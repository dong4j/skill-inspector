# Skill Inspector 阶段性状态

> 这是一份**活文档**，每次有阶段性进展（feature / refactor / 文档结构调整）都应回来更新本文。
> 待办优先级在 [`./todo.md`](./todo.md) 同步维护，本文负责"全景视图 + 进度勾选"。
>
> 关联：愿景见 [`../README.md`](../README.md)，路线见 [`./roadmap.md`](./roadmap.md)，规则见 [`./rules.md`](./rules.md)，
> 设计见 [`./design.md`](./design.md)，AI 审查方向见 [`./ai-review.md`](./ai-review.md)。

## 一、当前坐标

| 维度                                                       | 状态                                |
|----------------------------------------------------------|-----------------------------------|
| V1 MVP（Inspection 闭环 + 4 套规则 + 6 Quick Fix + 总开关）        | ✅ 完成                              |
| V1 收尾（2 条规则 + reference-style links + Action 实化）         | ✅ 完成（Inspection fixture 测试延后到 V2） |
| V1 体验增强（编辑器浮动按钮 PoC 方案 A / B 双路并存）                       | ✅ 完成（A=官方右上角 / B=自定义右下角 FAB）      |
| V2 多 Agent Profile                                       | ⏳ 设计已就绪，未启动                       |
| V3 Skill Explorer / V4 SkillsJar Manager / V5 Zeka Stack | 📅 等 V2 验证后                       |
| AI 审查（二期）                                                | 📅 提示词模板已沉淀到 `ai-review.md`，待集成   |

## 二、已实现 —— V1 MVP 全貌

近 15 次提交清晰呈现从 0 到 1 的路径：

```text
infra → model → parser → rules → quickfix → settings/statusbar → inspection → tests → docs
```

### 2.1 模块清单（32 个 Java 源文件 + 11 个测试类）

| 模块            | 落地能力                                                                                                                                                                            |
|---------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **检测入口**      | `SkillFileDetector` 按文件名 `SKILL.md` 守门                                                                                                                                          |
| **解析层**       | Markdown PSI 定位 frontmatter → YAML PSI 解析键值；Markdown PSI 提取 `LINK_DESTINATION`                                                                                                  |
| **领域模型**      | 9 个 record（`SkillFile` / `SkillFrontMatter` / `SkillMetadata` / `SkillBody` / `SkillYamlEntry` / `SkillReference` / `SkillProblem` / `SkillFixType` / `SkillSeverity`），零 IDE 依赖 |
| **结构规则**      | 11 条 Error，覆盖 frontmatter 必填、长度上限、kebab-case、name/目录一致性                                                                                                                         |
| **质量规则**      | 6 条 Warning / Weak Warning，覆盖 description 与正文质量                                                                                                                                 |
| **引用规则**      | 4 条，相对链接缺失 / 越界 / 大小写 / 非法路径                                                                                                                                                    |
| **安全规则**      | 5 条，secret / 危险命令 / 过宽 `allowed-tools` / 敏感路径 / prompt injection                                                                                                                |
| **Quick Fix** | 6 种确定性修复                                                                                                                                                                        |
| **配置入口**      | 应用级 `PersistentStateComponent` + Settings 页 + 状态栏（图标单击切换）                                                                                                                       |
| **测试覆盖**      | parser 8 类用例 / 四套 rules / RuleRunner / Quick Fix 文本 / settings / detector                                                                                                       |

### 2.2 关键架构决策（值得长期保持）

1. **三段管道，核心规则可移植**
   ```text
   SkillFileDetector → FrontMatterParser → SkillModelBuilder → RuleRunner → ProblemDescriptor
            (IDE)         (IDE PSI)          (IDE PSI)         (纯 Java)      (IDE)
   ```
   `model/ + rules/ + SkillQuickFixTexts` 不依赖 IntelliJ API，未来可直接复用到 CLI / Maven 插件 / GitHub Action。

2. **PSI-first，拒绝正则切分**
   YAML frontmatter 走 Markdown PSI + YAML PSI 双重解析；Markdown 链接走 `LINK_DESTINATION` 节点。避免代码块 / HTML 注释里的伪结构污染。

3. **Quick Fix 枚举派发，避免 Fix 类爆炸**
   `SkillProblem.fixTypes` (`List<SkillFixType>`) → 单一 `SkillQuickFix` 派发 → 文本生成集中在 `SkillQuickFixTexts`。规则层不直接 new
   IntelliJ 对象。

4. **配置入口收敛**
   应用级 `skillInspectionEnabled` 一个开关 → Settings 页和状态栏共用同一份状态 → 关闭即整个 Inspection 短路返回。

5. **状态栏极简交互**
   `IconPresentation` + `getClickConsumer()`，靠 `AllIcons.General.InspectionsEye` / `InspectionsPowerSaveMode` 两个内置图标做颜色对比，无自定义资源，主题自适应。

## 三、贯穿 V1 - V5 + AI 审查 的 TODO List

> 勾选规则：✅ 已完成 · 🚧 进行中 · ⏳ 未开始 · ❌ 明确不做（带原因）

### V1 MVP — ✅ 全部完成

- [x] `SkillFileDetector` 文件名守门
- [x] `FrontMatterParser`：Markdown PSI + YAML PSI 解析，兼容 UTF-8 BOM 与块标量
- [x] `SkillModelBuilder` 构建领域模型
- [x] `StructuralRules`：11 条规则
- [x] `QualityRules`：6 条规则
- [x] `ReferenceRules`：4 条规则（inline link / image link）
- [x] `SecurityRules`：5 条规则
- [x] `SkillMdInspection`：LocalInspectionTool 适配
- [x] `SkillQuickFix` + `SkillFixType`：6 种修复
- [x] 应用级 `PersistentStateComponent` + Settings 页
- [x] 状态栏组件（V1 末期重构为图标单击切换）
- [x] 单元测试：parser / 四套 rules / RuleRunner / quickfix / settings / detector

### V1 收尾 — ✅ 全部完成（Fixture 测试延后到 V2）

- [x] `resource.unused-reference` 规则：`references/` 下未被正文链接的文件
- [x] `script.missing-usage` 规则：`scripts/` 下脚本未在正文说明用法
- [x] Markdown reference-style links 解析：PSI 递归遍历天然覆盖 `LINK_DEFINITION` 节点中的 `LINK_DESTINATION`，新增专门测试用例锁定行为
- [x] `SkillInspectorAction` 实化：扫描全项目 SKILL.md → 跑 `RuleRunner` 汇总 Error/Warning → 激活 Problems View → 自动打开第一个有错的文件
- [x] 为新规则、reference-style links、Action 补单元测试（测试总数 53 → 63）
- [x] **编辑器浮动按钮 PoC 方案 A**（`SkillFloatingToolbarProvider`）：复用 `SkillInspectorAction`，
  挂在 IntelliJ 官方 `com.intellij.editorFloatingToolbarProvider` EP，仅在文件名为 `SKILL.md` 时显示，
  位置由平台决定（右上角，鼠标悬停淡入），与 IDE AI Assistant 风格一致
- [x] **编辑器浮动按钮 PoC 方案 B**（`SkillBottomFloatingButton` + `SkillBottomFloatingInstaller`）：
  自定义 36 dp 蓝色圆形 FAB，通过 `AppLifecycleListener` 挂全局 `EditorFactoryListener`，
  把按钮放到编辑器外层 `JLayeredPane#PALETTE_LAYER`，**精确停在右下角**（带阴影 + 悬停加深），
  与方案 A 同时启用，方便用户在沙箱里直接对比效果与权衡
- [ ] Inspection fixture 测试：用 `CodeInsightTestFixture` 验证 `ProblemDescriptor` 注册与 Quick Fix 真实写入（**延后到 V2**，与 Profile 引入一并补）

### V2 多 Agent Profile — ⏳ 未开始

- [ ] Profile 数据模型：Generic / Claude Code / Codex / Junie / Copilot / Cursor
- [ ] frontmatter 字段兼容性表（`allowed-tools` 等 experimental 字段映射）
- [ ] Settings 页支持选 profile（应用级 + 项目级覆盖）
- [ ] 兼容性 warning（不阻塞，作为 info 提示）
- [ ] 规则严重度覆盖（用户可降级 / 升级单条规则）
- [ ] 自定义阈值（最大正文长度、description 长度等）
- [ ] 安全扫描独立开关

### V2 收尾增强（已识别但不在 V2 主线）

- [ ] **`New > SKILL.md` 文件模板**：注册 `FileTemplate`，新建即带合规 frontmatter
- [ ] **Live Template / Postfix 补全**：`skill-meta`、`skill-section` 等模板
- [ ] **Intention：Rename Skill**：同步重命名父目录 + name
- [ ] **Intention：Extract Section to references/**：协助拆分过长正文
- [ ] **Inspection 抑制注释**：支持 `<!-- noinspection ruleId -->` 行级抑制
- [ ] **状态栏图标问题角标**：当前 SKILL.md 有 N 个 Error 时显示数字角标
- [ ] **Problems 面板分组**：4 类规则拆成 4 个 Inspection，用户可在 Inspection profile 树里单独勾选
- [ ] **Frontmatter Schema 提示**：给 YAML 注入 schema，敲 `na` 时补全 `name:`
- [ ] **Quick Fix Preview**：接 `LocalQuickFix.generatePreview`

### V3 Skill Explorer — ⏳ 未开始

- [ ] ToolWindow 列出项目级 / 用户级 skills
- [ ] 双击打开 `SKILL.md`，悬停预览 description
- [ ] 按问题数 / Agent / 风险等级过滤
- [ ] **Skill Diff**：两个 SKILL.md 的语义化 diff（按 section / frontmatter 字段对比）
- [ ] **导入向导**：从 `.claude/skills` 一键导入并适配

### V4 SkillsJar Manager — ⏳ 未开始

- [ ] 扫描 Maven / Gradle 依赖里的 `META-INF/skills/**/SKILL.md`
- [ ] 预览 frontmatter 和正文
- [ ] 解包到 `.claude/skills`、`.junie/skills`、`.agents/skills`
- [ ] JAR 内 skill 的只读检查（复用 V1 规则）

### V5 Zeka Stack 集成 — ⏳ 未开始

- [ ] 读 `pom.xml`，识别 Spring Boot / Zeka Stack / MyBatis / Nacos / XXL-JOB 等组件
- [ ] 推荐对应技术栈的 skill 模板
- [ ] 校验 skill 内容与当前组件版本的匹配度
- [ ] 对接未来 Maven 插件的 `validate` / `index` / `extract`

### 二期 AI 审查方向 — ⏳ 提示词已沉淀

详见 [`./ai-review.md`](./ai-review.md)，与本节通过编号挂钩：

- [ ] **AR-1** AI Provider 抽象层（Copilot / Claude / OpenAI / 本地 Ollama）
- [ ] **AR-2** 5 维语义分析（矛盾 / 歧义 / 人格一致性 / 认知负载 / 语义覆盖）
- [ ] **AR-3** Composition Conflict 分析（SKILL.md 与 `references/` 下其它文件的冲突检测）
- [ ] **AR-4** AI 结果 → IntelliJ `ProblemDescriptor` 的映射 + 文本定位（`findTextRange` 等价实现）
- [ ] **AR-5** 防注入：`<DOCUMENT_TO_ANALYZE>` 包裹用户内容 + 系统提示词强制为 DATA
- [ ] **AR-6** 自定义诊断（用户可在 Settings 配置 N 条自然语言诊断规则）
- [ ] **AR-7** AI 结果缓存：相同 SKILL.md hash 不重复调用
- [ ] **AR-8** "AI 建议 → 用户确认 → 应用" 流程（永不静默改写）

### 明确不做（带原因）— ❌

- ❌ **拆分过长正文 Quick Fix**：拆分点带主观判断（章节边界、语义），违反"保守修复"原则，仅给提示
- ❌ **收窄 `allowed-tools: Bash` Quick Fix**：哪些 Bash 子命令"安全"高度依赖上下文，且字段仍是 experimental
- ❌ **自动扩写 description**：替用户生成主观内容，违反"不替用户写正文"
- ❌ **V1 扫描 Maven/Gradle classpath**：留到 V4
- ❌ **远端 Skill Registry**：留到 V4 之后
- ❌ **LLM 自动改写正文**：AI 审查只给"建议"，不静默 apply

## 四、文档健康度审计

| 文档                            | 状态    | 备注                                                                   |
|-------------------------------|-------|----------------------------------------------------------------------|
| `README.md`                   | ✅ 对齐  | 项目愿景与规范基准                                                            |
| `AGENTS.md`                   | ✅ 对齐  | V1 收尾时已更新：Action 行为说明、ResourceRules 包结构、status.md / ai-review.md 文档表 |
| `docs/design.md`              | ✅ 对齐  | 领域模型 + 模块划分与代码一致                                                     |
| `docs/rules.md`               | ✅ 对齐  | 新增 `resource.unused-reference` / `script.missing-usage` 两行，删除"计划中"提示 |
| `docs/todo.md`                | ✅ 对齐  | 把已交付项纳入"已交付能力"清单，删除"待实现规则"段，"待实现 Quick Fix"仅留明确不做项                   |
| `docs/roadmap.md`             | ✅ 对齐  | V1-V5 路线清晰                                                           |
| `docs/implementation-plan.md` | ✅ 对齐  | 新增 Phase 5 "V1 收尾"段，"未开始"清单清掉过时项                                     |
| `docs/status.md`              | ✅ 本文件 | 活文档，每阶段更新                                                            |
| `docs/ai-review.md`           | ✅ 新增  | 沉淀 microsoft 项目提示词参考                                                 |

## 五、下一步动作

1. ~~完成 V1 收尾 5 项（参见 §3 V1 收尾）~~ —— ✅ 完成
2. ~~同步 `rules.md` / `todo.md` / `implementation-plan.md` 中过时表格~~ —— ✅ 完成
3. **下一步**：V2 启动前，先评审 `ai-review.md` 中 8 项 AR 任务，决定二期 AI 审查与 V2 Profile 的实施先后
4. **下一步**：V2 启动时同步补齐 Inspection fixture 测试（`CodeInsightTestFixture`）

---

最后更新：2026-05-25（编辑器浮动按钮 PoC 方案 A / B 双路并存）
