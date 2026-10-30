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
  - 自动同步（默认开启，可关闭）：打开应用时、登录或切换微软账号后各同步一次；按间隔定时同步（15 分钟到 24 小时可选，默认 6 小时）；书架、搜索记录、看过标记或设置改动后，60 秒内没有新改动就同步，最多等 5 分钟；可选“仅在 Wi-Fi 下自动同步”。这些选项只保存在本机，恢复别的设备的设置时不会被覆盖。
  - 同步时显示每一步的进度（下载云端、比对、上传、写入本机，能数的带上数量）。进度条有一道流光扫过已完成的部分，右上角的圆圈隔几秒淡淡地重画一次（系统关闭动画时不动）。
- **章节大小**：漫画详情页的章节行显示已下载章节在本机的大小（书源不提供未下载章节的大小）。
- **液态玻璃**：设置 → 外观 → 主题中的开关默认关闭。开启后，书架、浏览、书源和全局搜索顶部栏、搜索历史、同步进度、手机底部导航及平板侧栏使用玻璃效果；关闭时保留原界面。开启后同一页下方可以用滑块调顶栏、底栏和整屏玻璃的模糊度与不透明度，调完立即生效，也能一键恢复默认。见 [接入说明](../docs/glass-components.md)。
- **网站页面预览**：绅士漫画和 HentaiHand 有网站逐页小图时，漫画详情的章节下方显示前 20 页预览网格（可选 2–6 列），点图跳到那一页，「查看全部」打开分页网格；其他书源没有独立小图时不显示，不下载原图伪装缩略图。见 [支持范围](../docs/website-page-previews.md)。
- **封面和标题**：书架的显示设置里可选标题行数（2、3、4、6 行或完整标题，默认完整）。浏览书源时，显示方式菜单里的「封面和标题」可设每行几本（默认跟书架一样）、封面高度（矮的封面会裁掉上下）、标题行数和列表样式（默认是像 EhViewer 那样的大行，标题在封面旁边）。全局搜索右上角新加的网格按钮打开同样的设置，可选宽卡片（默认，标题在封面旁边）或竖卡片，以及卡片宽度和竖卡片的标题行数。改动在下方页面上立刻生效。
- **顶栏**：同步进度紧贴搜索栏下方；「热门/最近更新/筛选」和「已置顶/全部/有结果」放在它下面，往下滑时收起，回到页面顶部才重新出现；浏览页（图源/插件/迁移）只用右上角的同步圆圈表示正在同步，不再多一行进度。全局搜索同步时只显示同步进度条，搜索进度看各书源那一行的转圈。
- **移出书架**：漫画详情页点「已在书架」直接移出，不再弹确认框（有下载时仍会提示是否删除下载）。
- **全局搜索记录**：双栏显示，长关键词自动换行。有搜索词时点击搜索栏也能打开历史，收起后保留原结果和滚动位置。
- **全局搜索换写法重试**：某个书源搜不到结果时，按 EhViewer 收藏清单里同一本书的罗马音标题和日文标题，自动换成另一种写法再搜一次（作者、社团或标题，比如「チビゴン」↔「Chibigon」；清单里没有的纯假名名字按规则转成罗马音，汉字名字做不到），书源名旁注明「用「Chibigon」搜到」，点进书源也用这个写法。
- **“看过”标记**：漫画详情页的“标记看过”按钮，和书架互不影响（移出书架后仍保留）。浏览书源和全局搜索结果的封面上用眼睛角标显示。E-Hentai 系画廊按画廊 ID 匹配，E-Hentai 和 ExHentai 通用。
- **EhViewer 收藏标记**：配合 [EhViewer 改版](https://github.com/tiger-git2022/ehviewer-onedrive)，两边登录同一个微软账号即可。同步时读取 EhViewer 上传的收藏清单（`ehviewer/export/favorites/`：一个 `manifest.json` 列出按画廊 ID 分段的分片 `shard-<哈希>.json.gz`，分片内容不变则哈希不变；只下载、只重新索引变了的分片，清单没变就跳过；本地缓存在应用数据的 `tiger/ehviewer-favorites/`），在浏览书源、全局搜索的封面上显示“本地”或“云:收藏夹名”角标，详情页多一个按钮，点开能看到匹配到的 EhViewer 画廊，并可在 EhViewer 中打开。E-Hentai 系书源按画廊 ID 匹配；其他书源按标题匹配，会忽略各种括号和其中的作者、语言、汉化组等标签（汉化组写在最前面也能认出作者），统一全角半角、繁简（Android 10 及以上）和日文字形（桜/樱、気/气），“催○”这类和谐字当作任意字符，日文标题和罗马音标题都参与比较。卷数不同、同系列只差一个汉字（宮島桜の場合/宮島椿の場合）的不算同一本；作者相同时，只写了主标题（哔咔常见）也能对上。可在书架同步设置里关闭。

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
| tag / versionName | `v<上游 versionName>-t<N>`，N 补足三位 | `v0.21.2-t003` |
| versionCode | `<上游 versionCode> × 1000 + N` | `38003` |

N 对每个上游版本从 1 开始，每次发布加 1，所以 versionCode 一定递增。N 补足三位，是因为 GitHub 同一天的发布按标签逐字排序，不补的话 t9 会排在 t12 上面；之前的旧标签（如 `v0.21.2-t36`）没补，程序两种都认。
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
  - automatic sync (on by default): once at app start and after signing in or switching accounts, periodically (15 min to 24 h, default 6 h), and 60 s after the last change to the library, search history, marks or settings (at most 5 min after the first); optionally only on Wi-Fi. These options stay on the device and are not changed by restoring another device's settings.
  - each step of a sync shows its progress (downloading, comparing, uploading, updating this device, with counts where known); a light sweeps over the done part of the bar and the ring is softly redrawn every few seconds; neither moves with system animations off.
- **Chapter sizes**: chapters downloaded on this device show their size in the chapter list of a manga (sources give none for chapters not downloaded).
- **Liquid glass**: an off-by-default Appearance switch enables glass on the selected library, browsing and search toolbars, history, sync progress and phone/tablet navigation; sliders below it set the blur and opacity of the bars and of full-screen glass, applied at once. See the [integration notes](../docs/glass-components.md).
- **Website page previews**: WNACG server thumbnails are shown in a paginated grid from the manga menu, with tap-to-read navigation. Sources without an established thumbnail interface are skipped; original images are never fetched to manufacture previews. See [support notes](../docs/website-page-previews.md).
- **Covers and titles**: title lines for the library in its display settings (2, 3, 4, 6 or whole, whole by default). When browsing a source, "Covers and titles" in the display mode menu sets covers per row (as in the library by default), cover height (shorter covers are cropped), title lines and the list style (large rows with the title beside the cover, like EhViewer, by default). A grid button at the top of global search opens the same settings for wide cards (default) or tall cards, the card width and the title lines of tall cards. Changes show at once on the page below.
- **Top bars**: sync progress sits right below the search field, the Popular/Latest/Filter and Pinned/All/Has results chips below it, sliding away while the page scrolls down and back only at the top of the page; Browse (Sources/Extensions/Migrate) shows only its sync circle, without the progress row. While a sync runs, global search shows only the sync bar; each source row still spins until it is searched.
- **Removing from the library**: the "In library" button on the details removes at once, without a confirmation (a snackbar still offers to delete downloads).
- **Global search history** in two columns; long queries wrap. Focusing a nonempty search also opens history, while preserving the results and scroll position underneath.
- **Global search tries the other spelling**: a source that finds nothing is searched again with the other spelling of the artist, circle or title, learned from the default and Japanese titles in the EhViewer favorites ("チビゴン" ↔ "Chibigon"), else the romanized kana of the name. The source shows "Found as …", and opening it keeps that spelling.
- **"Read before" mark**: a button on the manga details page, independent of the library (kept after removing from the library). Shown as an eye badge on covers in Browse and global search. E-Hentai galleries match by gallery ID, so E-Hentai and ExHentai share marks.
- **EhViewer favorite marks**: with the [EhViewer fork](https://github.com/tiger-git2022/ehviewer-onedrive) signed in to the same Microsoft account, a sync reads the favorites list EhViewer uploads (`ehviewer/export/favorites/`: a `manifest.json` listing shards by gallery ID range, `shard-<hash>.json.gz`, whose hash changes only with their contents; only changed shards are downloaded and indexed again, and an unchanged manifest is skipped; cached in `tiger/ehviewer-favorites/` of the app data). Covers in Browse and global search get a "Local" or "Cloud: slot name" badge, and the details page gets a button listing the matching EhViewer galleries, which open in EhViewer. E-Hentai sources match by gallery ID; other sources match by title, ignoring brackets and the artist, language and translator tags in them (also with the translator group first), full-width/half-width, traditional/simplified Chinese (Android 10+) and Japanese character forms (桜/樱), with censor marks like "催○" matching any character; Japanese and romanized titles both count. Different volume numbers or one different kanji (another book of a series) do not match; with the same artist a title shortened to its main part does. Can be turned off in the library sync settings.

`main` mirrors upstream and is not developed on.

### Download

<https://github.com/tiger-git2022/mihon/releases/latest>. Release builds use package `app.mihon.tiger` and the name "Mihon Tiger", so they install beside the official Mihon (`app.mihon`). The app checks this repository for updates on launch; you can also check from the About page.

### Versions

Tag / versionName `v<upstream versionName>-t<N>` with N padded to three digits (for example `v0.21.2-t003`, so the releases page lists releases of the same day in order; older tags such as `v0.21.2-t36` are unpadded); versionCode `<upstream versionCode> × 1000 + N`. N starts at 1 for each upstream version and increases with every release.

### GitHub Actions

- `tiger-ci.yml`: unit tests, release build (debug signing) and format check for pull requests into `custom`.
- `tiger-release.yml`: builds signed APKs and publishes a release on every push to `custom`; each commit is released once (a manual run can force it). Release notes are Chinese first, then English.
- `tiger-sync-upstream.yml`: every 6 hours merges the latest upstream release tag into `custom`; pushes and releases when it merges cleanly and builds, otherwise opens an issue.

Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (release signing), and optionally `SYNC_TOKEN` (fine-grained PAT with Contents and Workflows write access, needed when upstream commits touch workflow files).

### OneDrive sign-in (MSAL)

Each package needs its redirect URI `msauth://<package>/<URL-encoded signature hash>` registered under the Android platform of the Azure app registration (client id `77f7046c-0a22-44b8-aa16-8b8416628c6c`). The release signature hash is in every release's "签名信息 / Signing info" section. The app builds the redirect URI at runtime from its actual package and signing certificate, so changing either only needs a new registration in Azure.

See the Chinese section above for the list of upstream files touched by the fork and the manual merge steps.
