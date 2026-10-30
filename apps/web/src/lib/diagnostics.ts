declare global {
  interface Window {
    /** Android shell bridge; it keeps the last steps so a hung page can still be diagnosed natively. */
    CstfDiag?: { mark(step: string): void; tick(): void };
  }
}

export function diagMark(step: string): void {
  try {
    window.CstfDiag?.mark(step.slice(0, 120));
  } catch {
    // Diagnostics must never break the page.
  }
}

export function describeElement(element: Element | null): string {
  if (!element) return "none";
  const label = element.getAttribute("aria-label") ?? element.textContent ?? "";
  return `${element.tagName.toLowerCase()}:${label.replace(/\s+/g, "").slice(0, 16)}`;
}

let installed = false;

/**
 * Error, lifecycle and liveness marks. The 1 s tick lets the shell tell a busy JS thread
 * (ticks stop) from a page that is alive but frozen or cut off from input (ticks continue).
 */
export function installErrorMarks(): void {
  if (installed || !window.CstfDiag) return;
  installed = true;
  window.addEventListener("error", event => diagMark(`error:${event.message}`));
  window.addEventListener("unhandledrejection", event => diagMark(`rejection:${String(event.reason)}`));
  document.addEventListener("visibilitychange", () => diagMark(`vis:${document.visibilityState}`));
  document.addEventListener("freeze", () => diagMark("page:freeze"));
  document.addEventListener("resume", () => diagMark("page:resume"));
  window.addEventListener("focus", () => diagMark("win:focus"));
  window.addEventListener("blur", () => diagMark("win:blur"));
  window.setInterval(() => {
    try {
      window.CstfDiag?.tick();
    } catch {
      // Diagnostics must never break the page.
    }
  }, 1000);
}
