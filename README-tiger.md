# Mihon Tiger（tiger-git2022/mihon）

基于 [mihonapp/mihon](https://github.com/mihonapp/mihon) 的个人分支，`custom` 分支在上游基础上增加：

- **OneDrive 书架同步**：设置 → 数据和存储 → 书架同步；书架页工具栏的同步按钮。
- **全局搜索历史**。

`main` 分支与上游保持一致，不在上面开发。

## 下载

<https://github.com/tiger-git2022/mihon/releases/latest>

- 大多数手机：`mihon-tiger-arm64-v8a-v<版本>.apk`
- 不确定：通用版 `mihon-tiger-v<版本>.apk`

release 包的包名是 `app.mihon.tiger`，应用名 “Mihon Tiger”，可以和官方 Mihon（`app.mihon`）同时安装。
应用启动时自动检查本仓库的新版本，“关于”页可以手动检查。

## 版本号

| | 规则 | 例子 |
|---|---|---|
| tag / versionName | `v<上游 versionName>-t<N>` | `v0.21.2-t3` |
| versionCode | `<上游 versionCode> × 1000 + N` | `38003` |

N 对每个上游版本从 1 开始，每次发布加 1，所以 versionCode 一定递增。
`app/build.gradle.kts` 里上游的 `versionCode`/`versionName` 两行不要改，构建号由 CI 通过 `-Ptiger.buildNumber=N` 传入。
应用内的版本比较见 `domain/src/main/java/tachiyomi/domain/release/interactor/ForkVersion.kt`。

## GitHub Actions

| 工作流 | 触发 | 作用 |
|---|---|---|
| `tiger-ci.yml` | 合并到 `custom` 的 PR、手动 | 单元测试 + 编译 release（debug 签名）+ 代码格式检查 |
| `tiger-release.yml` | 推送到 `custom`、手动、被同步工作流调用 | 编译签名 APK，发布到 Releases。同一个提交只发布一次（手动运行可勾选 force） |
| `tiger-sync-upstream.yml` | 每 6 小时、手动 | 把上游最新 Release 的 tag 合并进 `custom`；无冲突且编译、测试通过就推送并发布，否则开 Issue |

上游原有的 `build.yml`、`release.yml`、`update_website.yml` 依赖上游专有的 secrets，已在 `custom` 中删除。
同步工作流合并时会自动丢弃上游对 `.github/workflows` 的改动。

需要的仓库 Secrets：

- `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`：release 签名。
- `SYNC_TOKEN`（可选）：有 Contents 和 Workflows 写权限的 fine-grained PAT。上游提交改过工作流文件时，
  GitHub 不允许用 `GITHUB_TOKEN` 推送这些提交；没有这个 secret 时，同步会失败并开 Issue 说明。

Firebase / 遥测保持关闭（CI 传 `-Pinclude-telemetry=false`，仓库里没有 `google-services.json`）。

## OneDrive 登录（MSAL）

Azure 应用注册（client id `77f7046c-0a22-44b8-aa16-8b8416628c6c`）→ Authentication → Android 平台里，
每种安装包都要登记各自的重定向 URI：`msauth://<包名>/<URL 编码的签名哈希>`。

| 安装包 | 包名 | 签名哈希 |
|---|---|---|
| debug | `app.mihon.dev` | `HrLgSH3tTFq1KFxzXlgszundt4s=`（本地 debug keystore） |
| release | `app.mihon.tiger` | 见每个 Release 说明里的“签名信息”，或 Actions 运行摘要 |

Azure 门户添加 Android 平台时填“包名”和“签名哈希”（未编码的 Base64），它会自己生成重定向 URI。

应用在运行时根据实际的包名和签名证书生成重定向 URI（`OneDriveSyncClient.redirectUri`），
`AndroidManifest.xml` 里的 `BrowserTabActivity` 用 `${applicationId}` 作为 host、接受任意 path，
所以换签名或换包名不需要改代码，只需要在 Azure 里登记。未登录时，书架同步设置页底部会显示本安装包的重定向 URI。

## 合并上游时的注意事项

定制代码尽量放在独立文件里；必须改上游文件的地方都尽量集中在一处，并标了 `Fork` 注释：

- 独立文件：`eu/kanade/tachiyomi/data/sync/*`、`LibrarySyncSettings.kt`、`LibrarySyncActionState.kt`、
  `GlobalSearchHistoryPreferences.kt`、`GlobalSearchHistoryContent.kt`、`SearchHistory.kt`、`ForkVersion.kt`、
  `i18n/.../strings_tiger.xml`、`app/src/release/res/values/strings_tiger.xml`、`app/proguard-tiger.pro`、
  `.github/workflows/tiger-*.yml`
- 改动的上游文件（每处只有几行）：`app/build.gradle.kts`（文件末尾的 `region Fork` 块和 msal 依赖）、
  `settings.gradle.kts`（末尾的 `region Fork` 块）、`gradle/build-logic/build.gradle.kts`、`gradle/libs.versions.toml`、
  `AndroidManifest.xml`、`AppGraph.kt`、`SettingsDataScreen.kt`、`LibraryTab.kt`、`LibraryToolbar.kt`、`AppBar.kt`、
  `GlobalSearchScreen.kt`（两处）、`GlobalSearchViewModel.kt`、`SearchViewModel.kt`、`AppUpdateChecker.kt`、
  `GetApplicationRelease.kt`

手动合并：

```sh
git fetch https://github.com/mihonapp/mihon.git tag v0.21.2 --no-tags
git checkout custom
git merge --no-ff v0.21.2
# 上游的工作流文件不要合进来
git rm -r -q -f --cached --ignore-unmatch -- .github/workflows && rm -rf .github/workflows && git checkout HEAD -- .github/workflows
```

## 本地构建

为了不让 OneDrive 的占位文件和同步锁弄坏 Gradle 增量编译，本地构建输出放在 `~/.gradle/project-builds/mihon`
（`settings.gradle.kts` 和 `gradle/build-logic/build.gradle.kts` 末尾）。CI（有 `CI` 环境变量）上自动关闭，
也可以用 `-Ptiger.localBuildDir=false` 关闭。
