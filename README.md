# webview-bubble

轻量级 Android 网页套壳容器，深度对齐系统物理安全区与 AssistiveTouch 质感贴边悬浮控制器。

## 特性

- **纯顶层 WebView**：无 iframe 嵌套，彻底支持各类 OAuth 登录与第三方授权；
- **物理安全区锁死**：由原生 WindowInsets 物理限制边距，绝对避让状态栏与手势条；
- **AssistiveTouch 质感光环球**：
  - 贴边：4dp 极简磨砂胶囊条，严丝贴边；
  - 展开：44dp 纯圆触感光环球，轻触展开并唤起设置面板；
  - 闭环：面板关闭时悬浮球自动平滑滑回边缘贴边；
  - 倒计时：闲置自动吸附归位；
- **全静默手势返回**：深层后退完全静默，根路径二次滑动退出防误触；
- **纯净黑白灰**：跟随系统、浅色、深色模式无缝切换。

## 🤖 Agent 一键打包（推荐）

如果你在使用 AI 编程智能体（Claude Code、Cursor、OpenCode 等），无需手动克隆或配置本工程，直接复制下面这句话发给你的 Agent：

> **“请参考 https://github.com/Hxgh/webview-bubble ，帮我把 `[你要打包的网址]` 打包成一个叫 `[应用名称]` 的 Android APK。”**

Agent 会自动解析目标站点、探测抓取官方高清 Logo、按规范生成自适应图标切图并执行编译，最终在 `./dist/` 输出可直接安装的 `.apk` 文件（若检测到手机通过 USB 连接还会自动安装）。

## 命令行手动打包

也可以直接在工程根目录下执行脚本：

```bash
# 交互式引导 (回车后根据提示输入网址)
./pack.sh

# 或直接传参: ./pack.sh <网址> [应用名]
./pack.sh https://zcode.z.ai/cn/ "ZCode"
```

编译出的 APK 产物位于 `./dist/` 目录下。

## License

MIT
