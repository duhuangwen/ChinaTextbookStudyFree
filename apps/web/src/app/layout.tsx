import type { Metadata, Viewport } from "next";
import "./globals.css";
import { ThemeProvider } from "@/components/ThemeProvider";
import { BottomNav } from "@/components/BottomNav";
import { ToastProvider } from "@/components/Toast";
import { DailyRewardWatcher } from "@/components/DailyRewardWatcher";
import { AchievementWatcher } from "@/components/AchievementWatcher";
import { IntroAudioUnlock } from "@/components/IntroAudioUnlock";
import { LeagueWatcher } from "@/components/LeagueWatcher";
import { TvModeProvider } from "@/components/TvModeProvider";

export const metadata: Metadata = {
  title: "聪聪学堂",
  description: "全科免费，人人可学的小学AI学习平台",
  manifest: "/manifest.webmanifest",
  appleWebApp: {
    capable: true,
    title: "聪聪学堂",
    statusBarStyle: "default",
  },
  icons: {
    icon: [{ url: "/icons/icon.svg", type: "image/svg+xml" }],
    apple: [{ url: "/icons/apple-touch-icon.png", sizes: "180x180" }],
  },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#58CC02" },
    { media: "(prefers-color-scheme: dark)", color: "#131F24" },
  ],
};

/**
 * 首屏内联脚本：水合前读 localStorage，提前把 .theme-dark 挂到 <html>，
 * 防止深色用户看到一帧白底闪屏。逻辑与 ThemeProvider 保持一致：
 *   装备暗色美妆主题 强制暗；否则看三态偏好（dark / system+系统深色）。
 * 顺带注册 Service Worker（壳层预缓存 + 课程 JSON/音频 SWR）。
 */
const bootScript = `
(function () {
  try {
    var tvOverride = localStorage.getItem("csf-tv-mode");
    var tvQuery = new URLSearchParams(location.search).get("tv");
    if (tvQuery === "1" || tvQuery === "on") {
      localStorage.setItem("csf-tv-mode", "on");
      tvOverride = "on";
    } else if (tvQuery === "0" || tvQuery === "off") {
      localStorage.setItem("csf-tv-mode", "off");
      tvOverride = "off";
    }
    var tvUa = /ChinaStudyFreeTV|Android\\s*TV|Google\\s*TV|GoogleTV|Smart[\\s-]?TV|Tizen|Web0S|webOS|NetCast|HbbTV|BRAVIA|AFT[A-Z]|Fire\\s*TV|Leanback|CRKey/i.test(navigator.userAgent);
    var wideTouch = innerWidth >= 1280 && innerHeight >= 600 && innerWidth / Math.max(1, innerHeight) >= 1.45 &&
      matchMedia("(pointer: coarse)").matches && matchMedia("(hover: none)").matches;
    if (tvOverride === "on" || (tvOverride !== "off" && (tvUa || wideTouch))) {
      document.documentElement.classList.add("tv-mode");
      document.documentElement.dataset.tvMode = "on";
    }
    // The Android TV shell enables wide viewports; a 1280px layout (720px tall)
    // gets the lg desktop layouts instead of the 960x540 phone column. Hydration
    // inserts its own device-width meta later, so keep rewriting every copy.
    if (/ChinaStudyFreeTV/.test(navigator.userAgent)) {
      var tvViewport = "width=1280";
      var pinViewport = function () {
        var metas = document.querySelectorAll('meta[name="viewport"]');
        for (var i = 0; i < metas.length; i++) {
          if (metas[i].getAttribute("content") !== tvViewport) metas[i].setAttribute("content", tvViewport);
        }
      };
      pinViewport();
      new MutationObserver(pinViewport).observe(document.head, { childList: true, subtree: true, attributes: true, attributeFilter: ["content"] });
    }
  } catch (e) {}
  try {
    var DARK_THEMES = { theme_midnight: "#0F1419", theme_obsidian: "#08090C" };
    var equipped = "";
    try {
      var raw = localStorage.getItem("csf-progress-v1");
      if (raw) equipped = (JSON.parse(raw).state || {}).equippedTheme || "";
    } catch (e) {}
    var mode = localStorage.getItem("csf-theme-mode");
    var sysDark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
    var cosmeticDark = Object.prototype.hasOwnProperty.call(DARK_THEMES, equipped);
    var dark = cosmeticDark || mode === "dark" || (mode !== "light" && sysDark);
    if (dark) {
      var root = document.documentElement;
      root.classList.add("theme-dark");
      root.style.colorScheme = "dark";
      root.style.backgroundColor = cosmeticDark ? DARK_THEMES[equipped] : "#131F24";
    }
  } catch (e) {}
  if ("serviceWorker" in navigator) {
    var localHost = location.hostname === "localhost" || location.hostname === "127.0.0.1" || location.hostname === "::1";
    var embeddedApp = localHost || location.hostname === "appassets.androidplatform.net";
    if (!embeddedApp) {
      window.addEventListener("load", function () {
        navigator.serviceWorker.register("/sw.js").catch(function () {});
      });
    }
  }
})();
`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: bootScript }} />
      </head>
      {/* 底部导航的高度补偿由 BottomNav 自带的占位条负责（web-lesson-15）：
          课程 / 阅读器等沉浸页导航隐藏时不再留 64px 死空隙 */}
      <body className="min-h-screen bg-bg-soft">
        <ThemeProvider>
          <TvModeProvider>
            <ToastProvider>
              <DailyRewardWatcher />
              <AchievementWatcher />
              <LeagueWatcher />
              <IntroAudioUnlock />
              {children}
              <BottomNav />
              <div className="tv-control-hint" aria-hidden="true">
                <span>遥控器</span>
                方向键移动 · OK 确认 · 返回键退出
              </div>
            </ToastProvider>
          </TvModeProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
