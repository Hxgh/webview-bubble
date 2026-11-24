# webshell-android

轻量级高性能 Android 原生网页套壳应用，深度对齐 Android 现代 Edge-to-Edge 与系统多端安全高度（WindowInsets）规范。面向通用网站快速编译打包，支持自定义目标网站与自适应桌面图标。

## 架构特性

- **纯 Android 原生层叠视图架构（无 iframe 嵌套）**：
  - **顶层原生加载**：接入网站作为顶层页面直接在原生 `WebView` 中加载，不受任何 `X-Frame-Options: SAMEORIGIN` 或 `DENY` 限制，OAuth 登录授权与第三方跳转畅通无阻；
  - **物理安全高度锁死**：由 Android 原生 `FrameLayout` 的内边距（Padding）在操作系统渲染层直接将网页物理限制在状态栏/刘海与底部手势条之内；
  - **独立原生悬浮控制器**：悬浮球与贴边小条完全由 Android 原生控件（`View` + `OnTouchListener` + 属性动画）构建，120Hz 极速跟手，完全独立于网页 JS 线程。

- **AssistiveTouch 质感触觉悬浮球**：
  - **贴边形态**：4dp x 34dp 极简磨砂半透明胶囊小条，严丝合缝贴在屏幕边缘；
  - **悬浮形态**：44dp 纯圆悬浮球，内置双层同心触觉光环，支持按压弹性微缩反馈；
  - **单次轻触即开**：轻触贴边小条平滑弹性展开为悬浮球并直接呼出设置面板；
  - **倒计时自动贴边**：支持 3s / 5s / 10s / 从不。

- **极简设置面板**：
  - 纯黑白灰高对比度设计，支持跟随系统/浅色/深色切换；
  - 目标网址修改与一键应用；
  - 自动贴边倒计时调节。

- **全静默手势返回拦截**：
  - 接入 `OnBackPressedDispatcher` 深度拦截 Android 边缘侧滑返回手势，网页内部后退完全静默，根路径二次滑动退出防误触。

## 快速上手与定制

### 1. 定制目标站点

修改 `app/src/main/res/values/strings.xml` 中的默认地址与名称：

```xml
<resources>
    <string name="app_name">Your App Name</string>
    <string name="default_url">https://your-website.com</string>
</resources>
```

### 2. 编译与安装

```bash
# 编译 Debug APK
./gradlew :app:assembleDebug

# 安装到连接的 Android 手机
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 开源协议

MIT License
