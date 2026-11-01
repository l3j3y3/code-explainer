# 代码解释器

贴一段代码 → 自动识别语言 → 逐行中文解释。纯前端、离线可用；可选接 DeepSeek 做「AI 详解」。

- **网页版（PWA）**：<https://l3j3y3.github.io/code-explainer/>
  手机浏览器打开 → 菜单里「添加到主屏幕」，就是一个独立窗口的 app。
- **安卓 APK**：<https://l3j3y3.github.io/code-explainer/apk/code-explainer.apk>
  （同一个包也挂在 [Releases](https://github.com/l3j3y3/code-explainer/releases)，两条路任选一条能下就行）
- 首次装 APK 要允许「安装未知应用」，包名 `com.l3j3y3.codeexplainer`，只申请联网权限。

## 功能

- 8 种语言自动识别：Python / C / C++ / Java / JavaScript / SQL / HTML / CSS
  - 识别按信号打分，**候选全列**：顶栏「识别」那一格能点开，看到每种语言的分数和命中的理由，点一条就按它解释
  - 认不出就标「纯文本」，不再硬套 Python
- **整体**：一句话总结 + 结构块（第几行到第几行是什么类/函数）+ 主要步骤 + 行数统计
- **逐行**：按结构块聚合成卡片（空行折叠），每行配一句中文说明；「全部 / 只看要点」带行数，没被规则覆盖的行是淡色一句，不占正经解释
- **词表**：出现的关键字/内置/运算符，点开看含义 + 示例 + 官方文档链接；点行号能跳回逐行并高亮
- **AI 通道（可选）**：设置里填自己的 DeepSeek API key；填好之后**顶栏那颗「AI」就是开关**，点一下进 AI 模式，再点一下回纯离线
  - 开 AI 的那一刻会自动做两件事：把离线判的**算法类型 / 纠错 / 一句话总结 / 语言**拿去让 AI 复核，判错就换成它的结论（算法卡上标「来源：AI」，还留一个「看离线猜的」能换回去）；再把**逐行全部重讲**（不是只补没讲的行——规则文案那股生硬味就是这儿来的）
  - 整体「AI 详解」——流式生成；带超时看门狗，讲到一半断了保留已出的字并自动「接着讲」
  - 逐行「AI 重讲全部行」——60 行一批，带上下文、点名具体变量；分批缓存，重复点不重复花钱
  - 词表里词典没收录的词 → 「AI 问一句」
  - 底部可以追问
  - key 只存在本机（网页放 localStorage，APK 里同样），不上传；关掉 AI 就是纯离线，一个字都不发
- 粘贴即用、自动记住上次那段代码和语言
- **反馈**：⋯ →「反馈 / 提问题」，选类型、写内容，然后自己选出路——复制、发邮件（预填到 `liujiayejiaye@163.com`）、
  或者「去 GitHub 提 issue」（仓库 Issues 开着时才出现）。默认只带版本/平台/识别语言，**代码要手动勾**才附前 30 行
- **检查更新**：启动后静默读 Pages 上的 `version.json`（12 小时最多一次），有新版本时 ⋯ 里会冒出
  「更新到 x.y.z」；PWA 点了刷新换新版，APK 点了跳浏览器下载新包。⋯ 里也常驻「检查更新」可手动点。
  不做静默自动安装——Android 装包必须本人在安装器里点一下

## AI 通道怎么防断（v1.6.0）

以前只有一条 fetch，没有任何超时：手机网络一抖就是「一直转圈不报错」，讲到一半断流就永远停在半句。现在是四层：

1. **三个定时器**：首字节（不思考 25 秒 / 思考档 45 秒）、空闲（30 秒没新字节）、整次（180 秒）。任一超时都会 abort 并给出可读的错因。
2. **重试**：网络错 / 超时 / 5xx / 429 且**还没收到任何正文**时，等 700ms 重发一次；已经出过字的绝不重发（会重复）。
3. **流式空手退回非流式**：SSE 一个字节都没拿到时，自动用 `stream:false` 再发一次（有些网络会把 SSE 掐掉）。
4. **接着讲**：中途断流保留半截文本，第一次自动续写（assistant 前缀 + 「接着上面往下讲」），第二次才把「接着讲」按钮交给人。

另外：SSE 解析兼容 CRLF、`: keep-alive` 心跳、跨 chunk 断行；模型回 JSON 时用 `parseJsonLoose` 兜围栏和多嘴的句子。
单测在 `.tmp/ce-check.mjs`（拦 `window.fetch` 跑，不花钱），超时阈值可以注入 `window.__CE_AI_TUNE`。

## 目录

```
index.html          网页本体（单文件：样式 + 词典 + 分词/解释器 + UI + AI 调用 + 反馈/更新检查）
manifest.json       PWA 清单
sw.js               Service Worker（导航网络优先、资源缓存优先、version.json 直连网络；原生壳里不注册）
version.json        更新检查用的「真值」文件，**CI 生成，不手改**
icons/              PWA 图标
app/                Capacitor 安卓壳（webDir = app/dist，由 scripts/stage.mjs 从根目录拷 4 个文件）
app/android/        原生工程；签名说明见 app/android/app/README-signing.md
.github/workflows/android.yml   推 main 自动构建 APK、写 version.json 并发布
```

## 状态栏与 Android 15+

`targetSdk 36` 之后，Android 15/16 强制 edge-to-edge：状态栏变透明、盖在 WebView 上面。
这时 `@capacitor/status-bar` 的 `setOverlaysWebView(false)` 和 `setBackgroundColor` 在 API > 35
都是空操作（见它的 `shouldSetStatusBarColor()`）；而且**WebView 的 `env(safe-area-inset-*)` 只报
display cutout、从不报系统栏**，无挖孔的机器上恒为 0 —— 顶栏控件会被状态栏压住、点不到。

**给 WebView 加 padding 不管用**（v1.1 踩过）：Chromium 用整个 surface 渲染，padding 不移动内容。
所以现在的做法是「原生推数据、CSS 让位」：

- `MainActivity.java`：API >= 35 时装 `ViewCompat.setOnApplyWindowInsetsListener`，把
  `systemBars | displayCutout` 除以 density 换算成 CSS px，用 `evaluateJavascript` 写成
  `documentElement` 的行内 `--safe-area-top/bottom/left/right`；页面重建后靠 layout 监听 +
  `requestApplyInsets` 补推。**不要装到 `android.R.id.content` 上**，Capacitor Keyboard 已经占了那个 listener。
- `index.html`：`:root` 用 `env(...)` 给这四个变量兜底，`.appbar / .tabbar / .sheet / .toast`
  一律消费 `var(--safe-area-*)`，**不要直接用 `env()`**（那就绕开 Android 那条修法了）。
- 排查：⋯ → 关于 会显示「安全区 26/0」这类读数；是 `0px/0px` 就说明原生没推成功。

参考实现：agaric 的 [PR #4303](https://github.com/jfolcini/agaric/pull/4303)、fusion 用的
[`@capacitor-community/safe-area`](https://github.com/Runfusion/Fusion/commit/91c99dd93efaca9fde5f357b95ffb366fd33ffd9)。

## 改完怎么更新

发版清单（缺一条就会「提示有新版本但装下来还是旧的」）：

1. 改 `index.html` 里的 `APP_VERSION`（例如 `1.2.0` → `1.3.0`）——它是 App 版本、APK 的 versionName、
   以及 CI 写进 `version.json` 的 `version` 的唯一来源。
2. 把 `sw.js` 里的 `CACHE` 加一（`code-explainer-v5` → `v6`），PWA 才会换掉旧缓存。
3. `git push` 到 main：Pages 一两分钟更新；同时 CI 出 `vN` 包，Release 与 `apk/code-explainer.apk`、
   `version.json` 一起更新。手机重装覆盖即可，设置不丢。

`version.json` 由 CI 覆盖生成，**不要手改**；要在真机上看「发现新版本」的提示，可以临时把它推到更高版本，
验完再让 CI 覆盖回来。

## 本地跑

```bash
python -m http.server 8765     # 任意静态服务器都行，http 下 Service Worker 不生效
```

APK 本地构建要 JDK 21 + Android SDK（本机没装，直接用 GitHub Actions 出包）：

```bash
cd app
npm install
npm run sync        # stage + cap sync
npm run apk         # 需要 ANDROID_HOME / JDK 21
```
