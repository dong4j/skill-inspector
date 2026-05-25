# Skill Inspector — Landing Page (v2)

> 这是 Skill Inspector 插件的对外 landing page, 由
> [`.claude/skills/landing-page-guide-v2`](../.claude/skills/landing-page-guide-v2/SKILL.md)
> 这份 skill 落地生成. 当前是**静态 HTML 版本** (零构建依赖), 直接 `nginx` 静态托管即可.
>
> **当前版本: v2 — Strict Editorial ("Specification Manual")**
> 暖白 oat 基底 + OS 系统字体 + 严苛红 + 章节编号制 + 真实规则表 + before/after code diff.

> **部署**: 仓库根有一份 [`deploy.sh`](../deploy.sh), 一键完成 `rsync` 同步 + 可选发布到
> Marketplace + 可选上传 zip; 无脑跑 `./deploy.sh -d` 即可只部署 landing 站点.

---

## 版本变更记录

| 版本 | 风格 | 状态 |
|----|----|----|
| v1 | Editorial-Tech (Dark Lab) — 深色 + cyan/green | 已废弃 |
| **v2** | **Strict Editorial — Specification Manual** | **当前** |

v1 的视觉与姐妹站 SkillsJars Helper 雷同, 缺乏自己的辨识度. v2 借助
Skill Inspector 作为 Lint 工具的天然严肃感, 走完全反方向: **像一份正式 spec
文档的产品页**.

### 设计参考调性

