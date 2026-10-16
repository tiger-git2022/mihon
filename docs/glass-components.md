# 液态玻璃基础 / Liquid glass foundation

设置入口：**设置 → 外观 → 主题 → 液态玻璃**。默认关闭，切换后立即更新 Compose 状态，重启后保留选择。
本次只接入依赖和基础设施，还没有玻璃控件；开启后当前界面暂不变化，设置说明也明确提示这一点。

## 隔离方式

- `presentation-glass` 是独立的 Fork 模块，使用与 EhViewer 相同的 `io.github.kyant0:backdrop:2.0.1`。
- `GlassPreferences` 使用独立的 `tiger_liquid_glass` 偏好键，通过现有 `PreferenceStore` 持久化，不修改上游 `UiPreferences`。
- `ProvideLiquidGlass` 在应用主题入口观察偏好，通过 `LiquidGlassTheme` 提供 `LocalLiquidGlassEnabled`。
- `LiquidGlassTheme` 只提供状态，不修改 Material 主题、颜色或现有组件。没有 provider 时默认关闭；`TachiyomiPreviewTheme` 也保持默认关闭。
- `rememberGlassBackdrop(background)` 为页面创建背景采样源，关闭时返回 `null`，不分配采样图层。
  `Modifier.glassContent(null)` 直接返回原 Modifier。当前没有页面调用这些采样接口，因此开关开启时也不会额外录制页面。

## 后续接入组件

1. 将具体玻璃组件放进 `presentation-glass`，保留独立名称，不覆盖或修改上游同名组件。
2. 在需要替换的位置读取 `LocalLiquidGlassEnabled.current`。关闭时走完整的原 Material 组件分支，保留原颜色、阴影和交互。
3. 页面调用 `rememberGlassBackdrop(MaterialTheme.colorScheme.background)`，用 `Modifier.glassContent(backdrop)` 录制位于控件后方的内容，再把 backdrop 显式传给玻璃组件。
   不把玻璃控件本身放进被采样的区域，避免递归采样；不同页面或窗口分别管理采样源。
4. 具体组件实现时再处理系统版本支持、明暗主题、纯黑主题、无障碍和性能回退，不在本阶段承诺所有设备都支持折射。
5. 必须改动上游文件的接入点集中保留 `Fork` 注释，不把玻璃依赖加入上游 `presentation-core`。

## English

Settings → Appearance → Theme → Liquid glass is off by default, persisted, and observed live by the app theme.
This foundation contains no glass controls and currently makes no visual changes, even when enabled.
The standalone `presentation-glass` module exposes the opt-in composition local and page-scoped Backdrop helpers;
upstream Material components and themes remain intact. Future controls must retain their original Material fallback
when disabled, record only the content behind them, and handle platform capabilities when implemented.
