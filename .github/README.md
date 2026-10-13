# Mihon Tiger（tiger-git2022/mihon）

<p align="center">
  <a href="https://github.com/tiger-git2022/mihon/releases/latest"><img src="https://img.shields.io/github/v/release/tiger-git2022/mihon?style=for-the-badge&label=%E4%B8%8B%E8%BD%BD%E6%9C%80%E6%96%B0%E7%89%88%20%2F%20Download&color=2ea44f" alt="下载最新版 / Download latest"></a>
</p>

**[⬇ 下载最新 APK / Download the latest APK](https://github.com/tiger-git2022/mihon/releases/latest)**：大多数手机选 `mihon-tiger-arm64-v8a-…apk`，不确定就选通用版 `mihon-tiger-v…apk`。/ Most phones: `mihon-tiger-arm64-v8a-…apk`; if unsure, the universal `mihon-tiger-v…apk`.

[中文](#中文) | [English](#english) | [上游 Mihon 说明 / Upstream README](../README.md)

## 中文

基于 [mihonapp/mihon](https://github.com/mihonapp/mihon) 的个人分支，`custom` 分支在上游基础上增加：

- **OneDrive 同步**（设置 → 数据和存储 → 书架同步；书架页工具栏的同步按钮）。一次同步包括：
  - 书架的添加和移除；
  - 全局搜索记录（标注来自哪台设备）；
  - “看过”标记；
  - 本机设置备份（每台设备一份，可在“从 OneDrive 恢复设置”里选择任一设备的备份恢复；不含书架、历史和登录信息）。
  - 自动同步（默认开启，可关闭）：打开应用时同步一次；按间隔定时同步（15 分钟到 24 小时可选，默认 6 小时）；书架、搜索记录、看过标记或设置改动后，60 秒内没有新改动就同步，最多等 5 分钟；可选“仅在 Wi-Fi 下自动同步”。这些选项只保存在本机，恢复别的设备的设置时不会被覆盖。
- **全局搜索记录**：双栏显示，长关键词自动换行。
- **“看过”标记**：漫画详情页的“标记看过”按钮，和书架互不影响（移出书架后仍保留）。浏览书源和全局搜索结果的封面上用眼睛角标显示。E-Hentai 系画廊按画廊 ID 匹配，E-Hentai 和 ExHentai 通用。
- **EhViewer 收藏标记**：配合 [EhViewer 改版](https://github.com/tiger-git2022/ehviewer-onedrive)，两边登录同一个微软账号即可。同步时读取 EhViewer 上传的收藏清单（`ehviewer/export/favorites-index.json.gz`），在浏览书源、全局搜索的封面上显示“本地”或“云:收藏夹名”角标，详情页多一个按钮，点开能看到匹配到的 EhViewer 画廊，并可在 EhViewer 中打开。E-Hentai 系书源按画廊 ID 匹配；其他书源按标题匹配，会忽略各种括号和其中的作者、语言、汉化组等标签（汉化组写在最前面也能认出作者），统一全角半角、繁简（Android 10 及以上）和日文字形（桜/樱、気/气），“催○”这类和谐字当作任意字符，日文标题和罗马音标题都参与比较。卷数不同、同系列只差一个汉字（宮島桜の場合/宮島椿の場合）的不算同一本；作者相同时，只写了主标题（哔咔常见）也能对上。可在书架同步设置里关闭。

`main` 分支与上游保持一致，不在上面开发。

### 下载

<https://github.com/tiger-git2022/mihon/releases/latest>

- 大多数手机：`mihon-tiger-arm64-v8a-v<版本>.apk`
- 不确定：通用版 `mihon-tiger-v<版本>.apk`

release 包的包名是 `app.mihon.tiger`，应用名 “Mihon Tiger”，可以和官方 Mihon（`app.mihon`）同时安装。
应用启动时自动检查本仓库的新版本，“关于”页可以手动检查。

### 版本号

| | 规则 | 例子 |
|---|---|---|
| tag / versionName | `v<上游 versionName>-t<N>` | `v0.21.2-t3` |
| versionCode | `<上游 versionCode> × 1000 + N` | `38003` |

N 对每个上游版本从 1 开始，每次发布加 1，所以 versionCode 一定递增。
`app/build.gradle.kts` 里上游的 `versionCode`/`versionName` 两行不要改，构建号由 CI 通过 `-Ptiger.buildNumber=N` 传入。
应用内的版本比较见 `domain/src/main/java/tachiyomi/domain/release/interactor/ForkVersion.kt`。

### GitHub Actions

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

### OneDrive 登录（MSAL）

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

### 合并上游时的注意事项

定制代码尽量放在独立文件里；必须改上游文件的地方都尽量集中在一处，并标了 `Fork` 注释：

- 独立文件：`eu/kanade/tachiyomi/data/sync/*`、`LibrarySyncSettings.kt`、`LibrarySyncActionState.kt`、
  `GlobalSearchHistoryPreferences.kt`、`GlobalSearchHistoryContent.kt`、`SearchHistory.kt`、`ForkVersion.kt`、
  `MangaMarkBadges.kt`、`ReadMarkActionButton.kt`、`EhFavoriteBadges.kt`、`EhFavoriteActionButton.kt`、
  `i18n/.../strings_tiger.xml`、`app/src/release/res/values/strings_tiger.xml`、`app/proguard-tiger.pro`、
  `.github/workflows/tiger-*.yml`
- 改动的上游文件（每处只有几行）：`app/build.gradle.kts`（文件末尾的 `region Fork` 块和 msal 依赖）、
  `settings.gradle.kts`（末尾的 `region Fork` 块）、`gradle/build-logic/build.gradle.kts`、`gradle/libs.versions.toml`、
  `AndroidManifest.xml`、`AppGraph.kt`、`SettingsDataScreen.kt`、`LibraryTab.kt`、`LibraryToolbar.kt`、`AppBar.kt`、
  `GlobalSearchScreen.kt`（两处）、`GlobalSearchViewModel.kt`、`SearchViewModel.kt`、`AppUpdateChecker.kt`、
  `GetApplicationRelease.kt`、`MangaInfoHeader.kt`（`MangaActionRow` 的 `extraActions` 参数）、`MangaScreen.kt`、
  `BrowseSourceCompactGrid.kt`、`BrowseSourceComfortableGrid.kt`、`BrowseSourceList.kt`、`GlobalSearchCardRow.kt`（角标）

手动合并：

```sh
git fetch https://github.com/mihonapp/mihon.git tag v0.21.2 --no-tags
git checkout custom
git merge --no-ff v0.21.2
# 上游的工作流文件不要合进来
git rm -r -q -f --cached --ignore-unmatch -- .github/workflows && rm -rf .github/workflows && git checkout HEAD -- .github/workflows
```

### 本地构建

为了不让 OneDrive 的占位文件和同步锁弄坏 Gradle 增量编译，本地构建输出放在 `~/.gradle/project-builds/mihon`
（`settings.gradle.kts` 和 `gradle/build-logic/build.gradle.kts` 末尾）。CI（有 `CI` 环境变量）上自动关闭，
也可以用 `-Ptiger.localBuildDir=false` 关闭。

## English

Personal fork of [mihonapp/mihon](https://github.com/mihonapp/mihon). The `custom` branch adds:

- **OneDrive sync** (Settings → Data and storage → library sync; sync button in the library toolbar). One sync covers:
  - library additions and removals;
  - global search history (labelled with the device it came from);
  - "read before" marks;
  - a settings backup per device (restore any device's backup from "Restore settings from OneDrive"; library, history and logins are not included).
  - automatic sync (on by default): once at app start, periodically (15 min to 24 h, default 6 h), and 60 s after the last change to the library, search history, marks or settings (at most 5 min after the first); optionally only on Wi-Fi. These options stay on the device and are not changed by restoring another device's settings.
- **Global search history** in two columns; long queries wrap.
- **"Read before" mark**: a button on the manga details page, independent of the library (kept after removing from the library). Shown as an eye badge on covers in Browse and global search. E-Hentai galleries match by gallery ID, so E-Hentai and ExHentai share marks.
- **EhViewer favorite marks**: with the [EhViewer fork](https://github.com/tiger-git2022/ehviewer-onedrive) signed in to the same Microsoft account, a sync reads the favorites list EhViewer uploads (`ehviewer/export/favorites-index.json.gz`). Covers in Browse and global search get a "Local" or "Cloud: slot name" badge, and the details page gets a button listing the matching EhViewer galleries, which open in EhViewer. E-Hentai sources match by gallery ID; other sources match by title, ignoring brackets and the artist, language and translator tags in them (also with the translator group first), full-width/half-width, traditional/simplified Chinese (Android 10+) and Japanese character forms (桜/樱), with censor marks like "催○" matching any character; Japanese and romanized titles both count. Different volume numbers or one different kanji (another book of a series) do not match; with the same artist a title shortened to its main part does. Can be turned off in the library sync settings.

`main` mirrors upstream and is not developed on.

### Download

<https://github.com/tiger-git2022/mihon/releases/latest>. Release builds use package `app.mihon.tiger` and the name "Mihon Tiger", so they install beside the official Mihon (`app.mihon`). The app checks this repository for updates on launch; you can also check from the About page.

### Versions

Tag / versionName `v<upstream versionName>-t<N>` (for example `v0.21.2-t3`); versionCode `<upstream versionCode> × 1000 + N`. N starts at 1 for each upstream version and increases with every release.

### GitHub Actions

- `tiger-ci.yml`: unit tests, release build (debug signing) and format check for pull requests into `custom`.
- `tiger-release.yml`: builds signed APKs and publishes a release on every push to `custom`; each commit is released once (a manual run can force it). Release notes are Chinese first, then English.
- `tiger-sync-upstream.yml`: every 6 hours merges the latest upstream release tag into `custom`; pushes and releases when it merges cleanly and builds, otherwise opens an issue.

Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (release signing), and optionally `SYNC_TOKEN` (fine-grained PAT with Contents and Workflows write access, needed when upstream commits touch workflow files).

### OneDrive sign-in (MSAL)

Each package needs its redirect URI `msauth://<package>/<URL-encoded signature hash>` registered under the Android platform of the Azure app registration (client id `77f7046c-0a22-44b8-aa16-8b8416628c6c`). The release signature hash is in every release's "签名信息 / Signing info" section. The app builds the redirect URI at runtime from its actual package and signing certificate, so changing either only needs a new registration in Azure.

See the Chinese section above for the list of upstream files touched by the fork and the manual merge steps.
