# Skill Inspector TODO

本文档只保留 Skill Inspector 当前未实现、待实现的功能清单。所有已落地的能力都应从此文档迁出，避免把 TODO 写成事后总结。

> 检查规则见 [`./rules.md`](./rules.md)，版本路线见 [`./roadmap.md`](./roadmap.md)，项目愿景与规范基准见 [`../README.md`](../README.md)
> ，落地约束与领域模型见 [`./design.md`](./design.md)。

## MVP 功能

第一版先做 `SKILL.md` Inspection，不做复杂的 Skill 管理和远端同步。

| 功能               | 说明                                    |
|------------------|---------------------------------------|
| SKILL.md 识别      | 只对名为 `SKILL.md` 的文件启用检查               |
| frontmatter 校验   | 检查 `---` 包裹的 YAML 头部是否存在且可解析          |
| 必填字段检查           | 检查 `name`、`description` 是否存在          |
| 目录名匹配            | 检查 `name` 是否等于父目录名                    |
| 命名规范             | 检查 skill 名称是否为 kebab-case             |
| 描述质量             | 检查描述是否过短、过泛或缺少触发条件                    |
| allowed-tools 检查 | 检查工具列表格式和危险权限                         |
| Markdown 引用检查    | 检查相对链接指向的文件是否存在                       |
| 长度提示             | 提醒 `SKILL.md` 过长，建议拆分到 `references/`  |
| 安全扫描             | 检查疑似 secret、危险命令和 prompt injection 文案 |
| Quick Fix        | 自动补 frontmatter、修正 name、创建缺失引用文件      |

## Quick Fix 方向

- 创建缺失的 frontmatter。
- 补齐 `name` 和 `description`。
- 将 `name` 修正为父目录名。
- 将不合法名称转换为 kebab-case。
- 创建缺失的引用文件。
- 将过长正文提示拆分到 `references/`。
- 收窄危险的 `allowed-tools` 配置。
