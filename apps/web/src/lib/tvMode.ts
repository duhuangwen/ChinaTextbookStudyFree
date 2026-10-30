/**
 * TV mode detection.
 *
 * Native Android TV builds append "ChinaStudyFreeTV" to the WebView user
 * agent, so the web layer can use the same TV navigation without a bridge.
 * A wide, non-hover viewport is also treated as TV mode for vendor browsers
 * whose user agent does not advertise Android TV.
 */

export const TV_MODE_STORAGE_KEY = "csf-tv-mode";

const TV_USER_AGENT =
  /ChinaStudyFreeTV|Android\s*TV|Google\s*TV|GoogleTV|Smart[\s-]?TV|Tizen|Web0S|webOS|NetCast|HbbTV|BRAVIA|AFT[A-Z]|Fire\s*TV|Leanback|CRKey/i;

export interface TvModeEnvironment {
  userAgent: string;
  width: number;
  height: number;
  coarsePointer: boolean;
  noHover: boolean;
  override: string | null;
}

export function shouldEnableTvMode(env: TvModeEnvironment): boolean {
  if (env.override === "on") return true;
  if (env.override === "off") return false;
  if (TV_USER_AGENT.test(env.userAgent)) return true;

  const aspectRatio = env.width / Math.max(1, env.height);
  return (
    env.width >= 1280 &&
    env.height >= 600 &&
    aspectRatio >= 1.45 &&
    env.coarsePointer &&
    env.noHover
  );
}

/**
 * Leaves a full-screen flow (lesson, review, jump test) for its parent page.
 * On TV the remote's Back walks history, so pushing the parent again would
 * make the next Back land back inside the flow; step back instead.
 */
export function exitToParent(router: { back(): void; push(href: string): void }, href: string): void {
  if (document.documentElement.classList.contains("tv-mode") && window.history.length > 1) router.back();
  else router.push(href);
}

export function readTvModeOverride(): string | null {
  if (typeof window === "undefined") return null;
  try {
    const query = new URLSearchParams(window.location.search).get("tv");
    if (query === "1" || query === "on") {
      window.localStorage.setItem(TV_MODE_STORAGE_KEY, "on");
      return "on";
    }
    if (query === "0" || query === "off") {
      window.localStorage.setItem(TV_MODE_STORAGE_KEY, "off");
      return "off";
    }
    return window.localStorage.getItem(TV_MODE_STORAGE_KEY);
  } catch {
    return null;
  }
}

export function detectTvMode(): boolean {
  if (typeof window === "undefined") return false;
  return shouldEnableTvMode({
    userAgent: window.navigator.userAgent,
    width: window.innerWidth,
    height: window.innerHeight,
    coarsePointer: window.matchMedia?.("(pointer: coarse)").matches ?? false,
    noHover: window.matchMedia?.("(hover: none)").matches ?? false,
    override: readTvModeOverride(),
  });
}
