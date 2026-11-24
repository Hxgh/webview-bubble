---
name: pack-web
description: 一键将任意 Web 网站打包为专属 Android 原生套壳 APK
---

# Pack Web to Android APK

用于将指定的 Web 网址打包为独立的 Android APK。

## 规范与使用方式

1. 询问用户需要包裹的网站网址（若用户未提供）及应用名称。
2. 运行工程根目录的打包脚本：
   ```bash
   ./pack.sh <URL> [AppName]
   ```
3. 脚本会自动：
   - 请求目标网站探测标题并提取高清图标（Favicon / Apple-touch-icon / Web Manifest）；
   - 自动按 Android 规范生成带呼吸安全边距的自适应前景色与多分辨率 Mipmap 图标；
   - 写入 `strings.xml` 默认网址与名称；
   - 编译输出最终 APK 至 `./dist/` 目录；
   - 如检测到 adb 连接的手机，支持直接自动安装。
