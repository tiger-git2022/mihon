# 液态玻璃 / Liquid glass

设置入口：**设置 → 外观 → 主题 → 液态玻璃**。默认关闭，选择持久化并即时更新。

## 当前范围

- 书架、浏览（含书源列表）、单个书源和全局搜索的顶部操作栏。
- 同步进度与页头共用玻璃背景，保留原有进度线、文字、计数和动画；浏览、书源和全局搜索也显示实时进度。
- 搜索历史在搜索栏聚焦时展示，有搜索词也能打开；收起输入法不会关闭，搜索、点历史或返回才关闭。
  历史面板位于结果之上，保留结果和滚动位置。开启玻璃时历史是一整层铺满页面的玻璃（Backdrop），顶栏此时不另画玻璃，
  两者连成一片，和 EhViewer 展开的搜索一样。
- 浏览页的「图源 / 插件 / 迁移」标签在玻璃开启时放进顶栏的玻璃里，不再单独一条底色。
- 更新、历史的顶部操作栏。
- 手机底部导航、平板侧边导航。所有主页标签（书架、更新、历史、浏览、更多）的列表都从导航后面滚过，保留相应滚动留白。
- 不修改阅读器菜单、普通漫画卡片、章节行或设置面板。

- 漫画详情顶栏：详情滚到顶栏下面后变成玻璃（和 EhViewer 的画廊详情一样），选择章节时也是玻璃。
- 顶栏和底部导航都贴着屏幕边缘（`glassBar`）：没有圆角、阴影和亮边，只在朝向页面的一边画一条细线，
  顶栏是下边，底栏是上边，平板侧边导航是右边。浮动的部分（搜索历史卡片等）仍用 `glassPanel`。

- 从浏览页打开全局搜索时，先给浏览页拍一张静态图（`GlassSnapshot`），在还没有搜索结果时垫在搜索历史的玻璃后面，
  看起来像叠在浏览页上；有结果后改为透出结果。只在玻璃开启时录制和拍图，离开搜索页即释放。

## 实现边界

`presentation-glass` 独立提供 `LocalLiquidGlassEnabled`、`rememberGlassBackdrop`、`glassContent` 和 `glassPanel`。
使用与 EhViewer 相同的 Backdrop 2.0.1，玻璃外观也和 EhViewer 的 `LiquidGlass.kt` 一致：`GlassStyle` 的
Control（小控件）、Panel（操作栏、导航栏）、Sheet（浮动列表、对话框）、Backdrop（整屏）四种，模糊、折射边、
染色透明度（0.2 / 0.2 / 0.4 / 0.4）和阴影数值相同。浅色主题用白色染色和柔和阴影，深色主题用 surfaceContainerHigh
染色和亮边。改动玻璃数值时两边一起改。
系统版本支持由库的效果实现处理，旧系统仍保留染色；折射和模糊的实际可用性取决于系统能力。

应用层的 `GlassScaffold` 仅记录正文，操作栏和历史层是采样源之外的兄弟节点，避免玻璃递归绘制自身。
正文清除 `LocalGlassBackdrop`，仅通过 `LocalGlassPageContent` 调整列表布局，避免正文内的普通操作栏意外透明。
分类标签保持固定，浏览标签属于页头，下面的列表可以从页头与导航后方滚过。
`GlassNavigationScaffold` 关闭时委托原 `NavigationSuiteScaffold`，保留原来的显示和隐藏状态。

新增调用保持 `Fork` 注释，不修改上游 `presentation-core` 或替换 Material 组件定义。

## English

The off-by-default Appearance switch enables glass on the selected library, browsing and global-search toolbars,
sync progress, search-history overlay, phone navigation bar and tablet rail. Search history also opens while editing
a nonempty query and keeps the results mounted underneath. Content and overlay sampling are separated to prevent
recursive rendering; the disabled navigation branch delegates to the original Material scaffold.
