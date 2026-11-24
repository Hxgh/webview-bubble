#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import readline from 'node:readline';
import { execSync } from 'node:child_process';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = path.resolve(__dirname, '..');
const RES_DIR = path.join(ROOT_DIR, 'app', 'src', 'main', 'res');
const STRINGS_XML = path.join(RES_DIR, 'values', 'strings.xml');

function prompt(query) {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });
  return new Promise((resolve) => rl.question(query, (ans) => {
    rl.close();
    resolve(ans.trim());
  }));
}

async function fetchHtml(url) {
  const res = await fetch(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
    }
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
  return await res.text();
}

function parseTitle(html) {
  const ogTitle = html.match(/<meta[^>]*property=["']og:(?:site_name|title)["'][^>]*content=["']([^"']+)["']/i);
  if (ogTitle && ogTitle[1]) return ogTitle[1].trim();

  const titleMatch = html.match(/<title[^>]*>([^<]+)<\/title>/i);
  if (titleMatch && titleMatch[1]) {
    // 过滤掉长后缀 (如 "ZCode - 全球领先的代码助手")
    return titleMatch[1].split(/[-_|–—]/)[0].trim();
  }
  return '';
}

function parseIcons(html, baseUrl) {
  const icons = [];
  const linkRegex = /<link\b[^>]*>/gi;
  let match;
  while ((match = linkRegex.exec(html)) !== null) {
    const tag = match[0];
    const relMatch = tag.match(/rel=["']([^"']+)["']/i);
    const hrefMatch = tag.match(/href=["']([^"']+)["']/i);
    if (!relMatch || !hrefMatch) continue;

    const rel = relMatch[1].toLowerCase();
    const href = hrefMatch[1];
    if (rel.includes('apple-touch-icon')) {
      icons.push({ url: new URL(href, baseUrl).href, priority: 100 });
    } else if (rel.includes('icon')) {
      icons.push({ url: new URL(href, baseUrl).href, priority: 50 });
    }
  }

  // 默认 fallback
  const origin = new URL(baseUrl).origin;
  icons.push({ url: `${origin}/apple-touch-icon.png`, priority: 30 });
  icons.push({ url: `${origin}/favicon.ico`, priority: 10 });
  return icons.sort((a, b) => b.priority - a.priority);
}

async function downloadBestIcon(icons) {
  for (const item of icons) {
    try {
      const res = await fetch(item.url, { headers: { 'User-Agent': 'Mozilla/5.0' } });
      if (res.ok) {
        const buf = Buffer.from(await res.arrayBuffer());
        if (buf.length > 500) {
          return { buffer: buf, url: item.url };
        }
      }
    } catch (_) {}
  }
  return null;
}

function generateAndroidIcons(sourceImgPath) {
  const pyScript = path.join(__dirname, 'generate_icons.py');
  execSync(`python3 "${pyScript}" "${sourceImgPath}" "${RES_DIR}"`, { stdio: 'inherit' });
}

function updateStringsXml(appName, defaultUrl) {
  let xml = fs.readFileSync(STRINGS_XML, 'utf-8');
  xml = xml.replace(/<string name="app_name">[^<]*<\/string>/, `<string name="app_name">${appName}</string>`);
  xml = xml.replace(/<string name="default_url">[^<]*<\/string>/, `<string name="default_url">${defaultUrl}</string>`);
  fs.writeFileSync(STRINGS_XML, xml, 'utf-8');
}

async function main() {
  console.log('\n🚀 Webview Bubble —— 一键泛化网页打包器\n');

  let targetUrl = process.argv[2];
  if (!targetUrl) {
    targetUrl = await prompt('请输入要包裹的目标网址 (例如 https://zcode.z.ai/cn/): ');
  }

  if (!targetUrl) {
    console.error('❌ 网址不能为空');
    process.exit(1);
  }

  if (!/^https?:\/\//i.test(targetUrl)) {
    targetUrl = 'https://' + targetUrl;
  }

  console.log(`\n🔍 正在探测目标网站信息: ${targetUrl}...`);
  let html = '';
  let autoTitle = '';
  try {
    html = await fetchHtml(targetUrl);
    autoTitle = parseTitle(html);
  } catch (err) {
    console.warn(`⚠️ 网页抓取失败 (${err.message})，使用默认配置继续`);
  }

  let defaultName = autoTitle || new URL(targetUrl).hostname.replace(/^www\./, '');
  let appName = process.argv[3];
  if (!appName) {
    const inputName = await prompt(`请输入应用名称 (默认: "${defaultName}"): `);
    appName = inputName || defaultName;
  }

  console.log(`\n📦 应用名称: ${appName}`);
  console.log(`🌐 目标网址: ${targetUrl}`);

  // 图标处理
  if (html) {
    console.log('🎨 正在提取网站图标...');
    const candidateIcons = parseIcons(html, targetUrl);
    const bestIcon = await downloadBestIcon(candidateIcons);
    if (bestIcon) {
      console.log(`✅ 成功获取图标: ${bestIcon.url}`);
      const tempPath = path.join(ROOT_DIR, '.temp_icon.png');
      fs.writeFileSync(tempPath, bestIcon.buffer);
      try {
        generateAndroidIcons(tempPath);
        console.log('✨ 已自动生成全套 Android Mipmap 及自适应图标');
      } finally {
        if (fs.existsSync(tempPath)) fs.unlinkSync(tempPath);
      }
    } else {
      console.log('ℹ️ 未检测到高分图标，保持默认应用图标');
    }
  }

  // 写入配置
  updateStringsXml(appName, targetUrl);

  // 执行 Gradle 编译
  console.log('\n⚙️ 正在编译 Android APK...');
  try {
    execSync('./gradlew :app:assembleDebug', { cwd: ROOT_DIR, stdio: 'inherit' });
  } catch (err) {
    console.error('❌ 编译失败，请检查 Gradle 环境');
    process.exit(1);
  }

  // 输出安装包
  const distDir = path.join(ROOT_DIR, 'dist');
  fs.mkdirSync(distDir, { recursive: true });
  const safeName = appName.replace(/[^\w一-龥_-]/g, '_');
  const targetApk = path.join(distDir, `${safeName}.apk`);
  const srcApk = path.join(ROOT_DIR, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');

  if (fs.existsSync(srcApk)) {
    fs.copyFileSync(srcApk, targetApk);
    console.log(`\n🎉 打包完成！APK 产物已生成至:\n👉 ${targetApk}`);
  }

  // 检测连接的手机并支持一键安装
  try {
    const devicesOutput = execSync('adb devices', { encoding: 'utf-8' });
    const hasDevice = devicesOutput.split('\n').slice(1).some(line => line.includes('\tdevice'));
    if (hasDevice) {
      const doInstall = await prompt('\n📱 检测到已连接的 Android 手机，是否立即安装到手机？(Y/n): ');
      if (!doInstall || doInstall.toLowerCase() === 'y') {
        console.log('📲 正在安装到手机...');
        execSync(`adb install -r "${targetApk}"`, { stdio: 'inherit' });
        console.log('✅ 安装成功！已可在手机上体验。');
      }
    }
  } catch (_) {}

  console.log('\n🌟 任务圆满完成！\n');
}

main().catch(console.error);
