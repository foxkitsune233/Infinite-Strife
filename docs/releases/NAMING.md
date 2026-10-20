# Infinite Strife 命名约定

- 游戏显示名：`Infinite Strife`。
- GitHub仓库与Gradle根工程名：`Infinite-Strife`。
- GitHub远程：`git@github.com:foxkitsune233/Infinite-Strife.git`，本地远程名为 `github`。
- 新分发APK：`Infinite-Strife-<versionName>-<buildIndex>.apk`，例如 `Infinite-Strife-0.1.5-XXXX-XXXX-XXXX.apk`。
- 发包仍使用 `app/version.properties` 作为版本唯一来源；小更新保留基础版本，生成三组四位十六进制索引并递增versionCode。登记在 `MODIFICATION_INDEX.md`。
- Android applicationId/namespace、Kotlin包名保留 `com.example.douqiuqiu`，避免应用变成另一个安装身份、无法覆盖旧包及失去原存档访问。
- 既有 `DouQiuQiu-*.apk`、历史日志与测试文档路径保留原名，不重命名历史二进制冒充新构建。
- 当前工作目录仍为 `D:/android_project/DouQiuQiu`。后续新克隆目录推荐 `Infinite-Strife`；本次不搬迁已打开的工程或修改历史脚本绝对路径。
- 新脚本应以脚本位置或Git根目录定位工程，不硬编码开发者机器上的绝对路径。

## 仓库操作

在工程根目录执行 `git remote -v` 核对远程。后续提交/推送由仓库维护者执行；本次命名整理不自动提交或推送。

源码不携带的美术资源说明见 `../ART-ASSETS.md`。构建缓存、APK、本地配置及生成原图按 `.gitignore` 处理。

本次只修改源码与命名规范，没有生成新安装包。已分发的1014包仍显示原应用名；下一次按新前缀构建时显示新名称。
