# 液态玻璃 / Liquid glass

设置入口：**设置 → 外观 → 主题 → 液态玻璃**。默认关闭，选择持久化并即时更新。

## 当前范围

- 书架、浏览（含书源列表）、单个书源和全局搜索的顶部操作栏。
- 同步进度与页头共用玻璃背景，保留原有进度线、文字、计数和动画；浏览、书源和全局搜索也显示实时进度。
- 搜索历史在搜索栏聚焦时展示，有搜索词也能打开。历史面板位于结果之上，保留结果和滚动位置。
- 手机底部导航、平板侧边导航。导航覆盖在页面上，列表保留相应滚动留白；其他主页标签仍预留导航空间。
- 不修改阅读器菜单、普通漫画卡片、章节行或设置面板。

## 实现边界

`presentation-glass` 独立提供 `LocalLiquidGlassEnabled`、`rememberGlassBackdrop`、`glassContent` 和 `glassPanel`。
使用与 EhViewer 相同的 Backdrop 2.0.1；浅色主题使用白色染色和柔和阴影，深色主题使用表面染色和亮边。
系统版本支持由库的效果实现处理，旧系统仍保留染色；折射和模糊的实际可用性取决于系统能力。

应用层的 `GlassScaffold` 仅记录正文，操作栏和历史层是采样源之外的兄弟节点，避免玻璃递归绘制自身。
正文清除 `LocalGlassBackdrop`，仅通过 `LocalGlassPageContent` 调整列表布局，避免正文内的普通操作栏意外透明。
分类和浏览标签保持固定，下面的列表可以从页头与导航后方滚过。
`GlassNavigationScaffold` 关闭时委托原 `NavigationSuiteScaffold`，保留原来的显示和隐藏状态。

新增调用保持 `Fork` 注释，不修改上游 `presentation-core` 或替换 Material 组件定义。

## English

The off-by-default Appearance switch enables glass on the selected library, browsing and global-search toolbars,
sync progress, search-history overlay, phone navigation bar and tablet rail. Search history also opens while editing
a nonempty query and keeps the results mounted underneath. Content and overlay sampling are separated to prevent
recursive rendering; the disabled navigation branch delegates to the original Material scaffold.
