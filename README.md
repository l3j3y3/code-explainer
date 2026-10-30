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
- **AI 通道（可选）**：设置里填自己的 DeepSeek API key 后
  - 整体「AI 详解」——流式生成，比离线规则更懂语义
  - 逐行「AI 补全没讲的行」——只补离线词典讲不了的
  - 词表里词典没收录的词 → 「AI 问一句」
  - 底部可以追问
  - key 只存在本机（网页放 localStorage，APK 里同样），不上传；关掉 AI 就是纯离线，一个字都不发
- 粘贴即用、自动记住上次那段代码和语言

## 目录

```
index.html          网页本体（单文件：样式 + 词典 + 分词/解释器 + UI + AI 调用）
manifest.json       PWA 清单
sw.js               Service Worker（导航网络优先、资源缓存优先；原生壳里不注册）
icons/              PWA 图标
app/                Capacitor 安卓壳（webDir = app/dist，由 scripts/stage.mjs 从根目录拷 4 个文件）
app/android/        原生工程；签名说明见 app/android/app/README-signing.md
.github/workflows/android.yml   推 main 自动构建 APK 并发布
```

## 状态栏与 Android 15+

`targetSdk 36` 之后，Android 15/16 强制 edge-to-edge：状态栏变透明、盖在 WebView 上面。
这时 `@capacitor/status-bar` 的 `setOverlaysWebView(false)` 和 `setBackgroundColor` 在 API > 35
都是空操作（见它的 `shouldSetStatusBarColor()`），WebView 里 `env(safe-area-inset-top)` 也是 0
——顶栏控件会被状态栏压住、点不到。

修法在 `app/android/app/src/main/java/com/l3j3y3/codeexplainer/MainActivity.java`：
API >= 35 时给 WebView 装 `ViewCompat.setOnApplyWindowInsetsListener`，把
`systemBars | displayCutout` 的尺寸作为 padding 让出来；**不要装到 `android.R.id.content` 上**，
Capacitor Keyboard 插件已经占了那个 listener。Web 侧在 `body.is-native` 时不再叠 `env()`。

## 改完怎么更新

- **只改网页**：改 `index.html` → 把 `sw.js` 里的 `CACHE` 版本号加一 → `git push`，Pages 一两分钟更新。
- **连 APK 一起更新**：动一下 `app/**` 里任意文件（或去 Actions 页手动 `Run workflow`）→ CI 出 `vN` 包，
  Release 与 `apk/code-explainer.apk` 同时更新；手机重装覆盖即可，设置不丢。

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
