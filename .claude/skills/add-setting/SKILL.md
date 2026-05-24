---
name: add-setting
description: 在 SkillInspectorSettings 中新增持久化字段并暴露设置页字段
---

# /add-setting — 添加设置项

为插件新增一项**应用级**持久化设置（开关 / 数值 / 文本）。**项目惯例**：所有设置挂在同一个 `SkillInspectorSettings` 服务下，不要为每个新功能新建独立的
`PersistentStateComponent`。

## Usage

```
/add-setting [fieldName] [type]
```

- `fieldName` — camelCase 字段名，例如 `bodyMaxLength`、`autoFixOnSave`、`profileName`。
- `type` — `boolean` / `int` / `String` / `enum`。

## Information Needed

1. **字段语义** — 一句话描述它控制什么。
2. **默认值** — 必须给一个安全的默认（开 / 关 / 数值上限）。
3. **作用域** — 默认 application 级（多项目共享）；如果是项目级，要换成 `@State` 的 project storage（**罕见**，本项目无此场景）。
4. **是否影响已开 IDE 实例的实时检查** — 如果是，设置变更后需要触发 `DaemonCodeAnalyzer.restart(...)`。

## Files to Modify

### 1. 状态字段：`src/main/java/dev/dong4j/idea/skill/inspector/settings/SkillInspectorSettings.java`

#### 1.1 在 `State` 内部类追加字段

```java
public static class State {
    /** 是否启用 SKILL.md 格式检查 */
    public boolean skillInspectionEnabled = true;

    /** 正文长度上限, 超过会触发 body.too-long 警告 */
    public int bodyMaxLength = 12000;
}
```

**关键约束**：

- **字段必须是 `public`**：IntelliJ XML 序列化（XmlSerializerUtil）只看 public 字段；私有字段不持久化。
- **必须给默认值**：否则反序列化失败时整个 state 为 null，影响其他设置。
- **类型仅限可序列化的简单类型**：`boolean` / `int` / `long` / `String` / 枚举 / 简单 `List<String>` / `Map<String, String>`。*
  *不要存 `Path` / `VirtualFile` / 任意 POJO**。

#### 1.2 暴露 getter / setter

```java
public int getBodyMaxLength() {
    return state.bodyMaxLength;
}

public void setBodyMaxLength(int bodyMaxLength) {
    this.state.bodyMaxLength = bodyMaxLength;
}
```

完整范式参考：

```42:53:src/main/java/dev/dong4j/idea/skill/inspector/settings/SkillInspectorSettings.java
    public boolean isSkillInspectionEnabled() {
        return state.skillInspectionEnabled;
    }

    /**
     * 设置 SKILL.md 格式检查开关
     *
     * @param enabled true 表示启用检查, false 表示跳过所有 Skill Inspector 规则
     */
    public void setSkillInspectionEnabled(boolean enabled) {
        state.skillInspectionEnabled = enabled;
    }
```

> 不要直接暴露 `state` 字段。把 getter/setter 包一层，便于以后改字段名 / 加默认值兜底。

### 2. 设置页：`src/main/java/dev/dong4j/idea/skill/inspector/settings/SkillInspectorConfigurable.java`

#### 2.1 新建 UI 控件

按字段类型选择：

| 类型        | 控件                   |
|-----------|----------------------|
| `boolean` | `JBCheckBox`         |
| `int`     | `JBIntSpinner`       |
| `String`  | `JBTextField`        |
| 枚举        | `ComboBox<EnumType>` |

```java
private JBIntSpinner bodyMaxLengthSpinner;

@Override
@Nullable
public JComponent createComponent() {
    enabledCheckBox = new JBCheckBox(SkillInspectorBundle.message("settings.enable.inspection"));
    enabledCheckBox.setSelected(SkillInspectorSettings.getInstance().isSkillInspectionEnabled());

    bodyMaxLengthSpinner = new JBIntSpinner(
        SkillInspectorSettings.getInstance().getBodyMaxLength(),
        1000, 100_000, 500
    );

    return FormBuilder.createFormBuilder()
        .addComponent(enabledCheckBox)
        .addLabeledComponent(SkillInspectorBundle.message("settings.body.max.length"), bodyMaxLengthSpinner)
        .addComponentFillVertically(new JPanel(), 0)
        .getPanel();
}
```

> 简单两三项可以继续用 `BorderLayout`（参考已有代码），多于 3 项必须切到 `FormBuilder`，否则布局会乱。

#### 2.2 同步 `isModified` / `apply` / `reset`

每个新增字段都要在三个方法里挂上：

