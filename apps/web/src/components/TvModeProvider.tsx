"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";
import { detectTvMode } from "@/lib/tvMode";
import { pickDirectionalCandidate, type TvDirection } from "@/lib/tvNavigation";

const FOCUSABLE =
  "a[href], button:not([disabled]), input:not([disabled]):not([type='hidden']), " +
  "select:not([disabled]), textarea:not([disabled]), [contenteditable='true'], " +
  "[role='button']:not([aria-disabled='true']), [role='link'], " +
  "[tabindex]:not([tabindex='-1'])";

const DIRECTIONS: Record<string, TvDirection> = {
  ArrowUp: "up",
  ArrowDown: "down",
  ArrowLeft: "left",
  ArrowRight: "right",
};

function isVisible(element: HTMLElement): boolean {
  if (!element.isConnected || element.getAttribute("aria-hidden") === "true") return false;
  const style = window.getComputedStyle(element);
  if (style.display === "none" || style.visibility === "hidden") return false;
  if (Number.parseFloat(style.opacity || "1") < 0.1) return false;
  const rect = element.getBoundingClientRect();
  return rect.width > 1 && rect.height > 1;
}

function activeScope(): Document | Element {
  return (
    document.querySelector<HTMLElement>('[role="dialog"][aria-modal="true"]') ??
    document
  );
}

function focusablesIn(scope: Document | Element): HTMLElement[] {
  return Array.from(scope.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
    element => !element.closest("[inert], [aria-hidden='true']") && isVisible(element),
  );
}

function focusables(): HTMLElement[] {
  return focusablesIn(activeScope());
}

function preferredFocus(): HTMLElement | null {
  const scope = activeScope();
  const marked = scope.querySelector<HTMLElement>("[data-tv-autofocus]");
  if (marked && isVisible(marked) && !marked.hasAttribute("disabled")) return marked;
  // A disabled primary button (narration still playing) must not hand default
  // focus to a destructive control such as "退出课程": one OK press would leave.
  const main = document.querySelector<HTMLElement>("main");
  if (main) {
    const contentFocus = focusablesIn(main).find(element => !element.closest("[data-tv-skip-default]"));
    if (contentFocus) return contentFocus;
  }
  return focusables().find(element => !element.closest("[data-tv-skip-default]")) ?? null;
}

function isFullyVisible(element: HTMLElement): boolean {
  const rect = element.getBoundingClientRect();
  return rect.top >= 0 && rect.left >= 0 && rect.bottom <= window.innerHeight && rect.right <= window.innerWidth;
}

function isPinned(element: HTMLElement): boolean {
  for (let node: HTMLElement | null = element; node; node = node.parentElement) {
    const position = window.getComputedStyle(node).position;
    if (position === "sticky" || position === "fixed") return true;
  }
  return false;
}

/**
 * Android's Back key never reaches the page as a keydown, so the native shell
 * asks here first: an open modal is dismissed like Escape, and a page that
 * marks its own exit control with `data-tv-back` (the lesson ✕, which asks
 * before discarding progress) handles Back instead of a bare history.back().
 */
function handleNativeBack(): boolean {
  if (document.querySelector('[role="dialog"][aria-modal="true"]')) {
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true, cancelable: true }));
    return true;
  }
  const pageBack = document.querySelector<HTMLElement>("[data-tv-back]");
  if (!pageBack) return false;
  pageBack.click();
  return true;
}

declare global {
  interface Window {
    __cstfHandleBack?: () => boolean;
  }
}

function isEditingElement(element: Element | null): boolean {
  return (
    element instanceof HTMLElement &&
    Boolean(element.closest("input, textarea, select, [contenteditable='true']"))
  );
}

