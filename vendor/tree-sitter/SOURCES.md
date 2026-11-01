# vendor/tree-sitter

代码解释器用的语法分析器。这里的东西**不在仓库里手改**，改就是换版本，换版本就重算下面的 sha256。

取法（不要用 `tree-sitter-wasms` 那个 51MB 全家桶，版本老、体积大）：

```sh
npm pack web-tree-sitter@0.27.1
npm pack tree-sitter-c@0.24.1
npm pack tree-sitter-cpp@0.23.4
npm pack tree-sitter-java@0.23.5
npm pack tree-sitter-javascript@0.25.0
npm pack tree-sitter-python@0.25.0
npm pack tree-sitter-css@0.25.0
npm pack tree-sitter-html@0.23.2
```

解开 tarball，`web-tree-sitter` 的 js/wasm 在 `package/` 根，各语言包的 `.wasm` 也在 `package/` 根，直接拷过来改名。

**ABI 兼容**：下面这套组合实测可用（2026-11-01 跑过探针：七种语言都能解析、都能在错误位置报出 `ERROR`/`isMissing`）。
换 `web-tree-sitter` 大版本前先跑一遍探针，不兼容就别换。

| 文件 | 大小 | 来源 | sha256 |
|---|---|---|---|
| `web-tree-sitter.js` | 156,132 | web-tree-sitter@0.27.1 | `7c49e3c1d87e24e0bb4c2def909d17154dfde281f5f8280225450090bb4b8110` |
| `web-tree-sitter.wasm` | 210,415 | web-tree-sitter@0.27.1 | `beca80e44cccbf2299ed4d1e3fa6462a9a6fe818aeebf847547a392227ce4acc` |
| `tree-sitter-c.wasm` | 625,918 | tree-sitter-c@0.24.1 | `c852c2a85ebf2beb636aa3b0ef7f7e70458684d74f6741b20dcb296885bed9f9` |
| `tree-sitter-cpp.wasm` | 3,434,931 | tree-sitter-cpp@0.23.4 | `174eb0deb75b2ec7881bcacda9f995648d8e683956e5c2267e69ab6dc503fcbf` |
| `tree-sitter-css.wasm` | 128,668 | tree-sitter-css@0.25.0 | `8a23977fe271357cce6f254ef88c9bebf3854602d8046605aef6a45c02135c59` |
| `tree-sitter-html.wasm` | 18,385 | tree-sitter-html@0.23.2 | `c48fcd82c7ea8bf943180088ba7f28c48b2bb5287874179168bf9d31e394cf85` |
| `tree-sitter-java.wasm` | 414,641 | tree-sitter-java@0.23.5 | `4fdeac4ca6ca089f06c6f7e562abcac1733cd465728cc7031ebb73c2019122c4` |
| `tree-sitter-javascript.wasm` | 411,770 | tree-sitter-javascript@0.25.0 | `5fb488d0cabb4775a594bab85682de5ad6ce83c0d6ac997a9f82dd084d571240` |
| `tree-sitter-python.wasm` | 457,883 | tree-sitter-python@0.25.0 | `16108b50df4ee9a30168794252ab55e7c93bfc5765d7fa0aa3e335752c515f47` |

合计 5,858,743 字节（5.59 MB）。C++ 一个占了 3.27MB，是 C++ 语法本身复杂，绕不开。

许可：tree-sitter 本体 MIT；各语言语法包多数 MIT（tree-sitter-cpp 是 MIT，tree-sitter-python 是 MIT，tree-sitter-java 是 MIT，具体见各自包里的 LICENSE）。