```java
@Override
public boolean isModified() {
    SkillInspectorSettings settings = SkillInspectorSettings.getInstance();
    if (enabledCheckBox != null && enabledCheckBox.isSelected() != settings.isSkillInspectionEnabled()) return true;
    if (bodyMaxLengthSpinner != null && bodyMaxLengthSpinner.getNumber() != settings.getBodyMaxLength()) return true;
    return false;
}

@Override
public void apply() {
    SkillInspectorSettings settings = SkillInspectorSettings.getInstance();
    if (enabledCheckBox != null) settings.setSkillInspectionEnabled(enabledCheckBox.isSelected());
    if (bodyMaxLengthSpinner != null) settings.setBodyMaxLength(bodyMaxLengthSpinner.getNumber());
}

@Override
public void reset() {
    SkillInspectorSettings settings = SkillInspectorSettings.getInstance();
    if (enabledCheckBox != null) enabledCheckBox.setSelected(settings.isSkillInspectionEnabled());
    if (bodyMaxLengthSpinner != null) bodyMaxLengthSpinner.setNumber(settings.getBodyMaxLength());
}
```

**关键约束**：

- **`isModified` 必须覆盖所有字段**：漏一个会导致用户改了之后 `apply` 按钮不亮（IDE 以 `isModified=true` 才允许 apply）。
- **`disposeUIResources` 把控件置 null**：避免 IDE 关闭设置页后再访问到已销毁的 Swing 组件。
- **`apply` 中不要触发耗时操作**：例如不要直接重跑全项目扫描；触发 `DaemonCodeAnalyzer.restart(project)` 即可。

### 3. 让设置变更影响实时检查（可选）

如果新设置直接影响规则结果（如 `bodyMaxLength`），用户改完后理论上要让所有打开的 SKILL.md 重新跑 Inspection：

```java
// 在 apply() 末尾
ProjectManager.getInstance().getOpenProjects()[0]; // 例：拿第一个 project；多 project 要遍历
DaemonCodeAnalyzer.getInstance(project).restart();
```

但**项目当前 V1 没有走这条路径**——依赖 IDE 自身在用户编辑文件时自动重跑 Inspection。新增设置时根据需要再加。

### 4. 注册（已注册，无需改动）

`plugin.xml` 中 `<applicationService>` + `<applicationConfigurable>` 已经注册了 `SkillInspectorSettings` 与 `SkillInspectorConfigurable`：

```26:32:src/main/resources/META-INF/plugin.xml
        <!-- 应用级设置服务 -->
        <applicationService serviceImplementation="dev.dong4j.idea.skill.inspector.settings.SkillInspectorSettings"/>

        <!-- Settings | Tools | Skill Inspector -->
        <applicationConfigurable parentId="tools"
                                 instance="dev.dong4j.idea.skill.inspector.settings.SkillInspectorConfigurable"
                                 displayName="Skill Inspector"/>
```

新增字段不需要碰 plugin.xml；除非你拆出**新的设置页**（罕见，按 GitLens 经验是"少而专"，本项目坚持单页）。

### 5. i18n

```properties
settings.body.max.length=Maximum SKILL.md body length
```

中文：

```properties
settings.body.max.length=SKILL.md 正文长度上限
```

详见 `/add-i18n-key`。

### 6. 测试

`src/test/java/.../settings/SkillInspectorSettingsTest.java` 已经覆盖默认开关。新字段建议加：

```java
@Test
void shouldDefaultBodyMaxLength() {
    assertThat(new SkillInspectorSettings.State().bodyMaxLength).isEqualTo(12000);
}

@Test
void shouldRoundTripState() {
    SkillInspectorSettings.State state = new SkillInspectorSettings.State();
    state.bodyMaxLength = 5000;
    SkillInspectorSettings settings = new SkillInspectorSettings();
    settings.loadState(state);
    assertThat(settings.getBodyMaxLength()).isEqualTo(5000);
}
```

## Build & Verify

```bash
./gradlew compileJava
./gradlew test --tests "*SkillInspectorSettingsTest*"
./gradlew runIde
# IDE: Settings → Tools → Skill Inspector → 改值 → Apply
# 关闭 IDE 重启 → 验证值已持久化（写到 ~/Library/.../options/skillInspector.xml）
```

## Troubleshooting

- **设置不持久化**：字段不是 `public`；或字段是 `final`；或类型不可序列化。
- **`apply` 按钮一直灰**：`isModified` 漏掉了新字段的对比。
- **重启 IDE 后字段变成默认值**：`State` 类没给字段默认值，反序列化为 null（基本类型会回到 0 / false）。
- **`Cannot get state of disposed component`**：在 `disposeUIResources` 之后再访问控件。重置时确保先判 null。
- **多 IDE 实例间设置不同步**：application 级设置在多 IDE 实例间共享但需要重启另一实例才能拉到最新；这是 IntelliJ 平台限制，不是 bug。

## 参考代码

- 设置服务：`src/main/java/dev/dong4j/idea/skill/inspector/settings/SkillInspectorSettings.java`
- 设置页：`src/main/java/dev/dong4j/idea/skill/inspector/settings/SkillInspectorConfigurable.java`
- 测试：`src/test/java/dev/dong4j/idea/skill/inspector/settings/SkillInspectorSettingsTest.java`
- 注册位置：`src/main/resources/META-INF/plugin.xml`
- IntelliJ 官方文档：<https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html>