export function TvModeProvider({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

  useEffect(() => {
    const root = document.documentElement;
    const enabled = detectTvMode();
    root.classList.toggle("tv-mode", enabled);
    root.dataset.tvMode = enabled ? "on" : "off";
    if (!enabled) return;

    let userMoved = false;
    const focusDefault = () => {
      const active = document.activeElement as HTMLElement | null;
      const marked = preferredFocus();
      if (!userMoved && marked && active !== marked) {
        marked.focus({ preventScroll: true });
        return;
      }
      if (active === document.body || !active || !isVisible(active)) {
        marked?.focus({ preventScroll: true });
      }
    };

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) return;
      const target = event.target as Element | null;
      if (
        (event.key === "Enter" || event.key === " ") &&
        !isEditingElement(target)
      ) {
        const action = (event.target as HTMLElement | null)?.closest<HTMLElement>(
          "a[href], button:not([disabled]), [role='button']:not([aria-disabled='true']), [role='link']",
        );
        if (action) {
          userMoved = true;
          event.preventDefault();
          action.click();
        }
        return;
      }

      const direction = DIRECTIONS[event.key];
      if (!direction || isEditingElement(target)) return;
      userMoved = true;

      const items = focusables();
      if (items.length === 0) return;

      const active = document.activeElement as HTMLElement | null;
      const current =
        active && items.includes(active) ? active : preferredFocus();
      if (!current) return;

      // Up/down never hops between the sidebar and the page; only left/right crosses over.
      const vertical = direction === "up" || direction === "down";
      const region = current.closest(".app-shell > aside");
      const candidate = pickDirectionalCandidate(
        current.getBoundingClientRect(),
        items
          .filter(item => item !== current && (!vertical || item.closest(".app-shell > aside") === region))
          .map(item => ({
            element: item,
            ...item.getBoundingClientRect().toJSON(),
            primary: item.hasAttribute("data-tv-primary"),
          })),
        direction,
      );

      event.preventDefault();
      // Long reading text has nothing focusable; page through it before focus
      // leaves for the edge or drops into the pinned footer.
      const canScroll = direction === "down"
        ? window.scrollY + window.innerHeight < document.documentElement.scrollHeight - 1
        : window.scrollY > 0;
      const entersPinned = candidate && isPinned(candidate.element) && !isPinned(current);
      if (vertical && canScroll && (!candidate || entersPinned)) {
        window.scrollBy({
          top: (direction === "down" ? 1 : -1) * window.innerHeight * 0.6,
          behavior: "smooth",
        });
        return;
      }
      if (!candidate) {
        current.scrollIntoView({ block: "nearest", inline: "nearest", behavior: "smooth" });
        return;
      }
      candidate.element.focus({ preventScroll: true });
      // Scrolling a sticky sidebar item would only shift the content beside it.
      if (isPinned(candidate.element) || isFullyVisible(candidate.element)) return;
      candidate.element.scrollIntoView({
        block: "center",
        inline: "center",
        behavior: "smooth",
      });
    };

    const onFocusIn = (event: FocusEvent) => {
      const element = event.target;
      if (!(element instanceof HTMLElement)) return;
      element.dataset.tvFocused = "true";
    };
    const onFocusOut = (event: FocusEvent) => {
      const element = event.target;
      if (element instanceof HTMLElement) delete element.dataset.tvFocused;
    };

    let firstFrame = 0;
    let secondFrame = 0;
    firstFrame = requestAnimationFrame(() => {
      secondFrame = requestAnimationFrame(focusDefault);
    });

    const observer = new MutationObserver(() => {
      const active = document.activeElement as HTMLElement | null;
      if (!userMoved || active === document.body || !active || !isVisible(active)) {
        requestAnimationFrame(focusDefault);
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });

    const onPointerDown = () => {
      userMoved = true;
    };

    window.__cstfHandleBack = handleNativeBack;
    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("pointerdown", onPointerDown, true);
    document.addEventListener("focusin", onFocusIn);
    document.addEventListener("focusout", onFocusOut);
    return () => {
      cancelAnimationFrame(firstFrame);
      cancelAnimationFrame(secondFrame);
      observer.disconnect();
      delete window.__cstfHandleBack;
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("pointerdown", onPointerDown, true);
      document.removeEventListener("focusin", onFocusIn);
      document.removeEventListener("focusout", onFocusOut);
      root.classList.remove("tv-mode");
      delete root.dataset.tvMode;
    };
  }, [pathname]);

  return children;
}