- [Biome](https://biomejs.dev) — 亮色基底 + 大写小标
- [OXC](https://oxc.rs) — 性能数据 + 代码对比
- RFC 文档 — 编号、章节、可引用
- [Stripe Press](https://press.stripe.com) — Tailored typography + editorial 节奏
- NYT 排版 — 衬线大标 + marginalia 旁注

### 核心设计决策

| 决策点 | 选择 | 理由 |
|----|----|----|
| 基底色 | 暖白 oat `#F7F4ED` | 纸感, 与深色 IDE 截图风形成强反差; 易读 |
| 主 accent | 严苛红 `#C32F27` | RFC "MUST NOT" 风格; 呼应 Lint violation 语义 |
| 链接色 | Ink blue `#1E3A5F` | 学术 / 出版物风的克制蓝, 不喧宾夺主 |
| 修复色 | Forest green `#3E6B47` | code diff 中 "+ After" 行 |
| Display / Body 字体 | **OS system font stack** | 跨平台呈现各 OS 原生字体 (macOS PingFang / Windows 雅黑), 视觉熟悉度最高, 零 web font 加载 |
| Mono 字体 | JetBrains Mono | 与 IDE 内一致, 是开发者熟悉的"品牌字体" |
| Hero 形式 | 大标 italic 强调 + 右侧 marginalia 元数据栏 | 取消巨幅 IDE mockup, 给 data 让位 |
| Rules 展示 | 真实表格 (12 条精选, 含 severity/quick fix) | Lint 工具最有说服力的展示就是 "把规则摊出来" |
| Features 展示 | 编号大数字 + 段落 (替代 bento 卡片) | 像 spec 的 sub-section, 严肃可读 |
| Media 展示 | Code diff before/after (取代 IDE mockup) | 对 Lint 工具来说, 修复前后对比比截图更有信息密度 |
| Testimonials | blockquote 风 4 类受众 (无假人名假头像) | 维持 "不造假" 原则 |
| FAQ | §6.1 / §6.2 编号制 | 像 spec 一样可引用 |
| 动画 | 仅 fade-in (无 translate) | "字停纸上" 的物理感, 不破坏纸面稳重感 |

---

## 目录结构

```
landing/                    # 仓库根下, 与插件主代码并列
├── index.html              # 英文版 (v2 Strict Editorial) — 主页
├── zh/
│   └── index.html          # 中文版 (v2, 1:1 翻译英文版)
├── assets/                 # 双语共用
│   ├── styles.css          # 设计系统 (v2 Strict Editorial)
│   ├── main.js             # 极简动效 (reveal + header sentinel, 共 2 个 setup fn)
│   └── plugin-icon.svg     # 插件官方图标 (Marketplace 同款)
├── nginx.conf              # 站点 nginx 配置
└── README.md               # 当前文件
```

URL 设计:

- `/` → 英文版 (`index.html`, v2 完整)
- `/zh/` → 中文版 (`zh/index.html`, v2 完整)
- 顶部右侧 `EN | 中文` toggle 互相跳转
- 两个版本都通过 `<link rel="alternate" hreflang="…">` 互相引用

零依赖原则:

- **不需要** `node` / `npm` / `next` / `tailwind` 任何前端工具链.
- **唯一外网依赖**是 Google Fonts 上的 JetBrains Mono (display/body 全部走系统字体,
  零 web font), 已用 `display=swap` 防 FOIT, 内网部署时见 "离线字体" 小节.
- 体积 < 60 KB (HTML + CSS + JS + SVG, gzip 前), 加载时间几乎只取决于网络往返.

---

## 本地预览

任意一个静态文件服务器都能跑. 推荐 Python:

```bash
cd landing
python3 -m http.server 8000
# 浏览器打开 http://localhost:8000
```

或用 `npx serve` / `caddy` / `darkhttpd` 都行.

---

## 部署到云服务器 (推荐: 用根目录 deploy.sh)

仓库根的 `deploy.sh` 已经把 `rsync` / `nginx` 配置部署 / 插件市场发布全部封装好.
**先在脚本顶部 CONFIG 区把 `REMOTE_HOST` / `REMOTE_ROOT_DIR` / `SITE_URL` 改成你自己的值**,
然后一行命令搞定:

```bash
./deploy.sh -d         # 仅部署 landing 站点 (最常用)
./deploy.sh -n         # 仅部署 nginx 配置 + reload (首次部署需要)
./deploy.sh            # 默认: publish + upload zip + 部署 landing
./deploy.sh -h         # 看完整 usage
```

完整原理见 [`deploy.sh`](../deploy.sh) 脚本顶部注释.

### 手工部署 (备选)

```bash
rsync -avz --delete landing/ \
  user@your-server:/var/www/skill-inspector/

scp landing/nginx.conf user@your-server:/etc/nginx/conf.d/skill-inspector.conf
ssh user@your-server "nginx -t && systemctl reload nginx"
```

### 加 HTTPS (推荐 certbot)

```bash
sudo certbot --nginx -d skill-inspector.example.com
```

### 验证

```bash
curl -I https://skill-inspector.example.com/
# 期望: HTTP/2 200, Cache-Control 头按 nginx.conf 配置返回
```

---

## 替换占位文案 / 链接

`index.html` 中目前所有链接的指向:

- **GitHub**: <https://github.com/dong4j/skill-inspector>
- **Marketplace**: <https://plugins.jetbrains.com/plugin/31938>
- **作者其他插件 (vendor 页)**: <https://plugins.jetbrains.com/vendor/9afaba35-91ea-4364-8ced-64db868dd23e>
- **作者邮箱**: `dong4j@gmail.com`

需要根据实际情况调整的地方:

1. **域名**: 决定使用哪个域名后, 在 `<meta property="og:image">` 等处补全绝对 URL.
2. **真实 testimonials**: §5 当前是 4 类典型受众的真实场景, 而非假人名假头像.
   收集到真实用户引用后, 可以替换 `.persona-quote` / `.persona-cite` 文案.

---

## 字体策略

| 角色 | 字体 | 加载方式 |
|---|---|---|
| display + body | OS system font stack — `-apple-system` (San Francisco) / `Segoe UI` / `PingFang SC` / `Microsoft YaHei` / `Roboto` / `Helvetica Neue` | **零外部加载**, 每个 OS 显示自己的原生字体 |
| mono (代码块) | `JetBrains Mono` (Google Fonts) | 单一 web font, fallback 到 SF Mono / Consolas / Menlo |

为什么改成 system stack:

1. **视觉熟悉度最高** — 用户在任意网站 / OS 见到的都是同一套字体, 不会"突兀".
2. **零字体加载延迟** — 不再依赖 Google Fonts 的 display=swap, 没有 FOIT/FOUT.
3. **离线 / 内网友好** — 切掉一个外网依赖.
4. **加载体积 ↓** — display 字体不再额外加载 web font (省下 ~150 KB 的 Fraunces variable).

视觉层级通过**字号 + 字重 + 字距**制造, 而不是靠衬线:

- Hero 标题: `clamp(2.4rem, 6.5vw, 5.4rem)` · weight 600 · `letter-spacing: -0.025em`
- 章节标题: `clamp(2rem, 4.4vw, 3.4rem)` · weight 400
- italic 红色强调词 (`<em>`) 制造视觉重点, 替代衬线

### 选项: 完全离线 (推荐生产环境)

如果需要进一步切断 Google Fonts 依赖, 用
[google-webfonts-helper](https://gwfh.mranftl.com/fonts) 下载 JetBrains Mono
的 woff2 到 `assets/fonts/`, 然后:

1. 把 `<link>` 改成本地 `@font-face`:

   ```css
   @font-face {
     font-family: 'JetBrains Mono';
     font-style: normal;
     font-weight: 400 500;
     font-display: swap;
     src: url('./fonts/jetbrains-mono.woff2') format('woff2');
   }
   ```

2. 删除 `<link>` Google Fonts 标签 → 落地页变成 **0 外部依赖**.

---

## 11 essential elements 自检表

按 `landing-page-guide-v2` 的验证清单一一勾过:

- [x] URL with keywords — `<title>` / `<meta>` 嵌入 `skill-inspector` 等关键词.
- [x] Company logo (header) — sticky header + 真实插件 SVG.
- [x] SEO-optimized title and subtitle — §1 hero, 粗体 + italic 红色强调词.
- [x] Primary CTA (hero) — 黑底 + 红色印刷投影双 CTA.
- [x] Social proof (hero) — 右侧 marginalia 数据栏 + §2 表格底部 tally.
- [x] Images / videos (media) — §4 code diff gallery, 3 个真实 before/after.
- [x] Core benefits / features — §3 编号大数字 + 段落.
- [x] Customer testimonials — §5 blockquote 风 4 类受众 (拒绝造假).
- [x] FAQ section — §6 spec 风 §6.1 / §6.2 编号.
- [x] Final CTA — §7 黑底横幅 + 红色裁切线.
- [x] Footer — 4 列 + 受众价值主张 ("Made for JetBrains developers who write Agent Skills").

设计原则自检 (来自 `landing-page-guide-v2` "AVOID Generic AI Aesthetics"):

- [x] 字体: 全部使用 OS system font stack — 不在 skill 字体 ban list 上 (Inter / Geist
  / Manrope 等被 ban 的字体我们一个也没用), 用户在每个 OS 看到的是最熟悉的系统字体;
  视觉重量靠字号 / 字重 / italic 红色强调词制造, 而不是靠衬线.
- [x] 不用 "purple gradient on white" — 用 oat + 严苛红, 完全不同的色彩家族.
- [x] 不用纯白背景 — 暖白 oat + 微弱纸纹.
- [x] 不用对称 3 列 grid — section 用 1fr:2fr / 100px:1fr:1.3fr 的不对称栏.
- [x] ShadCN 默认按钮没有出现 — 方角 button + 红色印刷投影 (translate hover).
- [x] 不用 generic 图标 line drawing grid — 替换为真实表格 + code diff.
- [x] 不用三段式 bento — features 走编号大数字 + 段落, 像 spec sub-section.

---

## 维护提示

- **修改规则表 (§2)**: 同步更新 `index.html` 中 `rules-table` 与
  [`docs/rules.md`](../docs/rules.md). 当前只展示 12 条精选 (按视觉/示例价值挑),
  完整 30+ 条仍在 docs.
- **修改 code diff 示例 (§4)**: `diff-item` 是独立 block, 复制一份改文字即可;
  注意 `diff-line` 的 grid 是 `28px 12px 1fr`, 改宽度时三个值都要保持比例.
- **改设计 token**: 集中在 `styles.css` 顶部 `:root { ... }` 一处.
- **插件图标更新** (`src/main/resources/META-INF/pluginIcon.svg`):
  重新拷贝到 `landing/assets/plugin-icon.svg`, header / footer / favicon 三处自动跟随.
- **双语同步**: 改英文版 (`index.html`) 时, 同步改中文版 (`zh/index.html`).
  两份文件信息架构 1:1 对齐, section ID (#rules / #features / #diffs / #faq)
  与章节编号 (§1—§7) 完全一致, 便于通过 anchor 比对.

---

## 升级路径: 静态 HTML → Next.js 14 + ShadCN UI

如果以后想严格按 skill 默认要求重写成 Next.js 工程:

1. 在仓库外 `~/Developer/0.Worker/opensource/idea/skill-inspector-website/`
   `npx create-next-app@latest --typescript --tailwind --app --src-dir`.
2. `npx shadcn-ui@latest add button card accordion badge avatar separator input table`.
3. 把当前 `styles.css` 的 CSS variables 拷到 `app/globals.css`.
4. 按 `landing-page-guide-v2/references/component-examples.md` 的样板组件,
   保留当前文案 / 设计语言, 把每个 section 改成 React 组件.

当前静态版可作为**视觉基线**: 任何 Next.js 重写如果在 aesthetic / 信息架构上和它
偏离太远, 就要回头确认是否丢掉了 v2 "spec 手册" 的设计意图.
