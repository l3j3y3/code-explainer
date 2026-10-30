# APK 签名说明

这个 APK 是给自己装的，签名密钥就放在仓库里：`app/android/app/keystore.p12`
（PKCS12，别名 `codeexplainer`，密码 `codeexplainer`，RSA 2048，有效期 50 年）。

## 为什么放仓库里

没有配 GitHub Secret 的情况下，CI 只能从仓库里拿钥匙。好处是**签名固定**：以后每次
更新都能直接覆盖安装，设置里填的 API key 和上次那段代码都不会丢。如果让 CI 每次现生成
一把临时钥匙，每装一个新版本都得先卸载（Android 不允许装签名不同的更新）。

代价写在明面上：仓库是公开的，等于这把签名钥匙也公开，任何人都能签一个「同样签名」的
APK。这个 app 不申请任何敏感权限（只有 `android.permission.INTERNET`），不碰账号、不碰
钱、没有后端、数据都在本机 localStorage，所以风险可以接受。

## 想换成 Secret（更干净）

1. 换一把自己的钥匙：

   ```bash
   keytool -genkeypair -v -keystore release.p12 -storetype PKCS12 \
     -alias codeexplainer -keyalg RSA -keysize 2048 -validity 18250
   ```

2. 仓库 Settings → Secrets and variables → Actions 里加三个：

   | 名字 | 值 |
   | --- | --- |
   | `ANDROID_KEYSTORE_B64` | `base64 -w0 release.p12` 的输出 |
   | `KEYSTORE_PASSWORD` | 上面输入的密码 |
   | `KEY_ALIAS` | `codeexplainer` |

3. 删掉仓库里的 `app/android/app/keystore.p12`。

工作流会**优先用 Secret**（`ANDROID_KEYSTORE_B64` 非空时），没有才回落到仓库里这把。

## 版本号

`versionName` = `1.0.0`（取 GitHub Actions 的 `run_number`），`versionCode` = `run_number`，
每次构建自动加一，所以覆盖安装不会被判成「降级」。
